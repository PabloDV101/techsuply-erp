# Sprint 3, parte 4 — dominio y persistencia de Ventas

La parte 4 incorpora mapeos JPA de las siete tablas de V11, consultas y valores deterministas. **Esta parte no habilitó API ni implementó operaciones de cotización, pedido, reserva o despacho; la API de clientes se añadió después en la parte 5.** Complementa las [migraciones](SPRINT_3_MIGRACIONES.md) y el [diseño de Ventas](SPRINT_3_DISENO_VENTAS.md).

## Modelos y persistencia

- `maestros.domain.model.Cliente` mapea el catálogo y permite construir un cliente con código canónico, nombre obligatorio y límite de crédito cero; el plazo inicial de 30 días coincide con el valor por defecto de V11 y no implica crédito disponible. `ClienteRepository` expone consulta, paginación, guardado y bloqueo pesimista. El constructor no sustituye permisos ni validación integral de entrada; esos controles se incorporaron en la parte 5.
- `Cotizacion`, `DetalleCotizacion`, `PedidoVenta`, `DetallePedido`, `Despacho` y `DetalleDespacho` mapean tipos, precisión, nulabilidad y estados de V11. Las referencias entre documentos y hacia otros módulos son identificadores escalares, como en Inventario; las FK de V11 siguen siendo la autoridad. Los repositorios de documentos exponen lectura y consultas explícitas, sin borrado ni guardado: los constructores y operaciones de escritura se incorporarán con los casos de uso y sus reglas. Los bloqueos de cabecera requieren una transacción activa y todavía no prueban el orden de bloqueos de operaciones concurrentes.
- Los enums reflejan exclusivamente los valores actuales de V11: canal `INTERNO`, modalidad `CONTADO` y estados del esquema. Su presencia no implica que existan transiciones implementadas. El costo del detalle de despacho permanece nullable y se mapea con cuatro decimales.
- `MovimientoInventario` ahora mapea y expone en Java `detalleDespachoId`; la consulta HTTP y su OpenAPI todavía no incluyen esa fuente. Antes de confirmar un despacho desde aplicación se deben ampliar y probar esos contratos sin afectar recepción ni ajuste.

## Funciones y reglas

`ValoresVenta.Linea` valida una instantánea comercial inmutable con `BigDecimal`: cantidad positiva de hasta 11 enteros y tres decimales; precio no negativo de hasta 12 enteros y dos decimales; descuento e impuesto entre 0 y 100 con dos decimales. Solo normaliza ceros de escala mediante `UNNECESSARY`; rechaza precisión adicional significativa, no redondea ni calcula importes. Los `record` contienen `BigDecimal` inmutable y estas funciones no leen reloj, repositorio ni estado externo.

`ValoresVenta.pendiente(cantidad, despachada)` calcula la cantidad no despachada y `validarReserva` comprueba que la reserva no supere ese pendiente. No consulta stock ni representa por sí sola una reserva: la transacción y su concurrencia se implementarán en la parte 8. `DetallePedido.pendiente()` y `lineaComercial()` delegan en estos valores. Se mantuvo el cálculo de Compras separado porque aún no se ha aprobado una fórmula monetaria de Ventas; no se creó un Strategy ficticio ni se usó `double`.

La política de vigencia inclusiva, redondeo, autorización de descuentos, vigencia al confirmar y tratamiento de despachos en borrador siguen pendientes. No se implementaron métodos de transición que las supongan. La cancelación parcial acordada y la preservación del precio se aplicarán con sus casos de uso y pruebas transaccionales. El mapeo por sí solo no impide que SQL directo viole todas las reglas comerciales: V11 y la futura aplicación deben actuar conjuntamente.

## Pruebas

`ValoresVentaTest` cubre precisión, máximos, límites de porcentajes, valores inválidos, saldo y reserva, y la creación básica de cliente. `VentasPersistenceIT` crea un cliente mediante JPA y prepara documentos **sintéticos mediante SQL** para comprobar lectura, estados, decimales, consultas por clave, bloqueo y mapeo de la fuente de despacho. El movimiento sintético no es un flujo de confirmación ni modifica stock. La prueba usa PostgreSQL 17 de Testcontainers y `ddl-auto=validate`; no prueba escrituras JPA de documentos, API, seguridad de Ventas, concurrencia ni flujos de reserva/despacho.

La regresión completa `MAVEN_OPTS='-XX:+UseSerialGC' ./mvnw -o verify`, desde `erp-backend/`, pasó con **60 Surefire y 104 Failsafe**, sin fallos, errores ni omitidas. La primera ejecución focalizada falló porque se invocó antes de crear la prueba de integración; después se creó y pasó una repetición focalizada. La regresión completa posterior incluye la versión final del mapeo de la fuente de despacho y su prueba. Se validaron también ambas copias DBML, 42 diagramas PlantUML, enlaces locales y `git diff --check`. Los contratos OpenAPI no cambiaron.
