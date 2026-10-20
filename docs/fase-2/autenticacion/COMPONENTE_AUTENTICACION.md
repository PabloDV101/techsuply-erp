# Componente de autenticación y autorización — TechSupply Solutions

Implementación: `erp-backend/src/main/java/com/techsupply/erp_backend/security`.
Contrato: `docs/api/openapi-auth.yaml`. Java 21 y Spring Boot 4.1.1.

## 1. Comportamiento y límites

- Login por username o correo normalizado, con BCrypt coste 12. El hash nunca se expone en DTO.
- Contraseñas de altas: al menos 12 caracteres y como máximo 72 bytes UTF-8.
  Login acepta contraseñas no vacías hasta ese límite de bytes. No se recortan contraseñas.
- Usuario inexistente, inactivo, bloqueado o contraseña incorrecta: `401` con mensaje genérico.
  Las ramas que no comparan el hash real ejecutan una comparación BCrypt ficticia.
  Esto reduce diferencias de cómputo; no garantiza tiempos idénticos en red.
- Cinco fallos bloquean 15 minutos. Un bloqueo vencido inicia una ventana nueva de intentos;
  un login correcto reinicia intentos/bloqueo y actualiza el último acceso.
- Login bloquea la fila de usuario con `FOR UPDATE` dentro de una transacción. El `401`
  por credenciales incorrectas no revierte el incremento de intentos.
- Tokens opacos de 32 bytes aleatorios (`SecureRandom`), Base64 URL sin relleno.
  El servidor almacena SHA-256, nunca el token plano. Vigencia absoluta: dos horas.
- El filtro exige formato opaco, sesión vigente/no revocada y cuenta activa/no bloqueada.
  Lee roles y permisos actuales; roles inactivos no aportan autoridades.
- Logout revoca la sesión actual. Una solicitud posterior con ese token recibe `401`,
  incluido un segundo logout. Una solicitud ya autenticada antes del commit de revocación
  puede terminar; no existe cancelación retroactiva de operaciones en curso.
- `/me` devuelve la identidad y permisos desde persistencia, sin hash de contraseña.
- El filtro se registra solo en Spring Security. Su cadena administra y limpia el contexto.

La mitigación por cuenta no sustituye un limitador por origen/IP ni pruebas de carga.
No se afirma protección completa contra fuerza bruta o enumeración temporal.

## 2. Autorización y HTTP

Públicos: `POST /api/v1/auth/login`, `/actuator/health` y preflight `OPTIONS`.
El resto exige autenticación. `OPTIONS` no configura CORS.

| Operación | Permiso |
|---|---|
| Consultar usuarios y activar/desactivar | `SEGURIDAD_USUARIOS_ADMIN` |
| Crear usuario con roles | `SEGURIDAD_USUARIOS_ADMIN` y `SEGURIDAD_ROLES_ADMIN` |
| Consultar/crear roles, reemplazar roles o permisos del rol | `SEGURIDAD_ROLES_ADMIN` |
| Consultar/crear permisos | `SEGURIDAD_PERMISOS_ADMIN` |

Asignar roles o permisos exige además poseer los permisos delegados. Esto impide crear
una segunda cuenta para adquirir permisos que el actor no tiene. Los códigos de roles se
traducen a `ROLE_<CODIGO>`; los códigos de permisos son autoridades directas.
Los permisos nuevos creados en el catálogo no se conceden automáticamente al creador.
Su concesión inicial necesita una migración revisada que los asigne a un rol autorizado;
no hay un bypass implícito de la regla de delegación para ADMINISTRADOR.
La API protege al último administrador frente a desactivación y retirada del rol.
Las modificaciones de usuarios y asignaciones se coordinan con un bloqueo transaccional
PostgreSQL para preservar invariantes entre instancias.

Respuestas exitosas conservadas: login/me/administración `200`, logout `204` sin cuerpo.
Errores del componente y de seguridad:

```json
{"status":401,"code":"UNAUTHORIZED","message":"Autenticación requerida"}
```

- `400`: JSON, parámetros o validación inválidos.
- `401`: credenciales o autenticación inválidas; incluye `WWW-Authenticate: Bearer`.
- `403`: identidad válida sin permiso suficiente o delegación no autorizada.
- `404`: usuario/rol no encontrado.
- `409`: duplicación, conflicto de integridad o protección del último administrador.
- `503`: fallo de acceso a datos, sin SQL ni detalle de PostgreSQL en la respuesta.

No hay cookies de autenticación ni sesión HTTP. CSRF está desactivado para Bearer en
`Authorization`; si se adoptan cookies debe revisarse. CORS y la integración React siguen
pendientes; este cambio no define orígenes permitidos ni modifica el frontend.

## 3. Esquema y migraciones

Para seguridad, `src/main/resources/db/migration/` contiene:

- `V1__security_schema.sql`: usuarios, roles, permisos, relaciones y sesiones; índices,
  valores predeterminados, FK internas y restricciones.
- `V2__security_catalogs.sql`: rol ADMINISTRADOR y tres permisos administrativos.
  No crea cuentas ni contraseñas.
