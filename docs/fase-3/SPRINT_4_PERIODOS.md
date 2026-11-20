# Sprint 4 — periodos contables

**Estado:** API de creación, consulta y cierre implementada sobre V14; V15 coordina este bloqueo de periodo con edición, cancelación, contabilización y reversión manual. No hay reapertura ni contabilización automática de fuentes. Complementa la [ruta](SPRINT_4_CONTABILIDAD_POR_PARTES.md), el [diseño contable](SPRINT_4_DISENO_CONTABILIDAD.md) y el [OpenAPI](../api/openapi-sprint-4-contabilidad.yaml).

## Decisiones acordadas

El 13 nov. 2026 el usuario aprobó crear periodos pasados, actuales o futuros siempre que no se solapen; no cerrar si quedan asientos en borrador; y devolver el periodo cerrado ante un cierre repetido sin volver a auditar el cambio. La decisión anterior de no reabrir periodos sigue vigente. Los límites de cada rango incluyen ambos días. No se presupone periodicidad mensual, continuidad obligatoria ni cierre automático.

## API y autorización

| Operación | Ruta | Permiso | Resultado |
|---|---|---|---|
| Crear | `POST /api/v1/accounting/periods` | `CONTABILIDAD_PERIODOS_ADMIN` | 201 y `Location`; nace abierto. |
| Listar | `GET /api/v1/accounting/periods` | `CONTABILIDAD_PERIODOS_LEER` | 200, paginación hasta 100, `sort=id/fechaInicio/fechaFin`, dirección `asc/desc`. |
| Consultar | `GET /api/v1/accounting/periods/{id}` | `CONTABILIDAD_PERIODOS_LEER` | 200 con estado y metadatos de cierre, si existen. |
| Cerrar | `POST /api/v1/accounting/periods/{id}/close` | `CONTABILIDAD_PERIODOS_CERRAR` | 200; un reintento conserva actor e instante originales. |

La creación exige `fechaInicio` y `fechaFin` ISO. Inicio posterior al fin es 400; las fechas solapadas o que compartan un día dan 409. Fechas consecutivas sin día compartido son admisibles. Un periodo inexistente da 404; sin autenticación 401, sin permiso 403, según el contrato común `ApiError`. La API no admite borrar, editar fechas ni reabrir.

Para cerrar, el servicio bloquea la fila del periodo, comprueba que no exista asiento `BORRADOR` asociado y registra actor, instante y estado `CERRADO`; un borrador produce 409 y no altera el periodo. Los metadatos de cierre usan `Instant` truncado a microsegundos para coincidir con la precisión de PostgreSQL al recargar y responder un reintento. Cierre y auditoría exitosa son atómicos; un fallo de auditoría revierte el cierre. Las denegaciones siguen la política de mejor esfuerzo existente. El servicio de asientos ahora puede crear y consultar borradores, pero aún no ofrece una operación para resolverlos; un borrador impide cerrar hasta que se acuerde e implemente su resolución.

## Concurrencia y límites

Las altas de periodos realizadas por este servicio se serializan con `pg_advisory_xact_lock(141474, 41005)` y la exclusión GiST de V14 sigue impidiendo solapamientos también ante otros escritores. El bloqueo de altas evita el interbloqueo observado al insertar simultáneamente rangos idénticos a través de la API; no reemplaza la restricción SQL. V14 protege un periodo cerrado contra modificaciones y reapertura por DML ordinario, sujeto a los privilegios del propietario de base de datos. La serialización afecta a todas las altas de periodos; su rendimiento bajo carga no ha sido medido.

El cierre usa `SELECT ... FOR UPDATE`. V15 adquiere ese bloqueo para editar, cancelar, contabilizar o revertir antes de comprobar estado y lo mantiene hasta el commit, junto al protocolo de cuentas. Las pruebas focalizadas cubren el cierre con borradores y el ciclo manual; no se ha agregado una carrera HTTP simultánea entre contabilización y cierre. SQL directo u otros escritores que omitan el protocolo pueden eludir estas garantías.

Esta parte no alteró migraciones; V15 añadió después la coordinación del ciclo de asientos. Las pruebas usan PostgreSQL 17 desechable y no verifican una base compartida. El contrato de periodos y las operaciones manuales relacionadas están `implemented`. No se verificaron frontend, navegador, CORS, carga ni despliegue.
