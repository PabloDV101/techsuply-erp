# Componente transversal de validación y errores HTTP

## Alcance

Implementado en `erp-backend/src/main/java/com/techsupply/erp_backend/common/api`.
La solución usa Jakarta Bean Validation, que ya proporciona
`spring-boot-starter-validation`; no agrega dependencias.

Los módulos describen restricciones de entrada en sus DTO y activan la validación
del cuerpo con `@Valid`. Las reglas que dependen de estado o datos del negocio
(por ejemplo, disponibilidad de inventario, permisos, crédito o transiciones de
estado) siguen siendo responsabilidad de sus servicios de dominio/aplicación.

No se añadió un validador personalizado: las restricciones cubiertas se expresan
con las anotaciones estándar. Un módulo puede declarar una restricción propia
cuando una regla de formato o estructura de entrada sea reutilizable y no dependa
de una entidad ni de una consulta de negocio.

La rúbrica de Fase 2 llama a este candidato «reglas de negocio centralizadas».
Aquí se centraliza el mecanismo de validación y el contrato HTTP, no las reglas
de stock, crédito o contabilidad: esas dependen del estado y de la transacción
del módulo propietario. Esta delimitación evita que el componente común se
acople a entidades de negocio o permita validar fuera de una operación atómica.

## Contrato de error

Los errores HTTP usan el objeto JSON común:

```json
{
  "status": 400,
  "code": "BAD_REQUEST",
  "message": "Solicitud inválida: nombre: no debe estar en blanco"
}
```

| Campo | Significado |
|---|---|
| `status` | Código HTTP numérico. |
| `code` | Nombre estable del estado HTTP (por ejemplo `BAD_REQUEST` o `INTERNAL_SERVER_ERROR`). Para estados no estándar se usa `HTTP_<número>`. |
| `message` | Mensaje seguro para el cliente. En restricciones de DTO incluye nombres de campo y mensajes declarados por Bean Validation. |

Se mantiene la forma de tres campos de `AuthError` documentada por el componente
de autenticación. Los controladores o módulos pueden usar consejos de excepción
de mayor prioridad para conservar mensajes funcionales específicos; deben
mantener el mismo contrato JSON.
Actualmente `AuthExceptionHandler` tiene mayor prioridad que el consejo global
en `security.api`; fuera de ese paquete actúa `GlobalApiExceptionHandler`.

## Respuestas gestionadas

- `400 BAD_REQUEST`: DTO inválido, violación de Bean Validation, tipo/formato
  inválido en ruta o parámetro de consulta, parámetro requerido ausente o JSON malformado.
- `401 UNAUTHORIZED`: respuesta existente de los filtros de autenticación.
- `403 FORBIDDEN`: respuesta existente de seguridad o autorización del módulo.
- `404 NOT_FOUND`, `405 METHOD_NOT_ALLOWED`, `415 UNSUPPORTED_MEDIA_TYPE`:
  recurso, método o tipo de contenido no admitido.
- `409 CONFLICT`: conflicto de integridad conocido.
- `503 SERVICE_UNAVAILABLE`: fallo conocido de acceso a datos.
- `500 INTERNAL_SERVER_ERROR`: excepción inesperada; el cliente recibe un
  mensaje genérico. El detalle técnico se escribe solo en el log del servidor.

No se devuelven stack traces, SQL, mensajes de PostgreSQL ni nombres de clases
internas en el cuerpo de respuesta.

## Uso en módulos

```java
public record CrearProductoRequest(
    @NotBlank @Size(max = 160) String nombre,
    @NotNull @Positive BigDecimal precio
) {}

@PostMapping
ProductoResponse crear(@Valid @RequestBody CrearProductoRequest request) {
    return servicio.crear(request);
}
```

Para validar parámetros de métodos se emplean restricciones Jakarta en sus
parámetros y la validación de métodos de Spring. Los tipos numéricos de rutas y
consultas se convierten mediante Spring MVC; un valor que no puede convertirse
se responde como `400`.

No se debe usar una restricción de DTO para consultar disponibilidad o aplicar
reglas dependientes de varias entidades. Por ejemplo, el DTO puede verificar que
la cantidad solicitada sea positiva; el módulo de Inventario verifica dentro de
su operación transaccional que haya existencias suficientes.

## Verificación

Desde `erp-backend/`:

```bash
./mvnw test
./mvnw -o verify
```

`GlobalApiExceptionHandlerTest` verifica DTO válido, campo ausente/en blanco,
longitud y rango numérico, conversión de parámetro de ruta, JSON malformado,
formato de error y ocultamiento de detalles de excepciones inesperadas. Es una
prueba MVC aislada: no requiere PostgreSQL ni reemplaza pruebas de integración
con los controladores y filtros de cada módulo.

En la última verificación de esta rama, `./mvnw -o verify` terminó con 31 pruebas
Surefire y 27 Failsafe aprobadas; las de integración usaron PostgreSQL desechable
mediante Testcontainers, Flyway V1/V2 y validación de esquema Hibernate. El script
`python3 scripts/verify-auth-http.py --report /tmp/techsupply-auth-validation-http-results.json`
verificó 23 casos HTTP de auth, sin diferencias de comportamiento o contrato.
Estas pruebas demuestran compatibilidad con el módulo de seguridad, pero aún falta
probar el componente en un segundo módulo real y medir la cobertura exigida por
RNF-12. No hay evidencia de cumplimiento de todos los criterios de reutilización
de RNF-11 o de la rúbrica final.

El componente no tiene entidades ni migraciones propias. Sus códigos de error
dependen del estado HTTP, no del tipo de identificador de las tablas. El esquema
actual de seguridad usa `bigint`/`Long` desde Flyway V1 y las entidades Java;
la elección de UUID para otros módulos, si se reconsidera, requiere una decisión
de compatibilidad separada y no forma parte de la validación HTTP.
