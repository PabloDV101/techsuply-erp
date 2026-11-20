# Sprint 4 — diseño y políticas de Contabilidad

**Estado: V14–V24, API manual, auxiliares, integraciones operativas y reportes financieros parciales implementados dentro de sus límites.** V22–V23 añaden pagos a proveedores y V24 clasificación auditable para flujo indirecto condicionado. El procesamiento de facturas históricas sigue pendiente. Complementa la [ruta por partes](SPRINT_4_CONTABILIDAD_POR_PARTES.md), el [SRS](../SRS_TechSupply_Solutions.md), el [DBML](../../database/modelo.dbml), las [migraciones](SPRINT_4_MIGRACIONES.md), el [dominio](SPRINT_4_DOMINIO_PERSISTENCIA.md), las [cuentas](SPRINT_4_CUENTAS.md), los [periodos](SPRINT_4_PERIODOS.md) y el [OpenAPI](../api/openapi-sprint-4-contabilidad.yaml). Las decisiones económicas autorizadas se registran abajo; no se infieren otros efectos.

## Decisiones y alcance

Reglas existentes del SRS: RN-16 exige al menos dos partidas, igualdad de debe y haber y fecha dentro de periodo abierto; RN-17 requiere corrección mediante reversión y asiento nuevo, sin editar ni eliminar el asiento contabilizado; RN-18 exige idempotencia, y RN-15/23 regulan aplicaciones y contrapartes. RF-CON-01–07 y CA-04/05 tienen implementación parcial o acotada según la matriz y el cierre del sprint; RF-CON-08 sigue futuro. RN-20–22 exigen autorización financiera y auditoría sin secretos. El plan contempla moneda e importes decimales, sin definir una política cambiaria o fiscal inexistente.

**Decisiones del usuario, 12–16 nov. 2026:**

1. Si falla la contabilización de una factura de venta, conservar la factura y dejar pendiente la contabilización para reintento. No simular un asiento exitoso ni revertir una factura ya emitida. Esto exige persistir y consultar un estado pendiente recuperable; el mecanismo y momento de intento aún se deben diseñar y probar. No se presupone un evento, cola o tarea ya existentes.
2. Las facturas emitidas antes de activar Contabilidad se revisarán y contabilizarán mediante un proceso explícito; no modificar ni contabilizar datos históricos automáticamente. Antes de ejecutarlo deben acordarse selección, fecha, cuentas, conciliación, permisos y tratamiento de diferencias.
3. No reabrir periodos cerrados por ahora. La fecha del contraasiento será explícita y debe pertenecer a cualquier periodo abierto; no se reabre el periodo original.
4. Los borradores podrán editarse o cancelarse con motivo y auditoría antes de cerrar el periodo. Las cuentas con hijas son agrupadoras y no aceptan movimientos.
5. Para facturas nuevas: cargo a Clientes por cobrar por el total, abono a Ventas por subtotal y a Impuestos trasladados por impuestos. No se registra efectivo sin cobro ni costo de ventas en la integración de esta parte. Mapeos explícitos a cuentas activas y distintas; fecha de emisión si está abierta, y fecha explícita abierta si no.
6. La factura y su señal outbox deben persistirse en la misma transacción. El asiento automático se ejecuta por operación autorizada y es idempotente; su fallo deja factura emitida y señal pendiente con contador/código seguro para reintento. No se agregará worker periódico ni procesamiento de facturas históricas en esta parte.
7. Para cobros, el usuario con permiso confirma operativamente el ingreso; V19 no afirma conciliación bancaria ni integración con procesadores. La confirmación debita una cuenta de fondos configurable por medio y acredita anticipos por el total. Cada aplicación posterior, solo al mismo cliente y dentro del saldo CxC y disponible, debita anticipos y acredita Clientes por cobrar. El remanente no aplicado permanece como anticipo. Se requiere fecha contable en periodo abierto; asiento, estado, aplicación y auditoría comparten transacción. Se excluyen reversos, contracargos y depósitos agrupados.

Se preservan las decisiones de Ventas: factura comercial interna por pedido totalmente despachado, emisión explícita, fecha de vencimiento igual a emisión, sin CFDI ni cobro; `CONTADO` es modalidad, no evidencia de pago. Los cobros y las facturas de proveedor validadas existen como fuentes independientes; los pagos a proveedores son una fuente implementada dentro de sus límites; nómina y crédito no son fuentes implementadas. La factura de proveedor origina CxP después de conciliarse, mientras que la recepción solo genera el pasivo transitorio. Presupuestos RF-CON-08 son futuros; no se prometen CFDI ni cumplimiento fiscal.

## Trazabilidad y pruebas de aceptación previstas

