#!/usr/bin/env python3
"""Real HTTP auth verification. Requires Java 21, Docker, Python 3 + PyYAML and a built backend JAR.
Creates only its own ephemeral PostgreSQL container; does not use any ambient DB_URL or Compose volume.
Passwords/tokens are generated in memory and redacted from the report. Exit 1 = observed/contract mismatch.
"""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import shutil
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
import yaml


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--repo', type=Path, default=Path(__file__).resolve().parents[2])
    parser.add_argument('--report', type=Path, default=Path('/tmp/techsupply-auth-http-results.json'))
    args = parser.parse_args()
    repo = args.repo.resolve()
    contract = yaml.safe_load((repo/'docs/api/openapi-auth.yaml').read_text())
    jar = repo/'erp-backend/target/erp-backend-0.0.1-SNAPSHOT.jar'
    if not jar.is_file():
        raise SystemExit('Build first: cd erp-backend && ./mvnw -DskipTests package')
    report = {'transport': 'real HTTP over loopback; embedded Tomcat; no mocks',
              'openapi_version': contract['info']['version'],
              'jar_sha256': hashlib.sha256(jar.read_bytes()).hexdigest(),
              'cases': [], 'contract_gaps': [], 'cleanup': {}}
    runtime = Path(tempfile.mkdtemp(prefix='techsupply-auth-http-'))
    runtime.chmod(0o700)
    cid = None
    app = None
    log = None
    private = []
    base = None
    opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def docker(*parts, stdin=None):
        return subprocess.run(['docker', *parts], input=stdin, text=True, capture_output=True, check=True, timeout=90).stdout.strip()

    def clean(value):
        if isinstance(value, dict):
            return {k: '[REDACTED]' if k.lower() in {'password', 'accesstoken', 'token', 'passwordhash'} else clean(v)
                    for k, v in value.items()}
        if isinstance(value, list): return [clean(v) for v in value]
        if isinstance(value, str):
            for secret in private:
                if secret: value = value.replace(secret, '[REDACTED]')
        return value

    def request(method, path, payload=None, token=None, raw=None):
        headers = {'Accept': 'application/json'}
        data = raw.encode() if raw is not None else (json.dumps(payload).encode() if payload is not None else None)
        if data is not None: headers['Content-Type'] = 'application/json'
        if token: headers['Authorization'] = 'Bearer '+token
        req = urllib.request.Request(base+'/api/v1'+path, data=data, headers=headers, method=method)
        try: response = opener.open(req, timeout=20)
        except urllib.error.HTTPError as ex: response = ex
        with response:
            content = response.read()
            body = json.loads(content) if content else None
            return response.status, dict(response.headers), body, len(content)

    # Checks only keywords used by response schemas in this contract; not a general OpenAPI validator.
    def validate(schema, value, path='$'):
        if '$ref' in schema:
            target = contract
            for piece in schema['$ref'].removeprefix('#/').split('/'): target = target[piece]
            return validate(target, value, path)
        typ = schema.get('type')
        if value is None and schema.get('nullable'): return
        if 'enum' in schema and value not in schema['enum']: raise AssertionError(path+': invalid enum')
        types = {'object': dict, 'array': list, 'string': str, 'integer': int, 'boolean': bool, 'number': (int,float)}
        if typ and (not isinstance(value, types[typ]) or typ in {'integer','number'} and isinstance(value, bool)):
            raise AssertionError(path+': wrong JSON type')
        if typ == 'object':
            if not set(schema.get('required',[])) <= value.keys(): raise AssertionError(path+': missing required fields')
            if schema.get('additionalProperties') is False and not value.keys() <= schema.get('properties',{}).keys():
                raise AssertionError(path+': undeclared fields')
            for key, rule in schema.get('properties',{}).items():
                if key in value: validate(rule, value[key], path+'.'+key)
        if typ == 'array':
            if schema.get('uniqueItems') and len({json.dumps(x,sort_keys=True) for x in value}) != len(value):
                raise AssertionError(path+': duplicate items')
            for item in value: validate(schema.get('items',{}), item, path+'[]')
        if typ == 'string':
            if 'pattern' in schema and re.search(schema['pattern'],value) is None: raise AssertionError(path+': pattern mismatch')
            if 'minLength' in schema and len(value)<schema['minLength']: raise AssertionError(path+': too short')
            if 'maxLength' in schema and len(value)>schema['maxLength']: raise AssertionError(path+': too long')
            if schema.get('format') == 'date-time': dt.datetime.fromisoformat(value.replace('Z','+00:00'))
            if schema.get('format') == 'email' and '@' not in value: raise AssertionError(path+': invalid email')

    def dto_user(value, username=None):
        expected = {'id','username','correo','activo','empleadoId','clienteId','ultimoAcceso','roles','permisos'}
        if set(value) != expected: raise AssertionError('User DTO fields differ from source')
        if not isinstance(value['id'],int) or not isinstance(value['activo'],bool): raise AssertionError('User DTO field types')
        for key in ['roles','permisos']:
            if not isinstance(value[key],list) or not all(isinstance(v,str) for v in value[key]): raise AssertionError('Authority type')
        if username is not None and value['username'] != username: raise AssertionError('Unexpected identity')
        for key in ['empleadoId','clienteId']:
            if value[key] is not None and not isinstance(value[key],int): raise AssertionError('Reference type')
        dt.datetime.fromisoformat(value['ultimoAcceso'].replace('Z','+00:00')) if value['ultimoAcceso'] else None

    def admin_fields(path, body):
        if not isinstance(body,list): raise AssertionError('Expected administrative list')
        for value in body:
            if path == '/admin/users':
                dto_user(value)
                continue
            fields = {'id','codigo','nombre','descripcion','permisos'} if path == '/admin/roles' else {'id','codigo','modulo','accion','descripcion'}
            if set(value) != fields: raise AssertionError('Administrative DTO fields differ from source')
            if not isinstance(value['id'],int) or isinstance(value['id'],bool): raise AssertionError('Administrative id type')
            for key in fields-{'id','descripcion','permisos'}:
                if not isinstance(value[key],str): raise AssertionError('Administrative field type: '+key)
            if value['descripcion'] is not None and not isinstance(value['descripcion'],str): raise AssertionError('Description type')
            if 'permisos' in value and (not isinstance(value['permisos'],list) or not all(isinstance(x,str) for x in value['permisos'])):
                raise AssertionError('Administrative permission list type')

    def case(name, method, path, expected, payload=None, token=None, raw=None, check=None, template=None, setup=False):
        status, headers, body, size = request(method, path, payload, token, raw)
        entry = {'name': name, 'method': method, 'path': path, 'expected_status': expected,
                 'observed_status': status, 'setup': setup, 'response': clean(body), 'body_bytes': size,
                 'content_type': headers.get('Content-Type'),
                 'www_authenticate': headers.get('WWW-Authenticate'), 'behavior_errors': [], 'contract_errors': []}
        try:
            if status != expected: raise AssertionError('Expected HTTP '+str(expected)+', observed '+str(status))
            if status != 204 and 'application/json' not in headers.get('Content-Type',''): raise AssertionError('Expected JSON Content-Type')
            if status == 204 and size != 0: raise AssertionError('204 contains a body')
            if status >= 400:
                if body.get('status') != status: raise AssertionError('Error status/body mismatch')
                if set(body) != {'status','code','message'}: raise AssertionError('Unexpected error envelope')
                names = {400:'BAD_REQUEST',401:'UNAUTHORIZED',403:'FORBIDDEN',404:'NOT_FOUND',409:'CONFLICT',503:'SERVICE_UNAVAILABLE'}
                if body['code'] != names.get(status): raise AssertionError('Unexpected functional error code')
                if not isinstance(body['message'],str) or not body['message']: raise AssertionError('Error message missing')
            if status == 401 and headers.get('WWW-Authenticate') != 'Bearer': raise AssertionError('Missing Bearer challenge')
            if check: check(body)
        except (AssertionError, KeyError, TypeError, ValueError) as ex:
            entry['behavior_errors'].append(str(ex))
        responses = contract['paths'][template or path][method.lower()]['responses']
        declared = responses.get(str(status))
        if declared is None:
            entry['contract_errors'].append('HTTP '+str(status)+' is not declared for this operation')
        else:
            schema = declared.get('content',{}).get('application/json',{}).get('schema')
            if schema:
                try: validate(schema, body)
                except (AssertionError, KeyError, TypeError, ValueError) as ex: entry['contract_errors'].append(str(ex))
                entry['schema_check'] = 'checked against declared keywords'
            elif status != 204:
                gap = method+' '+(template or path)+' '+str(status)+': response schema absent in OpenAPI'
                if gap not in report['contract_gaps']: report['contract_gaps'].append(gap)
                entry['schema_check'] = 'not available; DTO checks only'
        report['cases'].append(entry)
        print(name+': expected '+str(expected)+', observed '+str(status)+
              (' [mismatch]' if entry['behavior_errors'] or entry['contract_errors'] else ' [ok]'), flush=True)
        return body

    try:
        db_password, admin_password, user_password = [secrets.token_urlsafe(30) for _ in range(3)]
        private.extend([db_password,admin_password,user_password])
        admin_name = 'http-admin-'+uuid.uuid4().hex[:12]
        user_name = 'http-user-'+uuid.uuid4().hex[:12]
        role_name = 'HTTP_'+uuid.uuid4().hex[:16].upper()
        cid = docker('run','--detach','--name','techsupply-http-'+uuid.uuid4().hex[:12],
                     '--env-file','/dev/stdin','--tmpfs','/var/lib/postgresql/data:rw',
                     '--publish','127.0.0.1::5432','postgres:17-alpine',
                     stdin='POSTGRES_DB=auth_http\nPOSTGRES_USER=auth_http\nPOSTGRES_PASSWORD='+db_password+'\n')
        for _ in range(60):
            ready = subprocess.run(['docker','exec',cid,'pg_isready','-U','auth_http','-d','auth_http'],capture_output=True)
            if ready.returncode == 0: break
            time.sleep(0.5)
        else: raise RuntimeError('Disposable PostgreSQL did not become ready')
        port = docker('port',cid,'5432/tcp').split(':')[-1]
        env = {k:os.environ[k] for k in ['PATH','JAVA_HOME','LANG','HOME'] if k in os.environ}
        env.update(DB_URL='jdbc:postgresql://127.0.0.1:'+port+'/auth_http', DB_USERNAME='auth_http', DB_PASSWORD=db_password)
        env['SPRING_APPLICATION_JSON'] = json.dumps({'server':{'address':'127.0.0.1','port':0},'app':{'security':{
            'bootstrap-admin':{'enabled':True,'username':admin_name,'email':admin_name+'@example.test','password':admin_password}}}})
        log_path = runtime/'backend.log'
        log = log_path.open('w')
        app = subprocess.Popen(['java','-jar',str(jar)],cwd=repo/'erp-backend',env=env,stdout=log,stderr=subprocess.STDOUT)
        for _ in range(180):
            if app.poll() is not None: raise RuntimeError('Backend exited during startup; private log was not published')
            text = log_path.read_text()
            match = re.search(r'Tomcat started on port (\d+)', text)
            if match and 'Se creó la cuenta administradora inicial' in text:
                base = 'http://127.0.0.1:'+match.group(1); break
            time.sleep(0.5)
        else: raise RuntimeError('HTTP backend did not become ready')
        report['isolation'] = {'postgres_image':'postgres:17-alpine','published_address':'127.0.0.1',
                               'storage':'tmpfs; no existing volume','bootstrap':'explicit; generated credentials',
                               'server_address':base}
        migrations = docker('exec',cid,'psql','-U','auth_http','-d','auth_http','-Atc',
            'select version from flyway_schema_history where success order by installed_rank')
        report['applied_migrations'] = migrations.splitlines()
        count = docker('exec',cid,'psql','-U','auth_http','-d','auth_http','-Atc','select count(*) from usuarios')
        if count != '1': raise RuntimeError('Fresh controlled bootstrap did not create exactly one account')
        def login_fields(body):
            if set(body) != {'accessToken','tokenType','expiresAt','usuario'}: raise AssertionError('Unexpected login fields')
            if body['tokenType']!='Bearer' or not re.fullmatch('[A-Za-z0-9_-]{43}',body['accessToken']): raise AssertionError('Invalid token format')
            if set(body['usuario']) != {'id','username','correo','roles','permisos'}: raise AssertionError('Login identity fields')
        admin = case('setup_admin_login','POST','/auth/login',200,
                     {'identificador':admin_name,'password':admin_password},check=login_fields,setup=True)
        admin_token = admin['accessToken']; private.append(admin_token)
        case('setup_unprivileged_role','POST','/admin/roles',200,
             {'codigo':role_name,'nombre':'HTTP test','permisos':[]},token=admin_token,setup=True,
             check=lambda b:admin_fields('/admin/roles',[b]))
        user = case('setup_controlled_user','POST','/admin/users',200,
             {'username':user_name,'correo':user_name+'@example.test','password':user_password,'roles':[role_name]},
             token=admin_token,setup=True,check=lambda b:dto_user(b,user_name))
        result = case('login_correcto','POST','/auth/login',200,{'identificador':user_name,'password':user_password},check=login_fields)
        token = result['accessToken']; private.append(token)
        case('credenciales_incorrectas','POST','/auth/login',401,{'identificador':user_name,'password':secrets.token_urlsafe(30)})
        case('usuario_inexistente','POST','/auth/login',401,{'identificador':uuid.uuid4().hex,'password':secrets.token_urlsafe(30)})
        case('me_sin_token','GET','/auth/me',401)
        case('me_token_valido','GET','/auth/me',200,token=token,check=lambda b:dto_user(b,user_name))
        for path in ['/admin/users','/admin/roles','/admin/permissions']:
            case('sin_permisos_'+path.rsplit('/',1)[1],'GET',path,403,token=token)
            case('con_permisos_'+path.rsplit('/',1)[1],'GET',path,200,token=admin_token,
                 check=lambda b,p=path:admin_fields(p,b))
        case('logout','POST','/auth/logout',204,token=token)
        case('reutilizar_token','GET','/auth/me',401,token=token)
        case('logout_repetido','POST','/auth/logout',401,token=token)
        case('token_inexistente','GET','/auth/me',401,token=secrets.token_urlsafe(32))
        case('json_invalido','POST','/auth/login',400,raw='{')
        case('campos_vacios_login','POST','/auth/login',400,{'identificador':'','password':''})
        # Boundary probes verify @Valid errors without changing the contract to fit them.
        case('rol_invalido','POST','/admin/roles',400,{'codigo':'','nombre':'','permisos':[]},token=admin_token)
        case('permiso_invalido','POST','/admin/permissions',400,{'codigo':'','modulo':'','accion':''},token=admin_token)
        case('estado_invalido','PATCH','/admin/users/'+str(user['id'])+'/status',400,{},token=admin_token,
             template='/admin/users/{id}/status')
    except Exception as ex:
        report['execution_error'] = clean(type(ex).__name__+': '+str(ex))
    finally:
        if app is not None:
            app.terminate()
            try: app.wait(timeout=15)
            except subprocess.TimeoutExpired: app.kill();app.wait(timeout=5)
            report['cleanup']['backend_stopped'] = app.poll() is not None
        if log is not None: log.close()
        if cid:
            try:
                docker('rm','--force',cid)
                report['cleanup']['own_postgres_removed'] = True
            except Exception:
                report['cleanup']['own_postgres_removed'] = False
        shutil.rmtree(runtime)
        report['cases'] = clean(report['cases'])
        report['summary'] = {'cases':len(report['cases']),
            'behavior_failures':sum(bool(x['behavior_errors']) for x in report['cases']),
            'contract_failures':sum(bool(x['contract_errors']) for x in report['cases']),
            'schema_gaps':len(report['contract_gaps'])}
        args.report.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
        print(json.dumps(report['summary'],ensure_ascii=False),flush=True)
        print('Sanitized report: '+str(args.report),flush=True)
    return 2 if report.get('execution_error') or False in report['cleanup'].values() else int(
        report['summary']['behavior_failures']>0 or report['summary']['contract_failures']>0 or report['summary']['schema_gaps']>0)


if __name__ == '__main__':
    raise SystemExit(main())
