# Integración de componentes reutilizables — Fase 2

## Alcance comprobado

La autoridad ejecutable es `erp-backend/src/main`: seguridad, validación,
categorías CRUD/búsqueda, auditoría y bandeja interna APP existen. El único
catálogo de negocio implementado es categorías. `NotificationPublisher` no tiene
productor de negocio en producción; `NotificationIntegrationIT` solo monta una
operación representativa para probar la infraestructura. Los 12 GET del OpenAPI
de reportería son diseño y no tienen controlador, consultas ni fuentes operativas.

La suite usa Spring Boot 4.1.1, PostgreSQL 17 desechable con Testcontainers,
Flyway V1–V5 y Hibernate `ddl-auto=validate`. V1 crea siete tablas de seguridad,
V3 categorías, V4 auditoría y V5 notificaciones; V2 agrega catálogos de seguridad.
El DBML también contiene tablas objetivo que no existen en Flyway y no debe
ejecutarse completo sobre una base actual. No se agregaron migraciones en esta
integración porque no cambia el esquema persistente.

| Origen | Destino | Contrato o interfaz | Prueba | Resultado | Pendiente |
|---|---|---|---|---|---|
| Bearer / Spring Security | Categorías CRUD | `@PreAuthorize`, permisos `MAESTROS_CATEGORIAS_*` | `CategoriaProductoIntegrationIT` | HTTP protegido y permisos comprobados | Definir autorización de llamadas internas a servicios antes de reutilizarlos |
| Seguridad | Errores y validación | `ApiError`, `@Valid`, `WWW-Authenticate` | `AuthIntegrationIT`, `GlobalApiExceptionHandlerTest`, `AuditIntegrationIT.phaseTwoHttpErrorsKeepTheSameShapeAcrossComponents` | Misma forma JSON para 400/401/403; mensajes de auth y cabecera Bearer conservados | Extender pruebas al crear otros módulos |
| CRUD de categorías | Búsqueda | `CategoriaSearchCriteria`, `PageRequests`, `PageResponse` | `CategoriaProductoIntegrationIT` | Filtros combinados, orden permitido y página estable | Segundo consumidor de negocio aún no existe |
| CRUD de categorías | Auditoría | `AuditTrail.change`, misma transacción | `AuditIntegrationIT.successfulWritesCarryActorAndOnlySafeMetadata` y rollback | Actor de Bearer y campos seguros; fallo de auditoría revierte cambio | Catálogo de acciones para módulos futuros |
| Seguridad | Auditoría | `AuditTrail` y adaptador `AuditActorProvider` | `AuthIntegrationIT`, `AuditIntegrationIT` | Login, logout y denegaciones observados | Eventos tras commit son de mejor esfuerzo |
| Manejo HTTP común | Auditoría | `AuthorizationDenialRecorder` implementado por `AuditTrail` | `GlobalApiExceptionHandlerTest.authorizationDenialUsesAuditPortWithoutChangingErrorBody` | El 403 usa el puerto sin dependencia `common → audit` | Vigilar dirección de dependencias al agregar módulos |
| Bearer | Bandeja APP | `AuthenticatedUserProvider`, `PageResponse` | `NotificationIntegrationIT` | Usuario propio, lectura idempotente y aislamiento entre identidades | Retención y otros canales no definidos |
| Operación representativa de prueba | Publicador APP | `NotificationPublisher.publishAfterCommit` | `NotificationIntegrationIT` | Publica tras commit; rollback cancela; reintento deduplica | Ningún productor de producción ni regla de destinatarios |
| Reportería | Compras / inventario / ventas / finanzas / nómina | `openapi-reporteria.yaml` de diseño | Sin prueba ejecutable | No integrada | Fuentes, fórmulas, permisos y pruebas de rendimiento |

## Decisiones de arquitectura y correcciones

1. `common.api` ya no importa `audit.application`. El manejador HTTP solo conoce
   `AuthorizationDenialRecorder`; `AuditTrail` extiende ese puerto y su implementación
   actual registra el evento. El constructor exige el puerto para impedir una
   configuración que omita silenciosamente auditoría de un 403. `audit` sí puede
   usar `common` para paginación y error. Esto elimina el ciclo de paquetes
   observado sin alterar el camino de un 403.
2. Se quitó el record Java `AuthError`, que repetía exactamente los campos de
   `ApiError`. `AuthExceptionHandler` y `AuthErrorWriter` usan ahora `ApiError`.
   No cambian ni el cuerpo JSON ni la cabecera de autenticación. El nombre del
   esquema `AuthError` en OpenAPI se conserva por compatibilidad de clientes.
3. `AuditOpenApiContractTest` cierra el hueco entre `AuditController` y su
   documento. La prueba HTTP transversal verifica el formato de auth, categorías,
   auditoría y notificaciones con identidades reales. La prueba de categorías →
   auditoría ya existente valida el recorrido login → escritura → consulta del evento;
   se reutiliza sin añadir una prueba que repita el mismo flujo.

## Límites para los siguientes módulos

- Los controladores actuales aplican permisos en la API. Antes de usar un servicio
  desde otro módulo se debe definir si la llamada representa a un usuario o una
  operación interna y dónde se exige la autorización. No inferir autorización
  por invocar un método Java protegido solo en su controlador.
- Un productor de notificaciones requiere evento de negocio real, destinatarios
  explícitos y clave idempotente estable. La bandeja actual es de mejor esfuerzo:
  una caída entre commit y callback puede perder el aviso. No se afirma entrega
  durable ni integración de categorías como productor.
- Reportería necesita migraciones y operaciones confirmadas de los módulos fuente,
  fórmulas aprobadas, alcance de filas y medición con datos representativos.
  Las rutas de diseño no se presentan como funcionalidad disponible.
- El esquema solo cubre diez tablas físicas. El `punto_reorden` del DBML y el
  «stock mínimo» del SRS, entre otras fórmulas, requieren decisión de negocio.
- Spring Boot registra en pruebas una contraseña generada para un usuario en memoria
  de autoconfiguración. `SecurityConfig` desactiva Basic y formulario en la cadena
  HTTP actual; conviene revisar esa autoconfiguración antes del despliegue para
  evitar confusión y registros innecesarios.

## Reproducción y evidencia

Desde `erp-backend/`, con Docker disponible y dependencias Maven en caché:

```bash
./mvnw -o verify
```

Verificación posterior a la corrección, 20 de octubre de 2026:
`./mvnw -o verify` terminó en `BUILD SUCCESS`, con 41 pruebas Surefire y
49 Failsafe, sin fallos, errores ni omisiones. Flyway validó V1–V5 y PostgreSQL
17 desechable ejecutó las pruebas de integración. También se comprobaron los
cinco YAML de `docs/api/`, la ausencia de ciclos de importación entre
los paquetes principales y el renderizado de los 42 diagramas PlantUML. La
auditoría previa al cambio había pasado 38 Surefire y 48 Failsafe.
El p95 de la búsqueda de 1500 categorías medido por pruebas secuenciales MockMvc
no demuestra objetivos de carga ni de reportes. No se midió cobertura global,
200 usuarios concurrentes ni RNF-03 (reportes p95 < 5 s).
