# Sprint 3 — diseño propuesto de Ventas

**Estado: API de clientes, cotizaciones, pedidos y confirmación con reserva y registro, consulta y confirmación de despachos implementados; cancelaciones con liberación de saldo y facturas comerciales internas implementadas.** Complementa la [ruta por partes](SPRINT_3_VENTAS_POR_PARTES.md), el [SRS](../SRS_TechSupply_Solutions.md), el [DBML objetivo](../../database/modelo.dbml), las [políticas de compras e inventario](SPRINT_1_POLITICAS.md) y el [OpenAPI propuesto](../api/openapi-sprint-3-ventas.yaml). Las secciones distinguen las reglas implementadas de los diseños y decisiones aún pendientes; no agregan requisitos de negocio por sí solas.

## Alcance, decisiones y límites

- **Decisiones del usuario, 10 nov. 2026:** este incremento contempla **solo modalidad de contado** y usa `America/Mexico_City` como zona de negocio para evaluar vigencia por fecha. Contado representa la modalidad comercial; no demuestra cobro ni pago, y no crea cuentas por cobrar. No se habilita ni se da por validado crédito hasta contar con política y fuente real de exposición. RF-VEN-06 permanece pendiente. Un cambio posterior del precio de catálogo no reemplaza el precio acordado de una cotización aceptada. Un pedido parcialmente despachado puede cancelarse por su saldo pendiente, liberando la reserva de ese saldo y conservando los despachos confirmados.
- Flujo prioritario interno: cliente → cotización → aceptación → conversión → confirmación/reserva → despacho parcial o total. La cotización debe estar aceptada y vigente al convertir; el servidor obtiene la fecha de negocio desde un reloj y la zona acordada. El usuario aprobó que `vigenciaHasta` sea inclusiva; vencida significa fecha de negocio posterior. El usuario aprobó rechazar una conversión nueva desde una cotización vencida y requerir una nueva cotización; un reintento equivalente de una conversión ya realizada devuelve el pedido existente.
- La modalidad `CONTADO` se registra en el pedido propuesto. No habilitar `CREDITO` ni interpretar límite de crédito como disponible mientras falten cuentas por cobrar. El contrato no registra pagos, portal de clientes ni ventas directas sin cotización. El usuario aprobó una factura comercial interna, emitida explícitamente por pedido totalmente despachado, sin CFDI ni timbrado SAT. La cuenta y asiento se dejan pendientes para Contabilidad.
- Detalles pendientes: concretar excepciones comerciales futuras. Se acordó rechazar la cancelación de un pedido mientras tenga despachos en borrador y requerir cancelarlos primero. El usuario aprobó la fórmula de redondeo de Compras para las cotizaciones y el permiso de administración de cotizaciones para emitir y aceptar descuentos. El contrato identifica lo que sigue pendiente y no autoriza inventar excepciones. Si una regla imprescindible no se aprueba, la operación dependiente no se implementa como lista para uso.

## Trazabilidad de requisitos

| Requisito | Diseño propuesto | Evidencia requerida |
|---|---|---|
| RF-MAE-04 | Clientes activos, identificadores y datos de contacto; campo de límite conservado sin simular validación crediticia. | CRUD, duplicados, permisos, baja lógica, usuario vinculado y límites declarados de crédito. |
| RF-VEN-01 / RN-04 | Cotización con líneas y condiciones comerciales congeladas, estado y vigencia. | Aceptación, vencimiento, redondeo, cambios de catálogo y auditoría. |
| RF-VEN-02 | Pedido convertido desde cotización aceptada, conservando líneas; una conversión por cotización. | Reintentos iguales/distintos y carreras, sin reserva al crear. |
| RF-VEN-03 / RF-INV-03 / RN-11 | Confirmación con cliente y condiciones válidas y reserva completa; contado únicamente. | CA-02, stock insuficiente y multirenglón sin reserva parcial, cliente inactivo, fallas y reintentos. |
| RF-VEN-04 / RF-INV-04 / RN-12 | Despachos parciales o completos sobre reservas del pedido. | CA-03, estado parcial, salidas con fuente y costo, rollback y reintentos concurrentes. |
| RF-VEN-05 | Factura comercial interna única por pedido completamente despachado. | Emisión explícita, historial, idempotencia y concurrencia; CFDI y Contabilidad fuera del incremento. |
| RN-18/19 / CA-06 | Fuente única, idempotencia y autorización en backend. | Restricciones, auditoría transaccional, denegación de mejor esfuerzo y carreras PostgreSQL. |

## Estados y operaciones propuestos

### Cliente

