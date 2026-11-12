# Guía propuesta para frontend — Ventas, sprint 3

**Clientes, cotizaciones y pedidos disponibles:** `/api/v1/customers`, `/api/v1/sales/quotes` y creación, consulta y confirmación en `/api/v1/sales/orders` están implementados y el [OpenAPI](openapi-sprint-3-ventas.yaml) los marca `implemented`. Registro, consulta, confirmación y cancelación de pedidos y despachos, y emisión y consulta de facturas comerciales internas, están implementados. La guía se corregirá conforme cada parte se implemente y pruebe. Las APIs activas de [Compras](guia-sprint-1.md) e [Inventario](guia-sprint-2-inventario.md) conservan sus contratos.

## Decisiones acordadas

- Este incremento contempla **solo contado**. El campo `modalidad: "CONTADO"` identifica la modalidad y **no indica que haya un pago registrado**. Cobros y crédito no están implementados.
- Las fechas de vigencia se evaluarán en `America/Mexico_City`. El día `vigenciaHasta` es inclusivo.
- El estado de la cotización refleja la última transición y no cambia automáticamente al vencer. `VIGENTE` por sí solo no garantiza vigencia por fecha; emisión y aceptación la comprueban en el backend.
- El precio pactado en la cotización aceptada se respeta aunque después cambie el catálogo. Base e impuesto se redondean por línea a dos decimales con `HALF_UP`; el permiso de administración de cotizaciones autoriza emitir y aceptar descuentos.
- Un pedido parcialmente despachado puede cancelar su saldo pendiente y liberar su reserva; se conserva el despacho confirmado. Antes deben cancelarse sus despachos en borrador.

## Flujo comercial

1. Crear y consultar cliente con `/api/v1/customers`; crear con límite distinto de cero o modificarlo exige `MAESTROS_CLIENTES_CREDITO_ADMIN` además de `MAESTROS_CLIENTES_ADMIN`. Consultar exige `MAESTROS_CLIENTES_LEER`. Desactivar no elimina historial.
2. Crear una cotización mediante `POST /api/v1/sales/quotes`, con líneas y clave idempotente; emitirla y aceptarla según estados y vigencia. Un reintento equivalente del mismo vendedor devuelve `200`. No modifica stock.
3. Convertir la cotización aceptada y vigente con `POST /api/v1/sales/orders`, indicando almacén, `modalidad` y clave. El pedido nace en borrador y no reserva stock.
4. Confirmar con `POST /api/v1/sales/orders/{id}/confirm`: el backend valida las condiciones y reserva todas las líneas o ninguna. El frontend no debe suponer reserva hasta recibir el resultado confirmado.
5. Registrar un despacho en `/api/v1/sales/dispatches`. Cada línea debe caber en la reserva descontando otros borradores. La confirmación `/api/v1/sales/dispatches/{id}/confirm` afecta física, reserva y kardex de forma atómica. Repetir una confirmación exitosa devuelve el documento sin duplicar salidas.
6. Para cancelar el saldo, primero cancelar cada borrador con `POST /api/v1/sales/dispatches/{id}/cancel` y después llamar `POST /api/v1/sales/orders/{id}/cancel` con `{"motivo":"..."}`. Un pedido totalmente despachado no se cancela.
7. Para un pedido totalmente despachado, emitir la factura interna con `POST /api/v1/sales/invoices` y una clave idempotente. Es un documento comercial, no acredita pago ni constituye CFDI, cuenta por cobrar o asiento contable.

Los listados usan `page` desde 0, `size` de 1–100 y `PageResponse`. Las respuestas de error usan `ApiError`: `400` datos inválidos, `401` autenticación, `403` permisos, `404` referencia inexistente y `409` estado, stock, vigencia o idempotencia en conflicto. Consultar el mensaje y estado actualizado ante conflicto; no convertir automáticamente un `409` en éxito.

## Ejemplos de solicitud

Los ejemplos corresponden a APIs disponibles de clientes, cotizaciones, creación de pedidos, registro de despachos y emisión de facturas internas.

```json
{"codigo":"CLI-001","nombreRazonSocial":"Cliente ejemplo","limiteCredito":0,"plazoCreditoDias":0}
```

```json
{"clienteId":1,"vigenciaHasta":"2026-11-30","claveIdempotencia":"cot-2026-001","detalles":[{"productoId":10,"cantidad":3,"precioUnitario":10,"descuentoPct":0,"tasaImpuesto":16}]}
```

```json
{"cotizacionId":1,"almacenId":1,"modalidad":"CONTADO","claveIdempotencia":"ped-2026-001"}
```

```json
{"pedidoId":1,"claveIdempotencia":"des-2026-001","detalles":[{"detallePedidoId":1,"cantidad":2}]}
```

```json
{"pedidoId":1,"claveIdempotencia":"fact-2026-001"}
```

Las claves son ejemplos, deben ser únicas para cada operación lógica y mantenerse en un reintento equivalente; el backend comparará el contenido. Los valores decimales requieren tratamiento de precisión apropiado en el cliente. El flujo y las reglas de estado se explican en el [diseño del sprint](../fase-3/SPRINT_3_DISENO_VENTAS.md); las cancelaciones y sus límites se explican en [cancelaciones](../fase-3/SPRINT_3_CANCELACIONES.md).

La respuesta de pedido incorpora `motivoCancelacion`, `canceladoPorUsuarioId` y `canceladoEn`, nulos si no hay metadatos. Los consumidores con validación estricta deben actualizar su esquema; las cancelaciones históricas pueden carecer de ellos.
