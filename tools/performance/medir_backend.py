#!/usr/bin/env python3
"""Medición local: inicia solo recursos desechables propios y nunca acepta DB_URL externa."""
import argparse
import concurrent.futures
import datetime as dt
import hashlib
import http.client
import json
import math
import os
import platform
import secrets
import socket
import subprocess
import tempfile
import threading
import time
import uuid
from zoneinfo import ZoneInfo
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
JAR = ROOT / 'erp-backend/target/erp-backend-0.0.1-SNAPSHOT.jar'
IMAGE = 'postgres:17-alpine'
USERS = 200


def command(args, *, env=None, data=None, timeout=120):
    return subprocess.run(args, input=data, text=True, capture_output=True, check=True, env=env, timeout=timeout, cwd=ROOT).stdout.strip()


def available_port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def call(port, path, token=None):
    conn = http.client.HTTPConnection('127.0.0.1', port, timeout=5)
    headers = {'Authorization': 'Bearer ' + token} if token else {}
    try:
        conn.request('GET', path, headers=headers)
        res = conn.getresponse()
        return res.status, res.read()
    finally:
        conn.close()


def seed_sql(tokens):
    token_rows = ',\n'.join("(%d,'%s')" % (i, hashlib.sha256(t.encode()).hexdigest()) for i, t in enumerate(tokens, 1))
    return """
BEGIN;
INSERT INTO roles(codigo,nombre) VALUES ('LOAD_READER','Lector de carga');
INSERT INTO roles_permisos(rol_id,permiso_id)
 SELECT r.id,p.id FROM roles r JOIN permisos p ON p.codigo IN
 ('MAESTROS_CATEGORIAS_LEER','MAESTROS_PRODUCTOS_LEER','REPORTERIA_INVENTARIO_LEER','INVENTARIO_ALERTAS_LEER','VENTAS_FACTURAS_LEER')
 WHERE r.codigo='LOAD_READER';
INSERT INTO usuarios(username,correo,password_hash)
 SELECT 'carga'||g,'carga'||g||'@example.test','cuenta-sin-login' FROM generate_series(1,200) g;
INSERT INTO usuarios_roles(usuario_id,rol_id)
 SELECT u.id,r.id FROM usuarios u CROSS JOIN roles r WHERE u.username LIKE 'carga%%' AND r.codigo='LOAD_READER';
CREATE TEMP TABLE load_tokens (n integer, digest text);
INSERT INTO load_tokens VALUES %s;
INSERT INTO tokens_sesion(usuario_id,token_hash,emitido_en,vence_en)
 SELECT u.id,t.digest,now(),now()+interval '4 hours' FROM load_tokens t JOIN usuarios u ON u.username='carga'||t.n;
INSERT INTO categorias_producto(codigo,nombre)
 SELECT 'CAT'||lpad(g::text,5,'0'),'Categoria '||g FROM generate_series(1,1000) g;
INSERT INTO almacenes(codigo,nombre)
 SELECT 'ALM'||g,'Almacen '||g FROM generate_series(1,3) g;
INSERT INTO productos(sku,nombre,categoria_id,punto_reorden)
 SELECT 'SKU'||lpad(g::text,6,'0'),'Producto '||g,c.id,15 FROM generate_series(1,10000) g
 JOIN categorias_producto c ON c.codigo='CAT'||lpad(((g-1)%%1000+1)::text,5,'0');
INSERT INTO existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada,costo_promedio)
 SELECT p.id,a.id,(p.id%%31),least(p.id%%31,p.id%%4),10 FROM productos p CROSS JOIN almacenes a;
INSERT INTO minimos_producto_almacen(producto_id,almacen_id,minimo)
 SELECT p.id,a.id,10 FROM productos p CROSS JOIN almacenes a;
COMMIT;
ANALYZE;
""" % token_rows


def psql(name, sql):
    return command(['docker','exec','-i',name,'psql','-X','-q','-t','-A','-v','ON_ERROR_STOP=1','-U','loadtest','-d','loadtest'],data=sql)