- `V4__audit_events.sql`: tabla de auditoría y permiso `SEGURIDAD_AUDITORIA_LEER`
  asignado al rol ADMINISTRADOR existente. Login, logout, bootstrap y 403 publican
  eventos mediante el componente descrito en `docs/fase-2/auditoria/COMPONENTE_AUDITORIA.md`.

El mismo directorio contiene V3 para categorías y V5 para la bandeja APP; la
autenticación no publica avisos automáticamente. Ver
`docs/fase-2/notificaciones/COMPONENTE_NOTIFICACIONES.md`.

Los identificadores son `bigint`/`Long`, coherentes con JPA y OpenAPI existentes.
El DBML general y las vistas UML usan Long/bigint: seguridad refleja V1/V2 y
categorías de producto refleja V3, auditoría V4 y notificaciones internas V5.
Los demás catálogos y módulos conservan su
carácter de diseño objetivo. La propagación documental de tipos a esas tablas
objetivo no crea tablas ni migra instalaciones existentes. Ver `docs/diagrams/README.md`.

`identificadores_usuario` y su trigger mantienen un espacio único para username/correo,
incluyendo colisiones cruzadas y altas concurrentes. No es un catálogo funcional adicional.
`usuarios_roles` conserva asignador/fecha y `usuarios.creado_en` tiene valor predeterminado.
Empleado/cliente son referencias escalares opcionales y mutuamente excluyentes;
las FK a Gestión Maestra quedan pendientes hasta que existan sus tablas compatibles.

**Estas migraciones iniciales se destinan a bases vacías.** No deben ejecutarse sobre una
base existente sin inventario de esquema e historial Flyway y un plan de adopción específico.
`baseline-on-migrate` está desactivado; `clean` está deshabilitado. Hibernate usa `validate`,
no crea ni actualiza tablas. No editar migraciones ya aplicadas en entornos compartidos.

## 4. Compilar, probar y arrancar

Desde `erp-backend/`, con Java 21:

```bash
./mvnw -v
./mvnw -DskipTests compile
./mvnw test
./mvnw verify
```

`test` ejecuta pruebas unitarias sin base de datos. `verify` además empaqueta y ejecuta las
clases `*IT` mediante Failsafe. Requiere Docker accesible y la imagen `postgres:17-alpine`
(descargada automáticamente si falta), además de acceso a Maven si faltan dependencias.

Las integraciones suministran siempre la URL/usuario/contraseña de un contenedor desechable,
sin reutilización, con contraseña aleatoria y sin volúmenes de bases existentes.
No usan `DB_URL` del desarrollador. Docker no disponible hace fallar la suite; no se omite
silenciosamente. No hay mocks de Spring Security, repositorios, Flyway ni PostgreSQL en ellas.

Reportes: `target/surefire-reports/` (unitarias), `target/failsafe-reports/` (integración).
Para ejecutar integración sin repetir unitarias ni empaquetado, tras compilar pruebas:

```bash
./mvnw test-compile failsafe:integration-test failsafe:verify
```

