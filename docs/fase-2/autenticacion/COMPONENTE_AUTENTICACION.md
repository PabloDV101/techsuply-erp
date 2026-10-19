# Componente de autenticación y autorización — TechSupply Solutions

**Documento:** `COMPONENTE_AUTENTICACION.md`  
**Estado:** diseño y contrato inicial; requiere verificar la implementación Java real.  
**Stack objetivo:** Java 21, Spring Boot, Spring Security, Spring Data JPA, Flyway y PostgreSQL.  
**Paquete base propuesto:** `com.techsupply.erp_backend.security`.

---

## 1. Propósito y alcance

El componente centraliza la autenticación de usuarios y la autorización de operaciones del ERP. Los demás módulos (Compras, Ventas, Inventario, Contabilidad y RR. HH.) deben consultar la identidad y aplicar permisos sin implementar su propio mecanismo de login.

Requisitos relacionados:

- **RF-SEG-01:** autenticación.
- **RF-SEG-02:** roles y permisos.
- **RF-SEG-03:** auditoría de operaciones críticas; requiere integración con el componente de auditoría.
- **RF-SEG-04:** gestión de sesión/token, expiración y cierre de sesión.

Esta documentación describe el diseño elegido y el contrato esperado. No sustituye las pruebas: antes de considerar el componente terminado, hay que comprobar que las clases Java y los endpoints descritos existen realmente en el repositorio y funcionan.

## 2. Decisiones de diseño

### 2.1 Tokens opacos

La primera propuesta usa tokens opacos tipo Bearer en lugar de JWT. El servidor genera un token aleatorio y entrega su valor al cliente una sola vez. En PostgreSQL almacena únicamente su hash SHA-256, asociado al usuario, fecha de emisión, vencimiento y revocación.

Ventajas:
- Permite revocar sesiones de inmediato.
- No requiere administrar claves de firma JWT.
- El token original no se conserva en la base de datos.

Coste:
- Cada petición autenticada debe consultar el estado del token en PostgreSQL, salvo que se añada una caché cuidadosamente diseñada.
- Hay que medir el rendimiento para verificar el objetivo de latencia del SRS.

La configuración de Spring Security puede usar `SessionCreationPolicy.STATELESS` porque no depende de una sesión HTTP tradicional. Eso no hace que el token opaco sea “sin estado”: la sesión se mantiene en `tokens_sesion`.

### 2.2 Contraseña y protección de cuentas

- Las contraseñas se almacenan mediante un hash BCrypt, nunca en texto plano.
- La configuración inicial propuesta usa un coste BCrypt de 12.
- Se propone bloqueo de 15 minutos después de 5 intentos fallidos consecutivos.
- Se propone una duración de token de 2 horas.
- Las cuentas inactivas o bloqueadas no deben autenticarse.
- Los errores de login deben ser genéricos para no revelar si un usuario existe.
- El bootstrap inicial del administrador debe ser explícito, temporal y mediante variables de entorno; nunca se deben incluir contraseñas conocidas en el repositorio.

Los límites de intentos y duración son decisiones iniciales y deben confirmarse con el equipo/profesor.

### 2.3 Roles y permisos

Los roles agrupan responsabilidades. Los permisos representan acciones concretas. Las autoridades pueden expresarse como `ROLE_<CODIGO_ROL>` y códigos de permiso sin prefijo, por ejemplo `SEGURIDAD_USUARIOS_ADMIN`.

Ejemplo de protección en Spring Security:

```java
@PreAuthorize("hasAuthority('SEGURIDAD_USUARIOS_ADMIN')")
```

La autorización siempre debe comprobarse en el backend. Ocultar botones en React no constituye un control de seguridad.

## 3. Modelo de datos

| Tabla | Propósito |
|---|---|
| `usuarios` | Identidad, correo, hash de contraseña, estado, referencias opcionales a empleado/cliente, intentos fallidos, bloqueo y último acceso. |
| `roles` | Catálogo de roles. |
| `permisos` | Catálogo de permisos por módulo y acción. |
| `usuarios_roles` | Relación muchos-a-muchos entre usuarios y roles; puede guardar quién asignó el rol y cuándo. |
| `roles_permisos` | Relación muchos-a-muchos entre roles y permisos. |
| `tokens_sesion` | Hash del token, usuario, emisión, vencimiento y revocación. |

La tabla `eventos_auditoria` pertenece al componente de auditoría y no debe duplicarse en esta rama.

### Integración con Gestión Maestra

