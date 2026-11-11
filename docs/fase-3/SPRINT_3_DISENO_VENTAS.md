# Sprint 3 — diseño propuesto de Ventas

**Estado: API de clientes implementada; SQL V11 y mapeos JPA disponibles; transiciones comerciales y API de Ventas siguen siendo diseño.** Complementa la [ruta por partes](SPRINT_3_VENTAS_POR_PARTES.md), el [SRS](../SRS_TechSupply_Solutions.md), el [DBML objetivo](../../database/modelo.dbml), las [políticas de compras e inventario](SPRINT_1_POLITICAS.md) y el [OpenAPI propuesto](../api/openapi-sprint-3-ventas.yaml). Las decisiones técnicas siguientes son propuestas para validar en las partes de implementación; no son requisitos de negocio adicionales ni describen código existente.

## Alcance, decisiones y límites

- **Decisiones del usuario, 10 nov. 2026:** este incremento contempla **solo modalidad de contado** y usa `America/Mexico_City` como zona de negocio para evaluar vigencia por fecha. Contado representa la modalidad comercial; no demuestra cobro ni pago, y no crea cuentas por cobrar. No se habilita ni se da por validado crédito hasta contar con política y fuente real de exposición. RF-VEN-06 permanece pendiente. Un cambio posterior del precio de catálogo no reemplaza el precio acordado de una cotización aceptada. Un pedido parcialmente despachado puede cancelarse por su saldo pendiente, liberando la reserva de ese saldo y conservando los despachos confirmados.
- Flujo prioritario interno: cliente → cotización → aceptación → conversión → confirmación/reserva → despacho parcial o total. La cotización debe estar aceptada y vigente al convertir; el servidor obtiene la fecha de negocio desde un reloj y la zona acordada. Propuesta de interpretación: `vigenciaHasta` es inclusiva; vencida significa fecha de negocio posterior. No se revalidan automáticamente condiciones de una cotización vencida: se rechaza la conversión y se requiere una nueva cotización; confirmar esta política antes de implementar.
- La modalidad `CONTADO` se registra en el pedido propuesto. No habilitar `CREDITO` ni interpretar límite de crédito como disponible mientras falten cuentas por cobrar. El contrato no registra pagos, portal de clientes ni ventas directas sin cotización. La factura comercial del SRS es una historia de prioridad alta, pero está condicionada a reglas y capacidad en la parte 12; no habilita CFDI ni timbrado SAT.
- Detalles pendientes: concretar autorización de descuentos y otras excepciones comerciales, y cómo tratar despachos en borrador al cancelar un pedido. Se propone rechazar la cancelación mientras existan despachos en borrador y requerir que se cancelen primero; esto evita una cancelación implícita. La fórmula de redondeo de Ventas también requiere validación. El contrato identifica lo que sigue pendiente y no autoriza inventar excepciones. Si una regla imprescindible no se aprueba, la operación dependiente no se implementa como lista para uso.

## Trazabilidad de requisitos

| Requisito | Diseño propuesto | Evidencia requerida |
|---|---|---|
| RF-MAE-04 | Clientes activos, identificadores y datos de contacto; campo de límite conservado sin simular validación crediticia. | CRUD, duplicados, permisos, baja lógica, usuario vinculado y límites declarados de crédito. |
| RF-VEN-01 / RN-04 | Cotización con líneas y condiciones comerciales congeladas, estado y vigencia. | Aceptación, vencimiento, redondeo, cambios de catálogo y auditoría. |
| RF-VEN-02 | Pedido convertido desde cotización aceptada, conservando líneas; una conversión por cotización. | Reintentos iguales/distintos y carreras, sin reserva al crear. |
| RF-VEN-03 / RF-INV-03 / RN-11 | Confirmación con cliente y condiciones válidas y reserva completa; contado únicamente. | CA-02, stock insuficiente y multirenglón sin reserva parcial, cliente inactivo, fallas y reintentos. |
| RF-VEN-04 / RF-INV-04 / RN-12 | Despachos parciales o completos sobre reservas del pedido. | CA-03, estado parcial, salidas con fuente y costo, rollback y reintentos concurrentes. |
| RF-VEN-05 | Factura comercial única al completar despacho. | Parte condicionada; se mantiene sin endpoint propuesto hasta aprobar su diseño y capacidad. |
| RN-18/19 / CA-06 | Fuente única, idempotencia y autorización en backend. | Restricciones, auditoría transaccional, denegación de mejor esfuerzo y carreras PostgreSQL. |

## Estados y operaciones propuestos

### Cliente

