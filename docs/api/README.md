# Contratos HTTP de la Fase 2

La base de las rutas es `/api/v1`. Los contratos ejecutables son
[`openapi-auth.yaml`](openapi-auth.yaml),
[`openapi-crud-categorias.yaml`](openapi-crud-categorias.yaml),
[`openapi-audit.yaml`](openapi-audit.yaml) y
[`openapi-notifications.yaml`](openapi-notifications.yaml).
[`openapi-reporteria.yaml`](openapi-reporteria.yaml) lleva
`x-implementation-status: design-only`: sus 12 rutas son propuestas, no existen en el backend.

Todas las API activas devuelven errores con `status`, `code` y `message`.
`security.api` conserva sus mensajes funcionales y `WWW-Authenticate: Bearer` en `401`;
el tipo Java que serializan seguridad y los otros módulos es `common.api.ApiError`.
El componente de esquema `AuthError` se mantiene en el contrato de autenticación
para evitar cambiar referencias de clientes de OpenAPI: tiene los mismos campos
y señala su tipo Java mediante `x-java-type`. Los permisos se verifican en el backend;
la respuesta de `403` intenta registrar un evento de auditoría de mejor esfuerzo.

Las pruebas `AuthOpenApiContractTest`, `CategoriaOpenApiContractTest`,
`AuditOpenApiContractTest` y `NotificationOpenApiContractTest` comparan rutas,
parámetros y DTO principales con los controladores. Las pruebas HTTP de integración
complementan esa comprobación sobre Spring Security, Flyway y PostgreSQL desechable.
Ver la [matriz de integración](../fase-2/integracion/COMPONENTES_FASE_2.md).
