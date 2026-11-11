# Sprint 3, parte 3 — migración de Ventas

`V11__sales_schema.sql` incorpora almacenamiento, permisos y validaciones SQL; **la migración por sí sola no implementa servicios ni API de clientes o Ventas**. Al cierre de la migración los contratos HTTP seguían siendo propuestas; la parte 5 implementó la API de clientes. La parte 4 incorporó mapeos JPA y validación de valores, sin implementar los casos de uso; las reglas de vencimiento, redondeo, autorización de descuentos y cancelación de borradores siguen abiertas según el [diseño](SPRINT_3_DISENO_VENTAS.md).

## Tablas y decisiones

| Tabla | Uso e integridad en V11 |
|---|---|
| `clientes` | Código canónico y único, datos de contacto, límite no negativo y estado; el límite no habilita crédito ni demuestra saldo. |
| `cotizaciones`, `detalles_cotizacion` | Cliente, vendedor, folio y clave únicos; vigencia, totales y líneas con FK a producto, cantidad positiva y porcentajes acotados. Las reglas de aceptación y cálculo aún no están implementadas. |
| `pedidos_venta`, `detalles_pedido` | Una cotización por pedido, almacén, vendedor y creador; canal `INTERNO`, modalidad `CONTADO`, idempotencia y líneas. Reservada más despachada no puede superar la cantidad. El estado usa `varchar(25)` porque `PARCIALMENTE_DESPACHADO` supera 20 caracteres. |
| `despachos`, `detalles_despacho` | Despacho por pedido y almacén, clave única y líneas únicas; costo `numeric(16,4)` nullable en borrador para fijarlo al confirmar. Una salida exige costo y atributos coincidentes. |

Se crearon tres secuencias de folios e índices para consultas previstas. Se añadieron 13 permisos propuestos al catálogo y al administrador, siguiendo V6/V9; su presencia no activa endpoints. No se crean factura, cobros ni cuentas por cobrar y no se hace ninguna modificación a `usuarios.cliente_id`: la referencia opcional existente sigue sin FK. Antes de añadir una FK, diagnosticar referencias existentes y su correspondencia real; no basta con que los números coincidan.

La migración agrega `movimientos_inventario.detalle_despacho_id` con FK y unicidad, extiende el `CHECK` de V9 y reemplaza la función usada por el trigger de V10 mediante `CREATE OR REPLACE`, manteniendo la validación previa de recepciones y ajustes. Para salida exige fuente, tipo, producto, almacén, línea del pedido, cantidad y costo coincidentes, y despacho en borrador. Los movimientos siguen protegidos por V10; V11 impide modificar detalles fuente que ya tienen movimiento y despachos confirmados, y protege las referencias esenciales del pedido. El dueño o administrador puede alterar estos controles.

Estas restricciones **no sustituyen la aplicación transaccional**: SQL no demuestra que el pedido tenga una cotización vigente, que sus líneas sean copias correctas, que una reserva pertenezca al pedido ni que stock, estado, movimiento y auditoría cambien juntos. Tampoco impone que todo despacho confirmado tenga líneas y movimientos. Los bloqueos y concurrencia de las operaciones nuevas deben probarse al implementarlas, incluidos posibles conflictos de orden entre bloqueo de padres y detalles. La cantidad de movimientos no cambia existencias automáticamente. La entidad de kardex mapea `detalle_despacho_id` desde la parte 4; la consulta HTTP actual y su contrato aún no lo exponen; antes de habilitar la confirmación de Ventas habrá que extender y probar esa lectura y OpenAPI sin romper las fuentes existentes.

## Actualización segura

No se modificaron V1–V10. Antes de aplicar V11 en una base existente, comprobar versión e historial Flyway y preparar respaldo y ventana adecuados: `ALTER TABLE`, validación de restricciones, creación de índices y sustitución de función pueden esperar bloqueos y afectar escrituras. Probar el tiempo con datos representativos. La prueba V10→V11 se ejecuta en un esquema desechable con movimientos de ambas fuentes y una referencia de cliente huérfana, preservados tras migrar; no inspecciona bases compartidas ni mide tiempos reales de despliegue. Si falla una migración, investigar el estado de Flyway y la transacción de PostgreSQL antes de actuar; no ejecutar `clean`, reparar checksums ni borrar datos automáticamente.

Las dos copias DBML muestran los campos y estado SQL V11; el validador compara nombres, tipos, nulabilidad, claves y referencias, pero no demuestra semántica de `CHECK`, triggers ni rendimiento. El diagrama de clases incorpora modalidad, idempotencia y costo nullable, distinguiendo SQL de implementación Java; la secuencia de despacho sigue siendo objetivo.

## Evidencia de validación

En Java 21 y PostgreSQL 17 de Testcontainers, `MAVEN_OPTS='-XX:+UseSerialGC' ./mvnw -o verify` pasó con **57 pruebas Surefire y 103 Failsafe**, sin fallos ni omitidas, con arranque y `ddl-auto=validate` para las entidades actuales. Después se ampliaron las aserciones de la prueba de migración y `MAVEN_OPTS='-XX:+UseSerialGC' ./mvnw -o -Dtest=CalculoOrdenTest -Dit.test=SalesMigrationIT verify` pasó con 3 Surefire y 2 Failsafe. La suite completa se ejecutó antes de esa ampliación de aserciones; la migración no cambió después. En ese momento aún no había entidades JPA de Ventas; su validación se registra en la parte 4.

Se validaron ambas copias DBML con `docs/diagrams/validar-modelo.cjs`, los ocho contratos OpenAPI y sus ejemplos propuestos, los 42 PlantUML y `git diff --check`. Los comandos de herramientas temporales figuran en los README de [diagramas](../diagrams/README.md) y [API](../api/README.md). Las pruebas SQL verifican restricciones y protección de fuentes; no prueban operaciones concurrentes de Ventas ni la carga de una base compartida.