| Requisito | Diseño y dependencia | Evidencia requerida al implementar |
|---|---|---|
| RF-CON-01 / RN-01 | Cuentas jerárquicas y periodos abiertos/cerrados; código único; cuentas agrupadoras sin movimientos. | Códigos duplicados, ciclos, padres inválidos, fechas inclusivas, solapamientos, autorización y cierre concurrente. |
| RF-CON-02 / RN-16 | Borrador manual con partidas; contabilización solo con dos o más partidas válidas y debe = haber exactamente. | CA-05, validaciones decimales, cero/negativos, cuenta inactiva o agrupadora, rollback y pruebas unitarias. |
| RF-CON-03 / RN-17/18 | Origen validado, reglas configuradas, contabilización idempotente y contraasiento trazable en periodo abierto. | Reintentos equivalentes y conflictivos, carreras, origen repetido, periodo cerrado, inmutabilidad, reversión única y auditoría. |
| RF-CON-04 | V21 vincula CxP a factura de proveedor validada y contabilizada. | Conciliación exacta, unicidad, importe, vencimiento y proveedor; recepción por sí sola no crea CxP. V22–V23 aplican pagos confirmados. |
| RF-CON-05 | Cuenta por cobrar vinculada a factura comercial válida; creación y tratamiento de históricas según política a concretar. | Unicidad, snapshot, conciliación, cliente, vencimiento, concurrencia y reintentos. |
| RF-CON-06 / RN-15/23 | Cobros separados de factura y aplicaciones parciales al mismo cliente; V22–V23 registran pagos de proveedor aplicados a CxP. | CA-04, no exceder saldo ni disponible, concurrencia, rollback, permisos y auditoría. |
| RF-CON-07 | Mayor, balance de prueba y estados financieros desde asientos válidos, sujetos a clasificación y fórmulas aprobadas. | Ejemplos reconciliables, periodos, exclusión de borradores, contraasientos, permisos y datos insuficientes. |
| RF-CON-08 | Presupuestos futuros, reglas y fuentes no acordadas. | No activar hasta acordar periodo, versiones, responsable, cálculo y controles. |
| RN-20–22 / RNF-07–09 | Autorización por acción, auditoría y transacciones; permisos específicos incorporados con V14–V24. | 401/403, protección de consultas y acciones, auditoría de éxito y denegación conforme al sistema actual, sin secretos, y rollback. |

## Estados y reglas técnicas

**Cuentas:** código único, jerarquía sin ciclos, tipo/naturaleza, estado y bandera de aceptación de movimientos. Con partidas solo se permite editar nombre; no código, tipo, naturaleza, jerarquía ni baja. Las cuentas con hijas son agrupadoras. La API de [cuentas](SPRINT_4_CUENTAS.md) y el servicio de asientos exigen cuenta activa de movimiento. No se deduce un catálogo real ni mapeos a ventas, IVA, inventario o costos.

**Periodos:** `ABIERTO → CERRADO`; límites inclusivos, inicio no posterior a fin y sin solapamientos. El usuario aprobó fechas pasadas o futuras, rechazar cierre con borradores y devolver el periodo ante reintento sin auditar nuevamente. La [API de periodos](SPRINT_4_PERIODOS.md) usa bloqueo de fila y no permite reapertura. V15 hace que contabilización y reversión bloqueen el periodo afectado antes de validar estado y fecha.

**Asientos:** V15 implementa `BORRADOR`, `CONTABILIZADO`, `REVERSADO` y `CANCELADO`. Los borradores se editan o cancelan con motivo y auditoría; editar conserva el periodo. Contabilizar exige dos o más partidas balanceadas, periodo abierto y fecha incluida. Revertir crea un contraasiento único e inmutable con partidas invertidas, motivo, actor, fecha explícita e idempotencia. El original y el contraasiento deben incluirse en mayor y balances aunque el original quede `REVERSADO`. Las operaciones son atómicas; no hay integración automática de fuentes.

**Partidas y precisión:** el usuario aprobó hasta 12 enteros y dos decimales, rechazando precisión adicional. V14 usa `numeric` sin typmod con CHECK de escala y límite; máximo `999999999999.99`. Cada partida debe tener un importe positivo en exactamente un lado. Java y PostgreSQL validan importes; contabilizar comprueba al menos dos partidas y balance exacto. Moneda, conversiones y clasificación fiscal no están acordadas.

**Idempotencia y concurrencia:** contabilización repetida devuelve el asiento existente sin duplicar auditoría; la reversión usa clave y huella de solicitud para retornar el mismo contraasiento ante repetición equivalente y rechazar contenido incompatible. V15 coordina bloqueos del catálogo y periodo con administración y cierre; aún se requiere probar carreras específicas.

## Integración operativa e historia

