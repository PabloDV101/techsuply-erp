# Sprint 2 — migración de inventario

**Estado: V9 implementa el esquema SQL; las entidades JPA y reglas de dominio se añadieron en la parte 3. Servicios/API de conteos y del ciclo de ajustes implementados, incluida la aplicación transaccional.** La fuente ejecutable es [`V9__inventory_counts_adjustments.sql`](../../erp-backend/src/main/resources/db/migration/V9__inventory_counts_adjustments.sql); la política y reglas de aplicación siguen en el [diseño](SPRINT_2_DISENO_INVENTARIO.md). No se modifica ninguna migración V1–V8.

## Cambios y restricciones

- `conteos_inventario`: folio, almacén, creador, fecha, clase y estado; claves foráneas, folio único, estados/clases permitidos e índice por almacén, estado y fecha. Una secuencia genera folios desde la aplicación.
- `detalles_conteo`: producto único por conteo; cantidades físicas y contadas no negativas, diferencia igual a contada menos sistema y versión asociada a la existencia observada. Si no existía fila, se exige cantidad de sistema cero y versión nula. El esquema almacena la referencia, pero no comprueba por sí mismo que siga vigente.
- `ajustes_inventario`: folio y clave de idempotencia únicos, motivo no vacío y FK. El CHECK de estado exige actor, fecha, motivo de rechazo y fecha de aplicación coherentes; prohíbe que solicitante y autorizador sean el mismo usuario. La API implementa registro, aprobación, rechazo y aplicación.
- `detalles_ajuste_inventario`: FK a ajuste/producto y opcionalmente conteo, producto único por ajuste, cantidad positiva y tipo positivo/negativo. El costo `numeric(16,4)` permite representar el rango de V8: positivo exige costo y negativo puede capturarlo al aplicar. La tabla conserva costos; no realiza valoración.
- `movimientos_inventario`: se conserva la FK/única de recepción, que pasa a admitir `NULL`, y se agrega FK única opcional a detalle de ajuste. Un CHECK admite exactamente `ENTRADA/RECEPCION_COMPRA` con fuente recepción o `ENTRADA/SALIDA` de `AJUSTE` con fuente ajuste. No se habilitan todavía fuentes de Ventas, devoluciones ni reversas. Mantiene la clave de operación única y los datos anteriores.
- Dos secuencias y ocho permisos nuevos; se asignan al rol `ADMINISTRADOR` existente sin crear usuarios ni datos operativos. La tabla objetivo `alertas_inventario` no se crea: la alerta prevista es calculada.

La entidad JPA `MovimientoInventario` mapea ahora como opcionales las fuentes de recepción y ajuste, con exclusión protegida por V9. El servicio de inventario crea movimientos de recepción y de ajuste.

El CHECK de fuente no sustituye la validación de que el producto, almacén y sentido del movimiento correspondan al documento fuente. Tampoco garantiza que una solicitud aprobada tenga líneas, que la transición sea legal, que el conteo esté cerrado o vigente, ni que una aplicación y sus movimientos ocurran en la misma transacción. Estas reglas se verifican en los servicios y su transacción; V9 por sí sola no las garantiza. La primera existencia se serializa con la unicidad y el bloqueo compartidos con recepción. V10 añade protección ante DML ordinario, con los límites indicados abajo.

## Compatibilidad y despliegue

`InventoryDocumentsMigrationIT` prueba en un esquema PostgreSQL aislado V1–V8 → V9 con existencias y una recepción/movimiento históricos, y verifica restricciones y permisos. La misma prueba actualiza V9→V10 con datos conservados. El arranque normal de pruebas aplica V1–V10 desde cero con `ddl-auto=validate`. Las pruebas de actualización V5→V6, V6→V7 y V7→V8 conservan sus destinos explícitos. `ConfirmarRecepcionIT` verifica el flujo de compras sobre V10.

V9 altera la tabla de movimientos y sus restricciones; PostgreSQL debe obtener bloqueos de tabla. Planificar ventana y respaldo según el tamaño y entorno antes de aplicarla, sin reinicializar datos ni editar V7/V8. El DBML conserva relaciones futuras como objetivo; ambas copias y el validador se actualizan juntas. La parte 8 no necesitó migración adicional; la parte 9 agrega V10. La parte 10 no requiere migración: usa productos, existencias, almacenes y el permiso creado en V9. El contrato OpenAPI de Inventario marca sus rutas implementadas; el contrato separado de Reportería sigue en diseño.


## V10 — protección de historial

[`V10__protect_inventory_history.sql`](../../erp-backend/src/main/resources/db/migration/V10__protect_inventory_history.sql) agrega funciones y triggers sin modificar tablas ni datos anteriores. Bloquea modificación, borrado y truncado de movimientos; protege documentos de ajuste aplicados y conteos cerrados/cancelados y sus detalles, y comprueba atributos de la fuente de nuevas inserciones. Se persisten los costos de salida antes de marcar el ajuste como aplicado, dentro de la misma transacción.

La actualización V9→V10 conserva existencias y movimientos históricos; no inspecciona ni corrige inconsistencias previas. La instalación de triggers requiere bloqueos de tabla: evaluar ventana y respaldo según el entorno antes del despliegue. El rol de migración debe ser dueño o contar con permisos para crear funciones y triggers; el rol operativo debe ser distinto, sin capacidad de alterar tablas, funciones o configuración de replicación. El mecanismo y sus límites, incluida la ausencia de protección de documentos de compra ante SQL directo, se describen en el [diseño](SPRINT_2_DISENO_INVENTARIO.md#parte-9--historial-e-integridad-implementados). No se definen usuarios ni contraseñas en la migración, y el repositorio no configura todavía una identidad Flyway separada de la conexión operativa: esa separación debe resolverse en el entorno de despliegue.
