#!/usr/bin/env python3
"""Prueba de navegador con PostgreSQL, backend y proxy temporales propios."""
import os
from pathlib import Path
import secrets
import signal
import socket
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
JAR = ROOT / 'erp-backend/target/erp-backend-0.0.1-SNAPSHOT.jar'


def run(args, *, env=None, data=None, cwd=ROOT):
    return subprocess.run(args, input=data, text=True, capture_output=True, check=True, env=env, cwd=cwd, timeout=120).stdout.strip()


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def wait_http(url, process):
    for _ in range(150):
        if process.poll() is not None:
            raise RuntimeError('El proceso terminó antes de estar listo')
        try:
            with urllib.request.urlopen(url, timeout=2) as response:
                if response.status == 200:
                    return
        except (OSError, urllib.error.URLError):
            time.sleep(1)
    raise RuntimeError('El servicio no alcanzó estado listo')


def stop(process):
    if process is None or process.poll() is not None:
        return
    os.killpg(process.pid, signal.SIGTERM)
    try:
        process.wait(timeout=15)
    except subprocess.TimeoutExpired:
        os.killpg(process.pid, signal.SIGKILL)
        process.wait()


def main():
    if not JAR.is_file():
        raise RuntimeError('Falta el JAR; empaquete el backend antes de ejecutar')
    name = 'techsupply-auth-test-' + uuid.uuid4().hex[:12]
    password = secrets.token_urlsafe(24)
    db_password = secrets.token_urlsafe(24)
    username = 'auth_prueba'
    docker_env = os.environ.copy()
    docker_env['POSTGRES_PASSWORD'] = db_password
    blocked = ('SPRING_', 'DB_', 'SERVER_', 'APP_', 'MANAGEMENT_', 'JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH', 'VITE_', 'BACKEND_PROXY_TARGET')
    clean_env = {k: v for k, v in os.environ.items() if not k.startswith(blocked)}
    backend = vite = None
    created = False
    with tempfile.TemporaryDirectory(prefix='techsupply-auth-test-') as directory:
        try:
            run(['docker', 'run', '-d', '--rm', '--name', name, '-e', 'POSTGRES_USER=auth_test', '-e', 'POSTGRES_DB=auth_test', '-e', 'POSTGRES_PASSWORD', '-p', '127.0.0.1::5432', 'postgres:17-alpine'], env=docker_env)
            created = True
            for _ in range(60):
                if subprocess.run(['docker', 'exec', name, 'pg_isready', '-U', 'auth_test', '-d', 'auth_test'], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0:
                    break
                time.sleep(1)
            else:
                raise RuntimeError('PostgreSQL no estuvo listo')
            pg_port = int(run(['docker', 'port', name, '5432/tcp']).rsplit(':', 1)[1])
            api_port, web_port = port(), port()
            while web_port == api_port:
                web_port = port()
            app_env = clean_env | {
                'DB_URL': f'jdbc:postgresql://127.0.0.1:{pg_port}/auth_test', 'DB_USERNAME': 'auth_test', 'DB_PASSWORD': db_password,
                'SERVER_PORT': str(api_port), 'SERVER_ADDRESS': '127.0.0.1',
                'APP_SECURITY_BOOTSTRAP_ADMIN_ENABLED': 'true', 'APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME': username,
                'APP_SECURITY_BOOTSTRAP_ADMIN_EMAIL': 'auth_prueba@example.test', 'APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD': password,
            }
            with open(Path(directory) / 'backend.log', 'w') as log:
                backend = subprocess.Popen(['java', '-XX:+UseSerialGC', '-Xms128m', '-Xmx768m', '-jar', str(JAR)], cwd=ROOT, env=app_env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            wait_http(f'http://127.0.0.1:{api_port}/actuator/health', backend)
            sql = "INSERT INTO usuarios(username,correo,password_hash) SELECT 'lector_prueba','lector_prueba@example.test',password_hash FROM usuarios WHERE username='auth_prueba' RETURNING id;"
            for _ in range(30):
                result = run(['docker', 'exec', '-i', name, 'psql', '-X', '-q', '-t', '-A', '-v', 'ON_ERROR_STOP=1', '-U', 'auth_test', '-d', 'auth_test'], data=sql)
                if result:
                    break
                time.sleep(1)
            else:
                raise RuntimeError('El usuario temporal no se creó')
            vite_env = clean_env | {'BACKEND_PROXY_TARGET': f'http://127.0.0.1:{api_port}', 'VITE_API_URL': ''}
            with open(Path(directory) / 'vite.log', 'w') as log:
                vite = subprocess.Popen(['npm', 'run', 'dev', '--', '--host', '127.0.0.1', '--port', str(web_port), '--strictPort'], cwd=ROOT / 'frontend', env=vite_env, stdout=log, stderr=subprocess.STDOUT, start_new_session=True)
            wait_http(f'http://127.0.0.1:{web_port}/login', vite)
            browser_env = clean_env | {'AUTH_TEST_BASE_URL': f'http://127.0.0.1:{web_port}', 'AUTH_TEST_USERNAME': username, 'AUTH_TEST_PASSWORD': password}
            subprocess.run(['node', 'scripts/auth-browser.cjs'], cwd=ROOT / 'frontend', env=browser_env, check=True, timeout=180)
        finally:
            stop(vite)
            stop(backend)
            if created:
                subprocess.run(['docker', 'rm', '-f', name], stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL, check=False)


if __name__ == '__main__':
    main()