La emisión escribe la factura, su auditoría y la fila outbox en una transacción; si no puede guardarse la señal durable, la emisión entera revierte para no dejar un documento sin recuperación. Una vez confirmada la emisión, fallos de configuración, periodo o contabilización mantienen factura emitida y outbox pendiente. La API de integración lista, consulta y reintenta con permisos; registra actor, intentos y códigos no sensibles. La publicación del asiento y el cambio a `CONTABILIZADA` son atómicos. No hay polling: un usuario autorizado ejecuta cada intento. La configuración debe existir antes de publicar.

V17 no crea auxiliar de Cuentas por Cobrar: el asiento reconoce un saldo agregado en la cuenta de Clientes por cobrar, y el detalle por cliente/aplicaciones queda para la parte 10. Facturas emitidas antes de activar el outbox se revisan mediante proceso separado, explícito y aún no implementado.

V18 crea el auxiliar inmutable de una factura de venta únicamente dentro del reintento que contabiliza el asiento, en la misma transacción que publica el asiento y completa la outbox. Se valida por unicidad de factura y por coincidencia de cliente, importe y fechas con la factura original. Un trigger requiere además que la outbox apunte al asiento contabilizado con origen `VENTAS/FACTURA_VENTA`; por eso un fallo al crear el auxiliar revierte conjuntamente asiento y estado de integración. Una clave/reintento repetido no genera otro auxiliar.

El saldo pendiente no se duplica en una columna mutable: V19 lo deriva del importe original menos aplicaciones contabilizadas. La cuenta expone ese saldo en consulta. El auxiliar se consulta paginado por cliente o por ID con `CONTABILIDAD_CXC_LEER`; no tiene alta, edición ni cancelación HTTP. V18 no rellena históricos. Facturas emitidas antes de V17 requieren revisión explícita; facturas ya contabilizadas antes de desplegar V18 tampoco se incorporan por un backfill ciego y deberán conciliarse mediante proceso explícito si existen.

En el proceso histórico explícito, primero identificar candidatos y excepciones sin escribir; luego ejecutar únicamente lo autorizado, con trazabilidad, idempotencia y conciliación antes y después. Evitar reprocesar facturas nuevas y antiguas dos veces. No realizar el diagnóstico sobre bases compartidas sin acceso y procedimiento apropiados; no inventar cantidad de documentos existentes.

La parte 12 aprobó y V20 implementa la contabilización de recepción aceptada, despacho confirmado y ajuste aplicado. Los asientos usan costos y cantidades persistidos en movimientos de Inventario, configuración explícita de cinco cuentas y fecha en periodo abierto. No se redondea: toda extensión debe caber exactamente en centavos. Mapeo faltante, periodo cerrado o precisión adicional revierten íntegramente el movimiento y dejan la fuente pendiente/aprobada para reintento. La recepción no crea factura de proveedor ni CxP. No tratar el estado `REVERSADO` como borrado económico.

## Contratos, permisos y errores

El [OpenAPI contable](../api/openapi-sprint-4-contabilidad.yaml) cubre cuentas, periodos, ciclo manual, mayor, integraciones, auxiliares, cobros/pagos y clasificación de flujo; el [contrato de Reportería](../api/openapi-reporteria.yaml) marca cuatro rutas financieras implementadas con límites. Las demás rutas de Reportería siguen como diseño.

V14 registró permisos y los asignó al rol `ADMINISTRADOR` existente. Las rutas manuales verifican permisos por acción. Se usa `/api/v1`, Bearer opaco, DTO y `ApiError {status, code, message}`. La auditoría general acompaña la transacción y la bitácora contable conserva motivos y claves; las denegaciones siguen la política existente.

## Diagrama y migraciones

Las vistas de [clases](../diagrams/clases/04-contabilidad.puml), [proceso](../diagrams/procesos/05-proceso-contabilidad.puml), [casos de uso](../diagrams/casos-uso/contabilidad.puml) y [secuencia](../diagrams/arquitectura/15-secuencia-ciclo-vida-asiento.puml) incluyen el ciclo de vida manual y las integraciones que siguen como diseño. No representan una integración transaccional con la emisión de factura.

V14–V21 incorporan el núcleo, ciclo manual, mayor, balance, integraciones de factura y de Compras/Inventario, auxiliar CxC y cobros/aplicaciones sin modificar V1–V13; V19 usa fondos configurables por medio y anticipos para importes no aplicados. No se consultó una base compartida. Facturas históricas no tienen fila outbox y no se procesan automáticamente. El mayor y balance incluyen original reversado y contraasiento compensatorio. No exportar DBML sobre una base existente.

## Decisiones pendientes antes de sus operaciones

