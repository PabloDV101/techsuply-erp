# Sprint 4 — plan de cuentas

**Estado:** API de cuentas implementada. V15 añade el ciclo manual de asientos; la integración operativa, los auxiliares y reportes siguen pendientes. Complementa la [ruta del sprint](SPRINT_4_CONTABILIDAD_POR_PARTES.md), el [diseño contable](SPRINT_4_DISENO_CONTABILIDAD.md) y el [contrato OpenAPI](../api/openapi-sprint-4-contabilidad.yaml). No se crea ni prescribe un catálogo real o mapeo contable por defecto.

## Contrato y autorización

El prefijo es `/api/v1/accounting/accounts`; el mismo permiso se comprueba en controlador y servicio. Se usan Bearer opaco, DTO, Bean Validation y el formato `ApiError` compartido.

| Operación | Ruta | Permiso | Resultado |
|---|---|---|---|
| Crear | `POST /accounting/accounts` | `CONTABILIDAD_CUENTAS_ADMIN` | 201 y `Location`; comienza activa. |
| Consultar | `GET /accounting/accounts/{id}` | `CONTABILIDAD_CUENTAS_LEER` | 200, incluye estado y padre. |
| Listar | `GET /accounting/accounts` | `CONTABILIDAD_CUENTAS_LEER` | 200 paginado; incluye inactivas; `page`, `size` hasta 100, `sort=id/codigo/nombre`, `direction=asc/desc`. |
| Editar | `PUT /accounting/accounts/{id}` | `CONTABILIDAD_CUENTAS_ADMIN` | 200 con los datos actuales. |
| Estado | `PATCH /accounting/accounts/{id}/status` | `CONTABILIDAD_CUENTAS_ADMIN` | 200 con `{ "activa": true/false }`; repetir el mismo estado no genera otro evento de cambio. |

Cuerpo de creación/edición: `codigo`, `nombre`, `tipo`, `naturaleza`, `cuentaPadreId` opcional o nulo y `aceptaMovimientos`. El backend recorta texto, normaliza nuevos códigos a mayúsculas y deriva `nivel`: raíz 1, hija nivel de padre + 1. Al editar, conserva la grafía de un código preexistente si solo cambia el uso de mayúsculas, para permitir renombrar cuentas históricas de V14 sin alterar su código. Tipos y naturalezas admitidos se especifican en OpenAPI. La ausencia de autenticación devuelve 401, la falta de permiso 403, entrada inválida 400, referencia inexistente 404 y conflictos 409, sin exponer SQL ni valores internos. Denegaciones usan la política de auditoría existente de mejor esfuerzo.

## Reglas aprobadas y decisiones de implementación

El usuario acordó el 13 nov. 2026 que una cuenta con partidas permite cambiar nombre, pero no código, tipo, naturaleza, jerarquía ni baja. La comprobación incluye partidas de borradores: una referencia ya existente no se altera ni se elimina. También se bloquea el cambio de `aceptaMovimientos` si hay partidas, para preservar la interpretación de la cuenta. Reactivar una cuenta no es una baja.

Las cuentas con hijas deben ser agrupadoras y no aceptar movimientos. Una cuenta con partidas no puede convertirse en padre. Para crear o reubicar una hija, el padre debe estar activo, ser agrupador y no tener partidas. Una cuenta con hijas no puede reubicarse en esta parte: así no se alteran silenciosamente niveles de descendientes ni se requiere una actualización recursiva. Se permiten cambios de nombre de una hija aunque su padre se haya desactivado posteriormente; no se cambian automáticamente los estados de descendientes. Cada modificación se valida antes de persistir; se bloquean ciclos directos e indirectos, padres ausentes y desbordamiento de nivel. La unicidad de código de la API es sin distinción de mayúsculas; V14 por sí sola conserva unicidad sensible a mayúsculas.

Si una base preexistente contiene dos códigos que solo difieren en mayúsculas, la API detectará el conflicto en una edición y no los modificará automáticamente. La presencia de tales datos no se ha comprobado en ninguna base compartida; su diagnóstico y resolución requieren un procedimiento explícito.

## Transacciones, auditoría y límites

Todas las escrituras de cuentas adquieren el bloqueo transaccional PostgreSQL `pg_advisory_xact_lock(141474, 41004)` antes de consultar la jerarquía o partidas; las actualizaciones también bloquean la fila de cuenta. Esto serializa las escrituras realizadas por este servicio, incluso entre instancias que comparten base, y permite rechazar cambios recíprocos que crearían ciclos. Las lecturas se mantienen paginadas sin adquirir ese bloqueo. El costo de serialización aún no se ha medido bajo carga.

**El bloqueo es un protocolo cooperativo:** SQL directo u otros escritores que no lo adquieran pueden omitirlo. V15 valida en el trigger de partidas que la cuenta esté activa y acepte movimientos, además de exigir asiento borrador y periodo abierto. La API del servicio también adquiere el bloqueo de catálogo y periodo. Propietarios de la base pueden alterar restricciones; no se garantiza seguridad ante SQL directo con privilegios de escritura que omita el protocolo.

Altas, ediciones y cambios de estado registran acción, entidad, actor y nombres de campos afectados sin sus valores. La auditoría exitosa forma parte de la misma transacción: su fallo revierte el cambio. No hay borrado físico por API. La administración de cuentas conserva V14; V15 extiende los controles de partidas y asiento.

## Integración con frontend

Consultar los esquemas `CuentaCreate`, `Cuenta`, `CuentaEstado` y `CuentaPage` en OpenAPI. Las rutas de cuentas y periodos, junto con creación y consulta de borradores de asientos, están `implemented`; contabilización y reversión continúan `design-only`. No interpretar una cuenta activa o la bandera `aceptaMovimientos` como prueba de que el ERP puede contabilizar asientos. No hay pruebas de navegador, CORS ni integración real con React en esta parte.
