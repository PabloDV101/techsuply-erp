# Componente de auditoría — Fase 2

Estado: implementado para autenticación, administración de seguridad y categorías de producto.
Compras, inventario, ventas, contabilidad y RR. HH. siguen pendientes; sus eventos se añadirán
al catálogo al implementar cada operación aprobada. La existencia de `AuditTrail` no acredita
la auditoría de un flujo que todavía no existe.

## Decisión y límites

- V4 crea `eventos_auditoria`, la FK opcional a `usuarios`, índices de fecha/actor/recurso,
  CHECK para actor/resultado y un trigger PostgreSQL que rechaza UPDATE y DELETE. El rol
  `ADMINISTRADOR` recibe `SEGURIDAD_AUDITORIA_LEER`; ningún usuario inicial se crea por V4.
- Los cambios exitosos se insertan mediante `AuditTrail.change(...)` en la transacción
  del servicio (`Propagation.MANDATORY`). Si el evento falla, la operación se revierte;
  si la operación revierte, también revierte el evento. Login exitoso y bootstrap siguen
  esta misma regla.
- Login rechazado se anota después del commit de los intentos fallidos, mediante una
  transacción independiente y de mejor esfuerzo. Logout se anota después del commit de
  la revocación. Las respuestas 403 de controladores/filtro también intentan insertar un
  evento independiente. Una caída de PostgreSQL puede impedir estos eventos de rechazo;
  jamás debe deshacer un logout ya confirmado ni transformar el rechazo en éxito.
- `AuditActorProvider` es el puerto de identidad. Su adaptador consulta
  `AuthenticatedUserProvider`; los módulos no reciben ni almacenan el Bearer. Para cambios
  protegidos, un actor ausente es error. El bootstrap usa `SISTEMA`; el login rechazado usa
  `ANONIMO`; un 403 conserva el ID del actor solo si está autenticado.
- El detalle es una lista cerrada de **nombres de campos modificados** y etiquetas
  `ACTIVO`/`INACTIVO`. No guarda valores previos/nuevos, contraseña, hash, token, nombre de
  usuario intentado, IP ni cuerpo de solicitud. Los IDs de recurso son los ya públicos del
  dominio. `motivo_codigo` admite los códigos constantes `CREDENTIALS_REJECTED` y
  `PERMISSION_DENIED` para rechazos.
- La tabla es de solo inserción para rutas ordinarias y la API solo ofrece GET. La política
  de retención y cualquier proceso de purga requieren aprobación institucional y un
  procedimiento separado; **no hay eliminación automática**. No se afirma protección
  frente a un superusuario de PostgreSQL ni frente a acceso físico a la base.

## Publicar un evento desde otro módulo

Inyectar `AuditTrail` en un servicio de aplicación `@Transactional`. Tras guardar el recurso
y obtener su ID, invocar `change` con una acción de `AuditAction` aprobada y un conjunto de
`AuditField`; no pasar DTO, mapas arbitrarios ni valores sensibles. Ejemplo actual:

```java
CategoriaProducto guardada = repository.saveAndFlush(categoria);
auditTrail.change(AuditAction.CATEGORY_CREATED, guardada.getId());
```

Una nueva acción necesita revisar el SRS, el catálogo `AuditAction`, los campos permitidos,
el límite transaccional y una prueba PostgreSQL. No registrar éxito antes de confirmar que el
recurso cumple sus invariantes. Para resultados fallidos de negocio aún no existe una API
general: evaluar si el requisito pide registrar el intento y cómo persistirlo tras rollback.

## Consulta y pruebas

`GET /api/v1/audit/events` exige `SEGURIDAD_AUDITORIA_LEER`, admite filtros combinados,
paginación de 1 a 100 y orden solo por `fecha` o `id`. Responde con `PageResponse` y
`ApiError`; contrato completo en `docs/api/openapi-audit.yaml`. No expone `detalle` JSONB
sin procesar. Ejemplo: `GET /api/v1/audit/events?modulo=SEGURIDAD&resultado=DENEGADO&size=20`.

Desde `erp-backend/`, con Docker disponible para Testcontainers:

```bash
./mvnw -o verify
```

`AuditIntegrationIT` comprueba eventos de éxito/denegación, actor, consulta protegida,
metadatos seguros, rollback y prohibición de UPDATE/DELETE contra PostgreSQL real aislado.
La suite anterior de autenticación y categorías se ejecuta en el mismo comando. En un entorno
sin artefactos Maven en caché, omitir `-o` con acceso a repositorios configurado.