Las referencias `empleado_id` y `cliente_id` deben tener claves foráneas en el esquema integrado si corresponden a las tablas maestras del modelo aprobado. Si esas tablas todavía no existen, se puede probar el componente de manera aislada y añadir las FK mediante una migración de integración posterior. Esa ausencia debe permanecer como tarea pendiente, no como condición final de producción.

## 4. Flujo de autenticación

1. El cliente envía identificador y contraseña a `POST /api/v1/auth/login` por HTTPS.
2. El backend busca al usuario por nombre de usuario o correo.
3. Verifica que la cuenta esté activa y no esté bloqueada.
4. Compara la contraseña ingresada con el hash BCrypt.
5. Si el login falla, actualiza el contador de intentos y aplica el bloqueo cuando corresponda.
6. Si tiene éxito, reinicia el contador, genera un token aleatorio y guarda solo su hash en `tokens_sesion`.
7. El backend devuelve el token al cliente.
8. Para cada petición protegida, el cliente envía `Authorization: Bearer <token>`.
9. El filtro valida existencia, expiración, revocación y estado de la cuenta; después establece la identidad y autoridades en el contexto de seguridad.
10. `POST /api/v1/auth/logout` revoca el token actual.

Códigos HTTP esperados:
- `200`: solicitud exitosa.
- `204`: logout exitoso sin cuerpo de respuesta.
- `400`: petición inválida.
- `401`: falta autenticación o las credenciales/token no son válidos.
- `403`: identidad válida, pero sin permiso suficiente.
- `409`: conflicto, por ejemplo nombre de usuario o correo duplicado.

## 5. Contrato de endpoints

La especificación `docs/openapi-auth.yaml` describe el contrato esperado. Las rutas deben confirmarse contra los controladores reales.

| Método | Ruta | Acceso esperado | Propósito |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | Público | Iniciar sesión. |
| `POST` | `/api/v1/auth/logout` | Token válido | Revocar la sesión actual. |
| `GET` | `/api/v1/auth/me` | Token válido | Consultar usuario y autoridades actuales. |
| `GET` | `/api/v1/admin/users` | `SEGURIDAD_USUARIOS_ADMIN` | Listar usuarios. |
| `POST` | `/api/v1/admin/users` | `SEGURIDAD_USUARIOS_ADMIN` | Crear usuario y asignar roles. |
| `PATCH` | `/api/v1/admin/users/{id}/status` | `SEGURIDAD_USUARIOS_ADMIN` | Activar/desactivar una cuenta. |
| `PUT` | `/api/v1/admin/users/{id}/roles` | `SEGURIDAD_ROLES_ADMIN` | Reemplazar roles del usuario. |
| `GET` | `/api/v1/admin/roles` | `SEGURIDAD_ROLES_ADMIN` | Consultar roles. |
| `POST` | `/api/v1/admin/roles` | `SEGURIDAD_ROLES_ADMIN` | Crear rol. |
| `PUT` | `/api/v1/admin/roles/{id}/permissions` | `SEGURIDAD_ROLES_ADMIN` | Reemplazar permisos de un rol. |
| `GET` | `/api/v1/admin/permissions` | `SEGURIDAD_PERMISOS_ADMIN` | Consultar permisos. |
| `POST` | `/api/v1/admin/permissions` | `SEGURIDAD_PERMISOS_ADMIN` | Crear permiso. |

Los endpoints administrativos no deben considerarse implementados solo porque aparecen en OpenAPI. Cada uno debe existir en el código y contar con pruebas.

## 6. ¿Para qué sirve `openapi-auth.yaml`?

OpenAPI es una descripción legible por herramientas del contrato HTTP. No ejecuta código, no crea tablas y no implementa controladores.

Se utiliza para:
- Documentar rutas, métodos, parámetros, cuerpos y respuestas.
- Generar una interfaz interactiva como Swagger UI.
- Generar clientes o tipos para frontend.
- Revisar si frontend y backend acuerdan el mismo contrato.
- Crear pruebas de contrato.

### Cómo abrirlo

Opciones:
1. Usar Swagger Editor en el navegador y abrir/importar `openapi-auth.yaml`.
2. Si el proyecto incorpora Swagger UI mediante `springdoc-openapi`, exponer la interfaz desde el backend. La URL habitual es `/swagger-ui/index.html`, pero depende de la configuración y de la dependencia instalada.
3. Mantenerlo como documentación versionada en `docs/api/`.

Si se utiliza un editor en línea, no pegues tokens, contraseñas, datos de clientes ni otros secretos.