Para arrancar manualmente, configurar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` hacia una
base local nueva y vacía preparada para este esquema. `SERVER_PORT` es opcional (8080).
`DB_PASSWORD` es obligatorio. El `.env` de Compose no se carga automáticamente en Spring.
Después:

```bash
./mvnw spring-boot:run
```

No usar el comando de arranque para probar una base existente sin revisar su compatibilidad.
El bootstrap administrativo está desactivado por defecto. Activarlo explícitamente mediante
`APP_SECURITY_BOOTSTRAP_ADMIN_ENABLED=true`, `APP_SECURITY_BOOTSTRAP_ADMIN_USERNAME`,
`APP_SECURITY_BOOTSTRAP_ADMIN_EMAIL` y `APP_SECURITY_BOOTSTRAP_ADMIN_PASSWORD`.
Suministrar valores locales por un mecanismo seguro y desactivarlo después de crear la cuenta.
No almacenar esas credenciales en Git. Requiere los catálogos de V2.

## 5. Pruebas y pendientes

- `AuthOpenApiContractTest`: compara rutas, códigos 400, campos, tipos y nulabilidad de OpenAPI con controladores y DTO reales, sin base de datos.
- `AuthServiceTest`: login, errores genéricos, intentos/bloqueo, reinicio y revocación; repositorios simulados.
- `PasswordValidationTest`: validación de longitud UTF-8 y entradas vacías.
- `OpaqueTokenServiceTest`: formato/longitud, muestra sin duplicados y vector SHA-256 conocido.
  Una muestra sin duplicados no demuestra por sí sola seguridad criptográfica.
- `AuthIntegrationIT`: cadena Spring Security real, API con MockMvc, PostgreSQL/Flyway,
  persistencia, revocación, permisos, errores y concurrencia.
- `ErpBackendApplicationIT`: carga de contexto con PostgreSQL desechable y validación de esquema.

La prueba de contexto anterior dejó de ejecutarse como unitaria para impedir conexiones
accidentales a la base del desarrollador. Las pruebas de integración no son pruebas E2E de React.

Pendientes fuera de este cambio: auditoría RF-SEG-03, FK de Gestión Maestra, CORS/frontend,
limitación por origen, rendimiento con carga, política de limpieza de sesiones antiguas y
adopción del esquema en instalaciones existentes. No se acredita cumplimiento integral de Fase 2.

## 6. Resultado de verificación — 19 de octubre de 2026

Comando ejecutado desde `erp-backend/`: `./mvnw verify`.
Resultado final: **BUILD SUCCESS** con Java 21 y Spring Boot 4.1.1.

| Suite | Pruebas | Fallos/errores/omitidas |
|---|---:|---|
| AuthServiceTest | 11 | 0 / 0 / 0 |
| PasswordValidationTest | 1 | 0 / 0 / 0 |
| OpaqueTokenServiceTest | 2 | 0 / 0 / 0 |
| AuthOpenApiContractTest | 9 | 0 / 0 / 0 |
| AuthIntegrationIT | 26 | 0 / 0 / 0 |
| ErpBackendApplicationIT | 1 | 0 / 0 / 0 |
| Total | 50 | 0 / 0 / 0 |

PostgreSQL 17 desechable: Flyway aplicó V1/V2 y Hibernate validó el esquema.
La suite incluyó los trece escenarios mínimos solicitados, protección de delegación,
colisiones de identificadores, cambios de roles y pruebas concurrentes de intentos y del
último administrador. También verificó que repetir la desactivación de un administrador
ya inactivo no produzca un conflicto de último administrador.

Se comprobó el YAML OpenAPI sin claves duplicadas, sus 12 operaciones, las referencias al
esquema de errores y el 409 de protección del último administrador. No sustituye un validador
completo de OpenAPI ni una prueba exhaustiva de contrato.
No se midió cobertura porcentual, carga ni seguridad de un despliegue público.

## 7. Contrato HTTP y reproducción de la verificación real

OpenAPI 1.1.1 completa la documentación de las respuestas existentes, sin cambiar rutas,
DTO ni comportamiento de producción. Los `400` por validación se declaran en todas las
operaciones que reciben DTO con `@Valid`, incluidos alta de rol, alta de permiso y cambio
de estado de usuario. Son rechazos correctos del backend; no se sustituyeron por códigos de éxito.

Todas las respuestas `200` tienen esquema: LoginResponse, UsuarioResumen, UsuarioResponse,
RolResponse y PermisoResponse. Los campos presentes pero nulos (`empleadoId`, `clienteId`,
`ultimoAcceso` y descripciones) son requeridos y `nullable`; las listas administrativas tienen
esquema de sus elementos. `additionalProperties: false` excluye campos ajenos, como hashes.
El esquema de error continúa siendo `{status, code, message}`. Logout conserva `204` sin cuerpo.

Desde la raíz del repositorio, con Java 21, Docker, Python 3 y PyYAML disponibles:

```bash
cd erp-backend
./mvnw -DskipTests package
cd ..
python3 erp-backend/scripts/verify-auth-http.py --report /tmp/techsupply-auth-http-results.json
```

El script inicia el JAR en una dirección loopback con puerto aleatorio y crea PostgreSQL 17
con almacenamiento temporal, sin usar Compose ni bases o volúmenes existentes. Habilita el
bootstrap únicamente en ese proceso con una cuenta y contraseña aleatorias; crea mediante
el API un rol y una cuenta sin permisos. No supone que exista una cuenta administrativa previa.
Detiene su proceso backend y retira su propio contenedor al finalizar.

Las contraseñas y tokens se mantienen en memoria y se ocultan en el informe. El log privado
de arranque se elimina. El informe contiene códigos esperados/observados, respuestas depuradas,
esquemas ausentes, discrepancias de contrato y estado de limpieza. No subir informes con datos
reales ni usar este script para verificar una base compartida.

Ejecuta 23 casos, incluidos los preparativos: login correcto e incorrecto, usuario inexistente,
`/me` con y sin token, permisos administrativos, logout, rechazo posterior del token, logout
repetido, token inexistente, JSON inválido y validaciones de entrada administrativas.
Su código de salida es 0 si no encuentra discrepancias; 1 si hay fallos de comportamiento,
contrato o esquemas ausentes; 2 si falla la ejecución o la limpieza del entorno aislado.

Valida los tipos, campos requeridos, referencias, nulabilidad, enum, patrón del token,
unicidad de conjuntos y exclusión de campos adicionales utilizados en este contrato.
No es un validador general de OpenAPI. La verificación cubre HTTP local real, sin mocks;
no acredita TLS/proxy, interfaz React, carga ni seguridad de producción.

Resultado de esta verificación HTTP, 19 de octubre de 2026: **23 casos aprobados**, sin
fallos de comportamiento, discrepancias de códigos ni esquemas ausentes. Se aplicaron V1/V2
en PostgreSQL desechable. Se confirmó que el proceso backend terminó y su contenedor se retiró.
El comando Maven `./mvnw -o verify` también terminó con **BUILD SUCCESS**: 23 pruebas unitarias
(incluidas nueve de contrato) y 27 de integración, sin fallos, errores ni omisiones.
La opción `-o` requiere disponer previamente de las dependencias en la caché Maven.