`ACTIVO`/`INACTIVO` se representa como booleano, sin borrado físico. Crear, consultar, listar, actualizar y cambiar estado están implementados con permisos, validación y auditoría. Código canónico único y nombre obligatorio; datos de contacto son opcionales. `limiteCredito` y `plazoCreditoDias` son datos maestros, no un cálculo de disponibilidad; se exige permiso adicional para modificar el límite, incluido al crear con valor distinto de cero. Una operación nueva exige cliente activo; el historial conserva su referencia.

`usuarios.cliente_id` ya existe como `bigint UNIQUE` sin FK y puede tener datos previos no válidos. No agregar una FK sin consultar primero las referencias existentes y acordar cómo tratar huérfanos o coincidencias accidentales de identificadores; una validación `NOT VALID` por sí sola no confirma que un usuario esté vinculado al cliente correcto. No reasignar ni borrar datos automáticamente. El portal futuro requerirá control de propiedad independiente.

### Cotización

Propuesta de transición `BORRADOR → VIGENTE → ACEPTADA`; `BORRADOR` o `VIGENTE` pueden cancelarse. Una cotización vigente puede vencerse según la fecha de negocio; la representación persistida o calculada de `VENCIDA` debe decidirse sin jobs invisibles. Aceptación y conversión bloquean el encabezado y comprueban vigencia bajo la misma regla. La conversión desde una cotización aceptada que ya venció responde conflicto; la revalidación y las condiciones para reutilizarla están pendientes. Se propone asignar el vendedor desde la identidad autenticada al crear; la auditoría conserva quién ejecuta cada transición. La aceptación de este incremento interno la registra un responsable autorizado en nombre del cliente; no demuestra una aceptación en portal ni un mecanismo de firma. Las líneas comerciales no se editan después de aceptarse; la solicitud captura cantidades, precios, porcentajes e impuestos sin alterar stock.

### Pedido y reserva

Propuesta `BORRADOR → CONFIRMADO → PARCIALMENTE_DESPACHADO → DESPACHADO`; si el primer despacho cubre todo, puede pasar directamente a `DESPACHADO`. Un solo pedido por cotización; creación con clave de idempotencia global normalizada y contenido equivalente retorna el mismo documento. Contenido distinto o una segunda conversión incompatible responde `409`. La conversión conserva el vendedor de la cotización y registra además al actor que crea el pedido. La creación no reserva ni altera kardex. Queda pendiente decidir si se exige que la cotización siga vigente al confirmar un pedido creado antes de su vencimiento; la confirmación dependiente no debe publicarse sin resolverlo.

La confirmación valida estado, cliente activo, modalidad admitida y condiciones comerciales; conserva el precio acordado en la cotización aceptada aunque el catálogo haya cambiado y no sustituye importes silenciosamente. Las restricciones de descuentos y excepciones siguen pendientes. Reserva todas las líneas o ninguna. Bloquear documentos y líneas, luego existencias en orden estable `(producto_id, almacen_id)` acordado con Compras y ajustes; comprobar que ningún proceso con otra secuencia produzca ciclos. Una existencia ausente no proporciona disponibilidad: rechazar sin crear stock; una entrada concurrente puede obligar a reintentar. Si una validación, escritura o auditoría falla, revertir todas las líneas. Incrementar `existencias.version` por cada cambio de reserva y comprobar que los conteos anteriores queden obsoletos.

Propuesta MVP: la reserva y su liberación actualizan el saldo reservado y dejan auditoría y vínculo por detalle de pedido, sin crear un movimiento de kardex hasta confirmar el despacho. Es una decisión técnica de diseño por validar contra el SRS y la expectativa de historial; no ampliar fuentes `RESERVA/LIBERACION` en V10 sin decidir y probar cómo representarlas. Si se aprueba otra política, corregir contrato, DBML y UML antes de migrar.

### Despacho

Propuesta `BORRADOR → CONFIRMADO` o `BORRADOR → CANCELADO`. Registrar es idempotente mediante clave y no modifica stock; la operación de confirmación por id se repite con `200` sin duplicar efectos. El despacho siempre se asocia a un pedido confirmado o parcialmente despachado y al almacén elegido para la reserva. Cada línea referencia exactamente una línea de ese pedido y una cantidad positiva, sin exceder reserva o pendiente.

La confirmación bloquea documento/pedido y existencias en un orden común con las demás operaciones. Por línea, calcula costo desde el promedio vigente de esa existencia bajo bloqueo; persiste el costo `numeric(16,4)` en el detalle antes de insertar la salida `DESPACHO_VENTA` con fuente única `detalle_despacho_id`, manteniendo ambas escrituras en la misma transacción. Reduce por igual física y reservada, incrementa versión, actualiza el pedido, inserta auditoría y confirma todo junto. Los despachos confirmados y movimientos no se editan; no hay reversa ni devolución en esta parte.