`ACTIVO`/`INACTIVO` se representa como booleano, sin borrado físico. Crear, consultar, listar, actualizar y cambiar estado están implementados con permisos, validación y auditoría. Código canónico único y nombre obligatorio; datos de contacto son opcionales. `limiteCredito` y `plazoCreditoDias` son datos maestros, no un cálculo de disponibilidad; se exige permiso adicional para modificar el límite, incluido al crear con valor distinto de cero. Una operación nueva exige cliente activo; el historial conserva su referencia.

`usuarios.cliente_id` ya existe como `bigint UNIQUE` sin FK y puede tener datos previos no válidos. No agregar una FK sin consultar primero las referencias existentes y acordar cómo tratar huérfanos o coincidencias accidentales de identificadores; una validación `NOT VALID` por sí sola no confirma que un usuario esté vinculado al cliente correcto. No reasignar ni borrar datos automáticamente. El portal futuro requerirá control de propiedad independiente.

### Cotización

Transición implementada `BORRADOR → VIGENTE → ACEPTADA`; `BORRADOR` o `VIGENTE` pueden cancelarse. Una cotización vigente puede vencerse según la fecha de negocio; el estado persistido no cambia automáticamente por el paso del tiempo. Aceptación y conversión bloquean el encabezado y comprueban vigencia bajo la misma regla. La conversión desde una cotización aceptada que ya venció responde conflicto; se requiere una nueva cotización para una conversión nueva. El vendedor procede de la identidad autenticada al crear; la auditoría conserva quién ejecuta cada transición. La aceptación de este incremento interno la registra un responsable autorizado en nombre del cliente; no demuestra una aceptación en portal ni un mecanismo de firma. Las líneas comerciales no se editan después de aceptarse; la solicitud captura cantidades, precios, porcentajes e impuestos sin alterar stock.

### Pedido y reserva

Estados previstos `BORRADOR → CONFIRMADO → PARCIALMENTE_DESPACHADO → DESPACHADO`; si el primer despacho cubre todo, puede pasar directamente a `DESPACHADO`. Un solo pedido por cotización; creación con clave de idempotencia global normalizada y contenido equivalente retorna el mismo documento. Contenido distinto o una segunda conversión incompatible responde `409`. La conversión conserva el vendedor de la cotización y registra además al actor que crea el pedido. La creación no reserva ni altera kardex. El usuario aprobó exigir vigencia también al confirmar un pedido creado antes del vencimiento.

La confirmación valida estado, cliente activo, modalidad admitida y condiciones comerciales; conserva el precio acordado en la cotización aceptada aunque el catálogo haya cambiado y no sustituye importes silenciosamente. El usuario aprobó respetar los descuentos aceptados sin autorización adicional al permiso de confirmar pedidos; no se habilitan excepciones comerciales. Reserva todas las líneas o ninguna. Bloquear documentos y líneas, luego existencias en orden estable `(producto_id, almacen_id)` acordado con Compras y ajustes; comprobar que ningún proceso con otra secuencia produzca ciclos. Una existencia ausente no proporciona disponibilidad: rechazar sin crear stock; una entrada concurrente puede obligar a reintentar. Si una validación, escritura o auditoría falla, revertir todas las líneas. Incrementar `existencias.version` por cada cambio de reserva y comprobar que los conteos anteriores queden obsoletos.

El usuario aprobó registrar la reserva actualizando el saldo reservado y el detalle del pedido con auditoría, sin crear movimiento de kardex hasta el despacho. La liberación y cancelación se implementarán en la parte 11.

### Despacho

Estados previstos `BORRADOR → CONFIRMADO` o `BORRADOR → CANCELADO`. Registrar es idempotente mediante clave y no modifica stock; la operación de confirmación por id se repite con `200` sin duplicar efectos. El despacho siempre se asocia a un pedido confirmado o parcialmente despachado y al almacén elegido para la reserva. Cada línea referencia exactamente una línea de ese pedido y una cantidad positiva, sin exceder reserva o pendiente. Por decisión del usuario, el registro descuenta las cantidades de otros borradores para evitar asignaciones duplicadas. La cancelación de borradores se implementó en la parte 11.

La confirmación bloquea documento/pedido y existencias en un orden común con las demás operaciones. Por línea, calcula costo desde el promedio vigente de esa existencia bajo bloqueo; persiste el costo `numeric(16,4)` en el detalle antes de insertar la salida `DESPACHO_VENTA` con fuente única `detalle_despacho_id`, manteniendo ambas escrituras en la misma transacción. Reduce por igual física y reservada, incrementa versión, actualiza el pedido, inserta auditoría y confirma todo junto. Los despachos confirmados y movimientos no se editan; no hay reversa ni devolución en esta parte.