def percentile(values, percent):
    if not values:
        return None
    values = sorted(values)
    return round(values[math.ceil(percent * len(values)) - 1], 3)


def valid_response(name, obj, dataset):
    try:
        if name == 'dashboard':
            indicators = {item['code']: item for item in obj.get('indicators', [])}
            return (set(indicators) == {'VENTAS_FACTURADO_SIN_IMPUESTOS','INVENTARIO_PRODUCTOS_ALERTA'}
                    and indicators['VENTAS_FACTURADO_SIN_IMPUESTOS']['value'] == 0
                    and indicators['INVENTARIO_PRODUCTOS_ALERTA']['value'] == dataset['alertas_globales'])
        expected={'categorias':dataset['categorias'],'productos':dataset['productos'],'stock_bajo':dataset['pares_bajo_minimo']}[name]
        return isinstance(obj.get('content'), list) and len(obj['content']) == 20 and obj.get('totalElements') == expected
    except (AttributeError, KeyError, TypeError):
        return False


def measure(port, tokens, users, routes, dataset, rounds):
    lock = threading.Lock()
    barrier = threading.Barrier(users + 1, timeout=60)
    in_flight = 0
    peak = 0
    def worker(index):
        nonlocal in_flight, peak
        connection = http.client.HTTPConnection('127.0.0.1', port, timeout=60)
        local = []
        barrier.wait()
        for turn in range(len(routes) * rounds):
            name, kind, path = routes[(index + turn) % len(routes)]
            with lock:
                in_flight += 1
                peak = max(peak, in_flight)
            started = time.perf_counter()
            status = 'exception'
            valid = False
            try:
                connection.request('GET', path, headers={'Authorization':'Bearer '+tokens[index]})
                response = connection.getresponse()
                payload = response.read()
                status = str(response.status)
                if response.status == 200:
                    obj = json.loads(payload)
                    valid = valid_response(name, obj, dataset)
            except (OSError, ValueError, http.client.HTTPException):
                connection.close()
                connection = http.client.HTTPConnection('127.0.0.1', port, timeout=60)
            elapsed_ms = (time.perf_counter()-started)*1000
            with lock:
                in_flight -= 1
            local.append((name,kind,round(elapsed_ms,3),status,valid))
        connection.close()
        return local
    with concurrent.futures.ThreadPoolExecutor(max_workers=users) as pool:
        futures=[pool.submit(worker,i) for i in range(users)]
        started=time.perf_counter()
        barrier.wait()
        rows=[item for future in futures for item in future.result()]
        seconds=time.perf_counter()-started
    results={}
    for name,kind,_ in routes:
        subset=[r for r in rows if r[0]==name]
        success=[r[2] for r in subset if r[3]=='200' and r[4]]
        all_elapsed=[r[2] for r in subset]
        results[name]={'tipo':kind,'solicitudes':len(subset),'correctas':len(success),
            'estados':dict(Counter(r[3] for r in subset)), 'respuestas_200_invalidas':sum(r[3]=='200' and not r[4] for r in subset),
            'p50_ms_correctas':percentile(success,.50),'p95_ms_correctas':percentile(success,.95),'p99_ms_correctas':percentile(success,.99),
            'max_ms_todas':round(max(all_elapsed),3), 'umbral_ms':2000 if kind=='consulta' else 5000,
            'correctas_bajo_umbral':sum(r[2] < (2000 if kind=='consulta' else 5000) for r in subset if r[3]=='200' and r[4]),
            'muestras_ms_estado_valida':[[r[2],r[3],r[4]] for r in subset]}
    return {'usuarios':users,'solicitudes_por_usuario':len(routes)*rounds,'duracion_s':round(seconds,3),
            'solicitudes_por_s':round(len(rows)/seconds,2),'max_solicitudes_en_vuelo_cliente':peak,'rutas':results}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output',type=Path,required=True,help='archivo JSON nuevo para resultados sin credenciales')
    parser.add_argument('--rounds',type=int,default=2,choices=range(1,21),metavar='1..20',help='vueltas por usuario a las cuatro rutas (predeterminado: 2)')
    args=parser.parse_args()
    if args.output.exists(): raise SystemExit('El archivo de salida ya existe; use otro nombre.')
    if not JAR.is_file(): raise SystemExit('Falta el JAR; ejecute el empaquetado indicado en README.md.')
    container='techsupply-perf-'+uuid.uuid4().hex[:12]
    env=os.environ.copy(); env['POSTGRES_PASSWORD']=secrets.token_urlsafe(24)
    tokens=[secrets.token_urlsafe(32) for _ in range(USERS)]
    backend=None; created=False
    start=dt.datetime.now(dt.timezone.utc).isoformat()
    with tempfile.TemporaryDirectory(prefix='techsupply-perf-') as tmp:
        log=Path(tmp)/'backend.log'
        try:
            command(['docker','run','-d','--rm','--name',container,'-e','POSTGRES_USER=loadtest','-e','POSTGRES_DB=loadtest','-e','POSTGRES_PASSWORD','-p','127.0.0.1::5432',IMAGE],env=env)
            created=True
            for _ in range(60):
                if subprocess.run(['docker','exec',container,'pg_isready','-U','loadtest','-d','loadtest'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL).returncode==0: break
                time.sleep(1)
            else: raise RuntimeError('PostgreSQL no alcanzó estado listo')
            postgres_port=int(command(['docker','port',container,'5432/tcp']).rsplit(':',1)[1])
            app_port=available_port()
            # Spring and JVM overrides from the developer shell must never redirect the disposable run.
            blocked_prefixes=('SPRING_','DB_','SERVER_','APP_','MANAGEMENT_','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS','_JAVA_OPTIONS','CLASSPATH')
            app_env={key:value for key,value in os.environ.items() if not key.startswith(blocked_prefixes)}
            app_env.update({'DB_URL':f'jdbc:postgresql://127.0.0.1:{postgres_port}/loadtest','DB_USERNAME':'loadtest','DB_PASSWORD':env['POSTGRES_PASSWORD'],
                'SERVER_PORT':str(app_port),'SERVER_ADDRESS':'127.0.0.1','APP_SECURITY_BOOTSTRAP_ADMIN_ENABLED':'false'})
            with log.open('w') as stream:
                backend=subprocess.Popen(['java','-XX:+UseSerialGC','-Xms256m','-Xmx768m','-jar',str(JAR)],env=app_env,stdout=stream,stderr=subprocess.STDOUT)
            for _ in range(150):
                if backend.poll() is not None: raise RuntimeError('El backend terminó antes de estar listo; verifique su configuración local.')
                try:
                    if call(app_port,'/actuator/health')[0]==200: break
                except (OSError,http.client.HTTPException): pass
                time.sleep(1)
            else: raise RuntimeError('El backend no alcanzó estado listo')
            psql(container,seed_sql(tokens))
            raw=psql(container,"""SELECT json_build_object('categorias',(SELECT count(*) FROM categorias_producto),'productos',(SELECT count(*) FROM productos),'almacenes',(SELECT count(*) FROM almacenes),'existencias',(SELECT count(*) FROM existencias),'minimos',(SELECT count(*) FROM minimos_producto_almacen),'usuarios_carga',(SELECT count(*) FROM usuarios WHERE username LIKE 'carga%'),'movimientos',(SELECT count(*) FROM movimientos_inventario),'facturas_venta',(SELECT count(*) FROM facturas_venta),'facturas_proveedor',(SELECT count(*) FROM facturas_proveedor),'pares_bajo_minimo',(SELECT count(*) FROM existencias e JOIN minimos_producto_almacen m ON m.producto_id=e.producto_id AND m.almacen_id=e.almacen_id WHERE e.cantidad_fisica-e.cantidad_reservada<=m.minimo),'alertas_globales',(SELECT count(*) FROM (SELECT p.id FROM productos p JOIN existencias e ON e.producto_id=p.id GROUP BY p.id HAVING sum(e.cantidad_fisica-e.cantidad_reservada)<=p.punto_reorden) q),'max_connections',current_setting('max_connections'),'postgres_version',current_setting('server_version'))::text;""")
            dataset=json.loads(raw.splitlines()[-1])
            today=dt.datetime.now(ZoneInfo('America/Mexico_City')).date()
            start_date=today.replace(day=1).isoformat(); end_date=today.isoformat()
            routes=[('categorias','consulta','/api/v1/categories?page=0&size=20'),
                ('productos','consulta','/api/v1/products?page=0&size=20'),
                ('stock_bajo','reporte','/api/v1/reports/inventory/low-stock?page=0&size=20'),
                ('dashboard','reporte',f'/api/v1/reports/dashboard?from={start_date}&to={end_date}')]
            unauth=call(app_port,routes[0][2])[0]
            if unauth!=401: raise RuntimeError('La comprobación sin token no devolvió 401')
            for name,kind,path in routes:
                status,body=call(app_port,path,tokens[0])
                if status!=200: raise RuntimeError(f'El precalentamiento de {name} devolvió {status}')
                parsed=json.loads(body)
                if not valid_response(name,parsed,dataset): raise RuntimeError(f'La respuesta del precalentamiento de {name} no coincide con el dataset')
            stages=[]
            for users in (1,50,200):
                print(f'Midiendo {users} usuarios...',flush=True)
                stages.append(measure(app_port,tokens,users,routes,dataset,args.rounds))
            cpu_info=Path('/proc/cpuinfo').read_text().splitlines()
            cpu_model=next((line.split(':',1)[1].strip() for line in cpu_info if line.startswith('model name')),None)
            memory_info=Path('/proc/meminfo').read_text().splitlines()
            memory_total=next((line.split(':',1)[1].strip() for line in memory_info if line.startswith('MemTotal:')),None)
            details={'inicio_utc':start,'fin_utc':dt.datetime.now(dt.timezone.utc).isoformat(),'git_head':command(['git','rev-parse','HEAD'],env=os.environ),
                'jar_sha256':hashlib.sha256(JAR.read_bytes()).hexdigest(),'python':platform.python_version(),
                'script_sha256':hashlib.sha256(Path(__file__).read_bytes()).hexdigest(),
                'sistema':platform.platform(),'cpu_logicas_vistas':os.cpu_count(),'afinidad_cpu':len(os.sched_getaffinity(0)) if hasattr(os,'sched_getaffinity') else None,
                'cpu_modelo':cpu_model,'memoria_host':memory_total,'java':subprocess.run(['java','-version'],capture_output=True,text=True).stderr.splitlines()[0],
                'postgres_imagen':IMAGE,'postgres_imagen_id':command(['docker','image','inspect',IMAGE,'--format','{{.Id}}']),
                'jvm_args':['-XX:+UseSerialGC','-Xms256m','-Xmx768m'],'pool_bd':'Spring Boot/Hikari por defecto',
                'dataset':dataset,'rutas':[{'nombre':n,'tipo':k,'ruta':p} for n,k,p in routes],
                'metodologia':f'usuarios distintos con sesiones precargadas, {len(routes)*args.rounds} GET por usuario, conexión persistente, arranque simultáneo por etapa, sin espera; p95 nearest-rank de respuestas 200 válidas',
                'sin_token_status':unauth,'etapas':stages}
            args.output.parent.mkdir(parents=True,exist_ok=True)
            with args.output.open('x',encoding='utf-8') as out: json.dump(details,out,ensure_ascii=False,indent=2); out.write('\n')
            print('Resultado guardado:',args.output,flush=True)
        finally:
            if backend is not None:
                backend.terminate()
                try: backend.wait(timeout=20)
                except subprocess.TimeoutExpired: backend.kill(); backend.wait()
            if created:
                subprocess.run(['docker','rm','-f',container],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,check=False)


if __name__=='__main__': main()
