# Componente de notificaciones internas — Fase 2

Estado: **infraestructura APP implementada; productores de negocio pendientes**. RF-SYS-02
es de prioridad Baja/MVP opcional. El SRS nombra stock bajo, aprobación pendiente y resultado
de operación, pero aún no define responsables/destinatarios ni existen los flujos de
Inventario y Aprobaciones que emitirían los dos primeros avisos. Ningún cambio de categorías,
seguridad u otro módulo envía automáticamente un aviso. La integración representativa está
en `NotificationIntegrationIT`, no en un caso de uso de producción.

## Contrato entre módulos

`NotificationPublisher.publishAfterCommit(NotificationRequest)` es la interfaz Java
reutilizable. Se llama **dentro** de la transacción del caso de uso dueño. La solicitud
incluye `NotificationType`, módulo, entidad, ID de recurso, clave estable de operación y
un conjunto explícito de IDs de usuario. El módulo dueño decide cuándo ocurre el evento y
quién debe recibirlo; la infraestructura no infiere destinatarios a partir de roles ni
permite publicarlos por HTTP. Antes de conectar un evento real, documentar y probar su regla
de selección. No agregar datos personales o secretos a `operationKey`: esta clave no se
devuelve por la API, pero se conserva en PostgreSQL para deduplicar.

Ejemplo de integración **representativo**, no conectado aún a un flujo real:

```java
@Transactional
void confirmarOperacion(Long recursoId, Long destinatarioId, String claveEstable) {
    // Persistir y validar el recurso en el servicio del módulo dueño.
    notificationPublisher.publishAfterCommit(new NotificationRequest(
        NotificationType.RESULTADO_OPERACION, "MAESTROS", "CATEGORIA_PRODUCTO",
        recursoId, claveEstable, Set.of(destinatarioId)));
}
```

Los tres tipos del enum corresponden a los ejemplos del SRS; sus títulos y cuerpos son
plantillas fijas del servidor. El contenido actual es deliberadamente genérico y apunta al
módulo de origen: no almacena DTO, contraseña, token, correo, importe, salario ni nombre de
cliente. No se debe interpretar la presencia de un tipo como prueba de que su productor
de negocio o regla de destinatarios está implementado.

## Transacción, duplicación y fallo

El publicador exige una transacción activa. Si ésta revierte, no publica nada. Después del
commit llama al escritor APP mediante `REQUIRES_NEW`; la fila solo aparece tras el commit.
Si la inserción falla, registra un aviso técnico sin IDs, claves ni SQL y **no revierte** la
operación ya confirmada. La restricción `UNIQUE(tipo_evento, clave_operacion, usuario_id)` y
`INSERT ... ON CONFLICT DO NOTHING` evitan que reintentos creen filas duplicadas o devuelvan
una notificación ya leída al estado inicial.

Esta versión es de **mejor esfuerzo**: una caída del proceso entre el commit y el callback,
o un fallo de PostgreSQL en el callback, puede perder el aviso. No existe reintento automático,
cola, Outbox ni garantía de entrega eventual. Si el negocio exige recuperación, habrá que
aprobar Outbox persistido con trabajador, control de concurrencia, reintentos y monitoreo.
SMTP también es opcional en el SRS y requeriría proveedor, credenciales, plantillas y política
de errores; no forma parte de esta entrega. SMS está fuera del alcance inicial.

## Esquema y API

V5 crea `notificaciones` con FK obligatoria a `usuarios`, canal fijo `APP`, campos de origen,
marca de lectura, índices de bandeja y restricción de idempotencia. La tabla objetivo previa
del DBML contemplaba `EMAIL`, destinatario externo e intentos; se ajustó para reflejar solo
lo implementado. `NO_LEIDA` y `LEIDA` son estados de lectura, no estados de un proveedor
externo. No hay borrado ni política automática de retención.

`GET /api/v1/notifications` usa la identidad del Bearer y permite paginación de 1 a 100,
orden solo por `creadaEn`/`id` y filtro opcional `leida=true|false`.
`PATCH /api/v1/notifications/{id}/read` marca una fila propia; una ajena o ausente devuelve
el mismo 404 y repetir la solicitud preserva `leidaEn`. Responden con `PageResponse` y
`ApiError`; ver `docs/api/openapi-notifications.yaml`. No hay ruta HTTP de publicación.

## Reproducir las pruebas

Desde `erp-backend/`, con Docker disponible para Testcontainers:

```bash
./mvnw -o verify
```

`NotificationRequestTest` valida el contrato interno y `NotificationOpenApiContractTest`
contrasta rutas y campos de respuesta con OpenAPI. `NotificationIntegrationIT` utiliza
PostgreSQL 17 desechable con Flyway V1–V5 y comprueba confirmación/rollback, dos destinatarios
explícitos, aislamiento HTTP, lectura idempotente, deduplicación, filtros/paginación y fallo
de inserción posterior al commit sin revertir la operación representativa y cuatro
publicaciones concurrentes de la misma clave. Sin artefactos
Maven en caché, quitar `-o` cuando haya acceso al repositorio de dependencias.
