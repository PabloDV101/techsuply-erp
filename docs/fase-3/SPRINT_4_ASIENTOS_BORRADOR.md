# Sprint 4 — asientos manuales en borrador

**Estado:** creación y consulta sobre V14; edición/cancelación con motivo, contabilización y reversión manual con contraasiento sobre V15. La integración automática de fuentes no está habilitada.

## API

| Operación | Ruta | Permiso | Resultado |
|---|---|---|---|
| Crear borrador | `POST /api/v1/accounting/entries` | `CONTABILIDAD_ASIENTOS_REGISTRAR` | 201 y `Location`; repetición equivalente devuelve 200. |
| Listar | `GET /api/v1/accounting/entries` | `CONTABILIDAD_ASIENTOS_LEER` | 200 paginado, máximo 100; orden por `id`, `folio` o `fechaContable`. |
| Consultar | `GET /api/v1/accounting/entries/{id}` | `CONTABILIDAD_ASIENTOS_LEER` | 200 con partidas en orden de línea. |
| Editar borrador | `PUT /api/v1/accounting/entries/{id}/edit` | `CONTABILIDAD_ASIENTOS_REGISTRAR` | 200; requiere motivo, conserva periodo y reemplaza fecha, descripción y partidas. |
| Cancelar borrador | `POST /api/v1/accounting/entries/{id}/cancel` | `CONTABILIDAD_ASIENTOS_REGISTRAR` | 200; motivo, actor e instante quedan registrados; no elimina el asiento. |
| Contabilizar | `POST /api/v1/accounting/entries/{id}/post` | `CONTABILIDAD_ASIENTOS_CONTABILIZAR` | 200; exige 2+ partidas balanceadas exactamente y periodo abierto que incluya la fecha. |
| Revertir | `POST /api/v1/accounting/entries/{id}/reverse` | `CONTABILIDAD_ASIENTOS_REVERTIR` | 200 con contraasiento; requiere fecha y clave de idempotencia. |

El cuerpo de creación requiere `periodoId`, `fechaContable`, descripción, clave de idempotencia y entre 2 y 32,767 partidas. Cada partida refiere una cuenta, contiene debe/haber y descripción opcional. El servidor asigna folio, creador y numeración; origen siempre es `MANUAL`, estado `BORRADOR`. No recibe origen operativo ni estado de asiento.

## Reglas aplicadas

- El periodo debe existir, estar abierto y contener inclusivamente la fecha contable. Un periodo inexistente devuelve 404; cerrado o fecha fuera del rango, 409.
- Cada cuenta debe existir, estar activa y aceptar movimientos. Cuenta ausente devuelve 404; inactiva o agrupadora, 409. La creación participa en el bloqueo de catálogo usado por la administración de cuentas y evita que un cambio concurrente invalide la comprobación.
- Una partida tiene exactamente un lado positivo; importes no negativos de máximo 12 enteros y 2 decimales. Se rechaza precisión adicional sin redondear. La forma `1.000` se rechaza, aunque el dígito adicional sea cero.
- Los borradores pueden quedar desbalanceados: la igualdad entre debe y haber se exige al contabilizar. Un borrador no actualiza saldos definitivos.
- Reusar una clave con contenido equivalente retorna el asiento existente sin nueva auditoría. Una clave con contenido distinto produce 409. El reintento se resuelve antes de revisar si el periodo sigue abierto, para que el resultado original sea recuperable.
- Alta de encabezado, partidas y auditoría forman una transacción. Un error revierte las escrituras de la operación; la secuencia PostgreSQL puede dejar folios sin usar tras rollback.

## Concurrencia y límites

Se toma el bloqueo transaccional por clave de idempotencia, luego el bloqueo cooperativo global de cuentas `pg_advisory_xact_lock(141474, 41004)` y finalmente `SELECT ... FOR UPDATE` sobre el periodo. La secuencia es compatible con el cierre, que solo bloquea la fila del periodo; el cierre espera la creación en curso y después observa su borrador, o la creación posterior observa el periodo cerrado y responde 409. Las altas/ediciones de cuentas usan el mismo bloqueo de catálogo. SQL directo u otros escritores que omitan el protocolo pueden invalidar esas garantías.

La consulta paginada recupera las partidas de la página en lote para evitar una consulta por asiento. No se ha medido rendimiento ni carga. El servicio permite editar/cancelar borradores con motivo para resolverlos antes del cierre. Los asientos contabilizados son inmutables; su corrección crea contraasiento con fecha explícita dentro de cualquier periodo abierto. No se reabren periodos. Las fuentes de Ventas/Compras no generan asientos automáticamente.

V15 añade metadatos e historial. El esquema, ambas copias DBML, OpenAPI, SRS y diagramas deben permanecer alineados.