La cancelación tras despacho parcial **libera la reserva del saldo pendiente y conserva los despachos confirmados**, según decisión del usuario. Propuesta técnica: bloquear primero el pedido, rechazar mientras tenga despachos en borrador y exigir que estos se cancelen antes; luego liberar exactamente la reserva atribuible a cada línea, incrementar versiones, registrar motivo/actor y auditoría y pasar el pedido a `CANCELADO`, conservando cantidades ya despachadas. Confirmación concurrente y cancelación se serializan. Se propone que repetir una cancelación exitosa devuelva el documento sin liberar dos veces; probar las transiciones y acordar el tratamiento de borradores antes de activar esta API.

## Cálculos, contratos y seguridad

El esquema de Compra usa `BigDecimal`, `HALF_UP` por línea y escala monetaria dos; esto es antecedente, **no aprobación automática de la política de Venta**. Proponer la misma fórmula solo si se confirma que es apropiada para precio, descuento e impuesto de Ventas. Mientras tanto el OpenAPI documenta escalas y rangos, pero no promete el resultado de redondeo. Las cantidades admiten hasta 11 enteros y 3 decimales; precios/importes `numeric(14,2)` y porcentajes 0–100 con dos decimales. Los costos de despacho propuestos usan `numeric(16,4)` para preservar V8. Validar overflow antes de guardar y no usar `double` en backend.

El [OpenAPI de Ventas](../api/openapi-sprint-3-ventas.yaml) usa Bearer opaco y prefijo `/api/v1`, DTO y paginación como los contratos actuales. Los permisos de clientes están activos; los de cotizaciones, pedidos y despachos se incorporaron en V11 pero sus rutas aún no existen y siguen como propuestas. Identificadores desconocidos responden `404`, validaciones `400`, ausencia de autenticación `401`, permiso insuficiente `403` y conflictos de estado/vigencia/stock/idempotencia `409`. Los errores usan `ApiError` sin detalles internos. Los eventos exitosos se confirman con el negocio; las denegaciones siguen la política existente de mejor esfuerzo, sin secretos.

## Migraciones, compatibilidad y pruebas

1. V11 creó clientes, cotizaciones, pedidos y despachos, y registró la clave de cotización y modalidad en ambas copias DBML. El detalle está en [migraciones](SPRINT_3_MIGRACIONES.md); no se modificaron V1–V10.
2. V11 amplía la función y las restricciones de V9/V10 para fuente de despacho y protege datos fuente; reserva/liberación como movimientos sigue pendiente de decisión. El SQL no reemplaza la aplicación atómica ni la autorización.
3. El costo del detalle de despacho es nullable `numeric(16,4)`; la aplicación futura debe fijarlo antes de insertar el movimiento y confirmar. Se conserva la precisión de V8.
4. V11 no agrega FK a `usuarios.cliente_id`: la compatibilidad de datos existentes debe diagnosticarse antes de decidir una futura migración. La prueba de actualización conserva un valor huérfano.
5. Pruebas de dominio para funciones puras; HTTP/autorización y contrato; PostgreSQL con transacciones, bloqueos y Testcontainers: CA-02 (dos reservas concurrentes), CA-03 (despacho parcial), ausencia de stock, límites, múltiples líneas, vencimiento, reintentos, fallas tardías y de auditoría. Repetir las regresiones de recepción y ajuste, y probar que la reserva/despacho incrementa versión y vuelve obsoleto un conteo anterior.
6. La discrepancia inicial de Flyway detectada en parte 0 no tiene causa confirmada: observar y registrar recurrencias, no atribuirla sin evidencia. Verificar en entorno limpio/CI antes de declarar reproducibilidad definitiva. No se modifica el código de preflight en esta fase documental.

## Diagramas y componentes

Las vistas UML de [casos de uso](../diagrams/casos-uso/ventas.puml), [validación de pedidos](../diagrams/casos-uso/ventas-validacion.puml), [clases](../diagrams/clases/03-ventas.puml), [proceso](../diagrams/procesos/04-proceso-ventas-despacho.puml) y [secuencia de despacho](../diagrams/arquitectura/06-secuencia-venta-despacho.puml) describen el diseño del sistema completo. Incluyen el crédito contemplado por el SRS; este incremento se limita a contado. El estado de implementación se conserva en la ruta del sprint, los contratos y las bitácoras, no en las vistas UML. La cancelación parcial conserva despachos confirmados y libera la reserva del saldo.

`CalculoOrden`, `ValoracionInventario`, `PageRequests` y `PageResponse` son antecedentes útiles, no promesas de portabilidad. La [parte 2](SPRINT_3_COMPONENTES_ESTRATEGIA.md) analizó sus dependencias y eligió candidatos; no introducir Strategy, Factory o abstracciones solo para sumar patrones. Documentar funciones puras, tests y variantes reales en [patrones y programación funcional](../PATRONES_Y_PROGRAMACION_FUNCIONAL.md).
