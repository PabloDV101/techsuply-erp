# Guía propuesta para frontend — Ventas, sprint 3

**Clientes disponible; Ventas en diseño:** `/api/v1/customers` y sus operaciones están implementadas y el [OpenAPI](openapi-sprint-3-ventas.yaml) las marca `implemented`. Cotizaciones, pedidos y despachos siguen sin controladores ni servicios. La guía se corregirá conforme cada parte se implemente y pruebe. Las APIs activas de [Compras](guia-sprint-1.md) e [Inventario](guia-sprint-2-inventario.md) conservan sus contratos.

## Decisiones acordadas

- Este incremento contempla **solo contado**. El campo `modalidad: "CONTADO"` identifica la modalidad y **no indica que haya un pago registrado**. Cobros y crédito no están implementados.
- Las fechas de vigencia se evaluarán en `America/Mexico_City`. Como propuesta técnica, el día `vigenciaHasta` es inclusivo; debe confirmarse antes de implementar esa interpretación.
- El precio pactado en la cotización aceptada se respeta aunque después cambie el catálogo. La fórmula de redondeo y autorización de descuentos aún requieren definición.
- Un pedido parcialmente despachado podrá cancelar el saldo pendiente y liberar su reserva; el despacho confirmado se conserva. El tratamiento de despachos aún en borrador sigue por definir.

## Flujo propuesto

1. Crear y consultar cliente con `/api/v1/customers`; crear con límite distinto de cero o modificarlo exige `MAESTROS_CLIENTES_CREDITO_ADMIN` además de `MAESTROS_CLIENTES_ADMIN`. Consultar exige `MAESTROS_CLIENTES_LEER`. Desactivar no elimina historial.
2. Crear una cotización mediante `POST /api/v1/sales/quotes`, con líneas y clave idempotente; emitirla y aceptarla según estados y vigencia. No modifica stock.
3. Convertir la cotización aceptada y vigente con `POST /api/v1/sales/orders`, indicando almacén, `modalidad` y clave. El pedido nace en borrador y no reserva stock.
4. Confirmar con `POST /api/v1/sales/orders/{id}/confirm`: el backend validará las condiciones y reservará todas las líneas o ninguna. El frontend no debe suponer reserva hasta recibir el resultado confirmado.
5. Registrar un despacho en `/api/v1/sales/dispatches` y confirmarlo con `/api/v1/sales/dispatches/{id}/confirm`. El despacho puede ser parcial; solo confirmar afecta física, reserva y kardex. Reintentar una confirmación exitosa devuelve el documento sin duplicar salidas.
6. La cancelación del saldo pendiente se hará mediante la operación propuesta de pedido; su interacción con despachos en borrador debe definirse antes de publicarla.

Los listados usan `page` desde 0, `size` de 1–100 y `PageResponse`. Las respuestas de error usan `ApiError`: `400` datos inválidos, `401` autenticación, `403` permisos, `404` referencia inexistente y `409` estado, stock, vigencia o idempotencia en conflicto. Consultar el mensaje y estado actualizado ante conflicto; no convertir automáticamente un `409` en éxito.

## Ejemplos de solicitud

El primero corresponde a la API de clientes; los demás son propuestas de Ventas.

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

Las claves son ejemplos, deben ser únicas para cada operación lógica y mantenerse en un reintento equivalente; el backend comparará el contenido. Los valores decimales requieren tratamiento de precisión apropiado en el cliente. El flujo y las reglas de estado se explican en el [diseño del sprint](../fase-3/SPRINT_3_DISENO_VENTAS.md); las rutas de Ventas no se deben integrar como definitivas hasta que estén marcadas como implementadas y probadas.