- Plan de cuentas real, naturaleza, mapeos por tipo de transacción y quién los administra; tratamiento del IVA, costo y comisiones.
- Moneda(s), fecha de reconocimiento, clasificación y tratamiento en reportes. La fecha explícita de reversión en periodo abierto ya está acordada.
- Cuentas concretas del plan real y conciliación de fuentes de Inventario con la política financiera; integración de facturas históricas mediante proceso explícito.
- Facturas de proveedor, pagos, estados financieros, clasificación y saldos de apertura. El auxiliar de Ventas refleja solo facturas contabilizadas y su saldo inicial coincide con el importe original.
- Definición del alcance de datos históricos y procedimiento de revisión. No ejecutar cambios masivos ni recontabilizar automáticamente.

## Parte 13 — factura de proveedor y CxP

V21 permite registrar una factura por orden con número único por proveedor, corregirla en estado `REGISTRADA` y validarla tras recepción completa. Se exige correspondencia exacta de líneas, cantidades aceptadas, precios, descuentos, impuestos y costo recibido en el kardex. La validación usa la fecha de negocio `America/Mexico_City` dentro de un periodo abierto y cuentas activas configuradas para IVA acreditable y CxP, junto al mapeo previo de Mercancía recibida no facturada. Debita ese pasivo y el impuesto acreditable; acredita CxP por el total. Factura validada, asiento y auxiliar se confirman juntos. Ante diferencia, mapeo ausente o periodo cerrado, la factura continúa `REGISTRADA`. El auxiliar es inmutable; el saldo actual iguala el original hasta implementar aplicaciones de pago. No hay CFDI, cancelación, devolución, pago ni tratamiento de impuestos no acreditables en este incremento.

## Parte 15 — estados financieros: decisiones iniciales

El usuario autorizó el método indirecto de flujo de efectivo y que se informe `NO_DISPONIBLE` cuando no exista información clasificada fiable. El tipo genérico de cuenta no identifica por sí solo efectivo, equivalentes, partidas no monetarias ni actividades de operación, inversión y financiación; no se deben deducir esas clasificaciones por código o nombre. Se necesita un mecanismo explícito de clasificación y evidencia de su completitud antes de ofrecer cifras de flujo.

Los informes se basan en partidas de asientos contabilizados y reversados, según fecha contable; se excluyen borradores y cancelados. Resultados y balance agregan movimientos por tipo de cuenta y requieren `CONTABILIDAD_REPORTES_LEER`. La parte 15 implementa estos informes HTTP y el flujo indirecto bajo las condiciones y limitaciones siguientes; no completa todo RF-CON-07.

### Clasificación y cálculo del flujo indirecto (parte 15)

V24 y `ClasificacionFlujoService` proporcionan clasificación explícita, temporal y auditable por cuenta, y clasificación por partida para movimientos no monetarios o mixtos. El reporte deriva las actividades de los movimientos clasificados, muestra resultado y ajuste operativo agregado (operación menos resultado), inversión y financiación, y comprueba que el neto de actividades coincide con el cambio de efectivo desde la apertura hasta el corte. Si falta clasificación/cobertura, efectivo, una actividad no tiene contraparte en efectivo, una partida no monetaria no se compensa dentro del asiento o la conciliación falla, la respuesta es `NO_DISPONIBLE` con motivo y sin rubros numéricos. Las cuentas y partidas históricas requieren clasificación manual; no se autocompletan.

V24 permite sustituir clasificaciones mediante anulación con actor y motivo, preservando la historia. El índice de exclusión impide vigencias activas solapadas por cuenta y el índice parcial limita una clasificación activa por partida. Las migraciones y el upgrade V23→V24 se verificaron con PostgreSQL/Testcontainers; no se aplicó la migración a una base compartida.

### Fórmulas implementadas y verificación

El estado de resultados suma abonos menos cargos de INGRESO y resta cargos menos abonos de COSTO y GASTO para el periodo. El balance acumula al fin del periodo ACTIVO en saldo deudor, PASIVO y CAPITAL en saldo acreedor y agrega al patrimonio el saldo neto acumulado de cuentas de resultado; devuelve la diferencia entre activo y pasivo más patrimonio para permitir conciliación. No equivale a clasificación corriente/no corriente ni a un cierre fiscal.

Para flujo, se exigen cuentas identificadas como efectivo con vigencia del periodo, clasificación de todas las cuentas con movimientos hasta el corte y de cada partida no monetaria del periodo. Cada asiento con actividad de operación, inversión o financiación debe contener una partida de efectivo; los componentes marcados no monetarios deben compensarse por asiento. Los movimientos de partidas clasificadas determinan actividades por signo inverso al saldo deudor, y se presenta operación como resultado más ajuste calculado. Se comprueba que la suma de actividades coincida con el cambio entre efectivo inicial y final; si falta cobertura o conciliación se devuelve `NO_DISPONIBLE`. Esta implementación conservadora puede rechazar transacciones válidas que requieran desagregación adicional; no demuestra clasificación financiera o fiscal automática.