La URL del servidor en el archivo es relativa (`/api/v1`), por lo que el editor puede necesitar que se indique el host local real, por ejemplo `http://localhost:8080/api/v1`, para probar las solicitudes. Esto solo funcionará cuando el backend esté ejecutándose y los endpoints existan.

## 7. Cómo probar manualmente la API

### 7.1 Comprobar prerrequisitos

Desde la raíz del repositorio, revisa la rama y el estado:

```bash
git branch --show-current
git status --short
```

Levanta PostgreSQL con el Compose que ya utiliza el proyecto. Por ejemplo, si el archivo es `deploy/docker-compose.yml`:

```bash
docker compose -f deploy/docker-compose.yml up -d
docker compose -f deploy/docker-compose.yml ps
```

Confirma que el `application.yml` del backend use el nombre de base, usuario, contraseña y puerto correctos. No subas `.env` ni contraseñas a Git.

### 7.2 Ejecutar migraciones y backend

Desde `backend/`:

```bash
./mvnw test
./mvnw spring-boot:run
```

Si no existe Maven Wrapper, usa `mvn test` y `mvn spring-boot:run`.

Al arrancar, revisa los logs para confirmar que Flyway ejecutó las migraciones y que Spring Boot terminó de iniciar. Si Hibernate usa `ddl-auto: validate`, la aplicación puede fallar si las tablas esperadas no existen o no coinciden con las entidades.

**Importante:** antes de ejecutar estos pasos, confirma que el código Java de autenticación está realmente copiado al backend y que compila. Los archivos SQL y OpenAPI por sí solos no crean un API funcional.

### 7.3 Probar login con `curl`

Cuando el endpoint esté implementado y exista una cuenta de prueba creada de forma segura, ejecuta:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{
    "identificador": "admin@techsupply.test",
    "password": "REEMPLAZA_CON_LA_CLAVE_LOCAL"
  }'
```

No copies literalmente el usuario ni la contraseña de ejemplo si no los has creado. Usa las credenciales de una cuenta local de pruebas. No uses una contraseña real ni la guardes en Git.

Respuesta esperada para un login exitoso (valores ilustrativos):

```json
{
  "accessToken": "<token-devuelto-por-el-servidor>",
  "tokenType": "Bearer",
  "expiresAt": "2026-10-18T18:00:00Z",
  "usuario": {
    "id": 1,
    "username": "admin",
    "correo": "admin@techsupply.test",
    "roles": ["ADMINISTRADOR"],
    "permisos": ["SEGURIDAD_USUARIOS_ADMIN"]
  }
}
```

La estructura exacta debe coincidir con el DTO real. Si la respuesta es `401`, revisa credenciales, estado/bloqueo de la cuenta y logs; no desactives controles de seguridad para hacer pasar la prueba.

### 7.4 Probar `/me`

Copia el token devuelto en la variable de shell. No lo compartas ni lo guardes en un archivo versionado:

```bash
read -rsp "Token de acceso: " TOKEN
echo

curl -i http://localhost:8080/api/v1/auth/me \
  -H "Authorization: Bearer $TOKEN"
```

La respuesta debería mostrar la identidad y roles/permisos actuales. Borra la variable al terminar:

```bash
unset TOKEN
```

### 7.5 Probar un endpoint sin token y uno sin permiso

Petición sin token:

```bash
curl -i http://localhost:8080/api/v1/auth/me
```

Resultado esperado: `401 Unauthorized`.

Para probar `403 Forbidden`, autentica un usuario válido que no tenga el permiso requerido y llama a un endpoint administrativo que realmente esté protegido. No basta con probarlo con el administrador, porque ese usuario probablemente sí tendrá acceso.

### 7.6 Probar logout y revocación

Con el token válido:

```bash
curl -i -X POST http://localhost:8080/api/v1/auth/logout \
  -H "Authorization: Bearer $TOKEN"