La cancelación tras despacho parcial **libera la reserva del saldo pendiente y conserva los despachos confirmados**, según decisión del usuario. La implementación bloquea primero el pedido, rechaza si hay despachos en borrador, libera la reserva atribuible a cada línea, incrementa versiones, registra motivo/actor/fecha y auditoría y pasa el pedido a `CANCELADO`, conservando cantidades despachadas. Repetir con el mismo motivo devuelve el documento sin liberar dos veces; un motivo diferente produce conflicto.

## Cálculos, contratos y seguridad

Compras y cotizaciones usan `BigDecimal`, `HALF_UP` por línea y escala monetaria dos por decisión aprobada. El usuario aprobó esa fórmula para cotizaciones; [se documenta su implementación](SPRINT_3_COTIZACIONES.md). Las cantidades admiten hasta 11 enteros y 3 decimales; precios/importes `numeric(14,2)` y porcentajes 0–100 con dos decimales. Los costos de despacho propuestos usan `numeric(16,4)` para preservar V8. Validar overflow antes de guardar y no usar `double` en backend.

El [OpenAPI de Ventas](../api/openapi-sprint-3-ventas.yaml) usa Bearer opaco y prefijo `/api/v1`, DTO y paginación como los contratos actuales. Las rutas de clientes, cotizaciones, pedidos, despachos y facturas comerciales internas están activas. Identificadores desconocidos responden `404`, validaciones `400`, ausencia de autenticación `401`, permiso insuficiente `403` y conflictos de estado/vigencia/stock/idempotencia `409`. Los errores usan `ApiError` sin detalles internos. Los eventos exitosos se confirman con el negocio; las denegaciones siguen la política existente de mejor esfuerzo, sin secretos.

## Migraciones, compatibilidad y pruebas

1. V11 creó clientes, cotizaciones, pedidos y despachos, y registró la clave de cotización y modalidad en ambas copias DBML. El detalle está en [migraciones](SPRINT_3_MIGRACIONES.md); no se modificaron V1–V10.
2. V11 amplía la función y las restricciones de V9/V10 para fuente de despacho y protege datos fuente; la reserva no genera movimiento de kardex según decisión aprobada. El SQL no reemplaza la aplicación atómica ni la autorización.
3. El costo del detalle de despacho es nullable `numeric(16,4)`; la confirmación lo fija antes de insertar el movimiento. Se conserva la precisión de V8.
4. V11 no agrega FK a `usuarios.cliente_id`: la compatibilidad de datos existentes debe diagnosticarse antes de decidir una futura migración. La prueba de actualización conserva un valor huérfano.
5. Pruebas de dominio para funciones puras; HTTP/autorización y contrato; PostgreSQL con transacciones, bloqueos y Testcontainers: CA-02 (dos reservas concurrentes), CA-03 (despacho parcial), ausencia de stock, límites, múltiples líneas, vencimiento, reintentos, fallas tardías y de auditoría. Repetir las regresiones de recepción y ajuste, y probar que la reserva/despacho incrementa versión y vuelve obsoleto un conteo anterior.
6. La discrepancia inicial de Flyway detectada en parte 0 no tiene causa confirmada: observar y registrar recurrencias, no atribuirla sin evidencia. Verificar en entorno limpio/CI antes de declarar reproducibilidad definitiva. No se modifica el código de preflight en esta fase documental.

## Diagramas y componentes

Las vistas UML de [casos de uso](../diagrams/casos-uso/ventas.puml), [validación de pedidos](../diagrams/casos-uso/ventas-validacion.puml), [clases](../diagrams/clases/03-ventas.puml), [proceso](../diagrams/procesos/04-proceso-ventas-despacho.puml), [secuencia de conversión](../diagrams/arquitectura/09-secuencia-conversion-pedido.puml), [secuencia de registro de despacho](../diagrams/arquitectura/11-secuencia-registro-despacho.puml), [secuencia de confirmación de despacho](../diagrams/arquitectura/06-secuencia-venta-despacho.puml) y [secuencia de cancelación](../diagrams/arquitectura/12-secuencia-cancelacion-ventas.puml) describen el diseño del sistema completo. Incluyen el crédito contemplado por el SRS; este incremento se limita a contado. El estado de implementación se conserva en la ruta del sprint, los contratos y las bitácoras, no en las vistas UML. La cancelación parcial conserva despachos confirmados y libera la reserva del saldo.

`CalculoOrden`, `ValoracionInventario`, `PageRequests` y `PageResponse` son antecedentes útiles, no promesas de portabilidad. La [parte 2](SPRINT_3_COMPONENTES_ESTRATEGIA.md) analizó sus dependencias y eligió candidatos; no introducir Strategy, Factory o abstracciones solo para sumar patrones. Documentar funciones puras, tests y variantes reales en [patrones y programación funcional](../PATRONES_Y_PROGRAMACION_FUNCIONAL.md).