```

Resultado esperado: `204 No Content`.

Después vuelve a llamar a `/me` con el mismo token. Resultado esperado: `401 Unauthorized`. Si el token sigue funcionando, hay un defecto de revocación que debe corregirse.

## 8. Cómo ejecutar y entender las pruebas automatizadas

Las pruebas automatizadas son código que comprueba comportamientos esperados sin que tengas que hacer cada petición manualmente. En Spring Boot normalmente se usa JUnit 5; Mockito puede sustituir repositorios/servicios para pruebas unitarias y Spring Boot Test puede levantar partes de la aplicación.

### Pruebas unitarias

Prueban una clase aislada con dependencias simuladas. Son rápidas y sirven para verificar:
- Login correcto.
- Contraseña incorrecta.
- Cuenta inactiva.
- Bloqueo tras los intentos configurados.
- Que se persista el hash del token y no el token original.

Comando:

```bash
cd backend
./mvnw -Dtest=AuthServiceTest test
```

El nombre exacto de la clase de prueba puede variar. Comprueba primero qué archivos existen en `src/test/java`.

### Pruebas de integración

Prueban varias piezas reales juntas: Spring, repositorios, migraciones y PostgreSQL. Para autenticación deberían cubrir:
- Flyway desde una base vacía.
- Login y persistencia de sesión.
- `/me` con token válido.
- Logout y rechazo del token revocado.
- Expiración de token.
- Permisos (`403`) y ausencia de autenticación (`401`).

Comando general:

```bash
./mvnw verify
```

Este comando solo ejecutará las pruebas configuradas en el proyecto. Si todavía no existen pruebas de integración, no las crea automáticamente; hay que escribirlas.

### Cómo leer los resultados de Maven

- `BUILD SUCCESS`: los objetivos ejecutados terminaron correctamente; revisa cuántas pruebas se ejecutaron.
- `BUILD FAILURE`: hay que leer el primer error relevante y el nombre de la prueba/clase que falló.
- `Tests run: 0`: el comando no encontró pruebas coincidentes o no están configuradas como esperabas.
- Fallo de conexión o autenticación PostgreSQL: revisa Docker, URL JDBC y variables de entorno.
- Fallo de Flyway o `ddl-auto: validate`: revisa versiones de migración, tablas y entidades.

Los reportes de pruebas suelen aparecer en `backend/target/surefire-reports/`.

## 9. Pruebas de aceptación antes de fusionar

- [ ] Las migraciones funcionan desde una base PostgreSQL de pruebas vacía.
- [ ] Login correcto devuelve un token.
- [ ] Contraseña incorrecta devuelve `401`.
- [ ] Cuenta inactiva/bloqueada no inicia sesión.
- [ ] La base almacena el hash del token, no el token original.
- [ ] `/me` funciona con token válido.
- [ ] `/me` sin token devuelve `401`.
- [ ] Un usuario sin permiso recibe `403` al acceder a un endpoint protegido.
- [ ] Logout revoca la sesión y el token ya no funciona.
- [ ] Token expirado o revocado es rechazado.
- [ ] Desactivar una cuenta revoca sus sesiones según la política aprobada.
- [ ] Las pruebas automatizadas realmente se ejecutaron y sus resultados se revisaron.
- [ ] OpenAPI coincide con los endpoints y DTO implementados.
- [ ] Las claves foráneas a Gestión Maestra se agregan al esquema integrado.
- [ ] Se integra el componente de auditoría para cubrir RF-SEG-03.

## 10. Reutilización entre módulos/proyectos

Los módulos consumidores deberían depender de un contrato de aplicación, por ejemplo `AuthenticatedUserProvider`, y consumir un objeto como `AuthenticatedActor`. Deben evitar depender directamente del filtro de tokens o leer `SecurityContextHolder` desde cualquier clase de negocio.

Si otro proyecto usa JWT u otro proveedor de identidad, puede implementar un adaptador que entregue el mismo contrato. Así, Compras o Inventario no necesitan conocer el mecanismo de autenticación subyacente.

Los códigos de permisos deben acordarse entre módulos y documentarse. No se deben aceptar roles enviados por el cliente como prueba de autorización.

## 11. Estado y tareas pendientes

Antes de declarar terminado el componente:
1. Confirmar que existen y compilan todas las clases Java mencionadas en el contrato.
2. Ejecutar `./mvnw test` en el backend real.
3. Añadir pruebas de integración con PostgreSQL y pruebas HTTP.
4. Verificar que cada endpoint de OpenAPI esté implementado; retirar o marcar como pendiente cualquier ruta que no exista.
5. Añadir las FK a empleados/clientes cuando las tablas maestras estén disponibles.
6. Integrar el contrato de auditoría para los eventos críticos.
7. Actualizar diagramas de secuencia y componentes con la decisión de tokens opacos.
8. Revisar el diff y hacer commit únicamente cuando los resultados sean reproducibles.

---

**Nota de estado:** `openapi-auth.yaml` describe el contrato; no implementa los endpoints. Las migraciones crean tablas, pero tampoco implementan el API. La autenticación solo puede probarse de extremo a extremo cuando los controladores, servicios, filtro de seguridad, configuración y repositorios estén presentes y funcionando en el backend.
