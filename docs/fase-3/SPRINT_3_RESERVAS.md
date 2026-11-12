# Sprint 3, parte 8 — Confirmación y reserva

`POST /api/v1/sales/orders/{id}/confirm` confirma un pedido en borrador y reserva todas sus líneas en el almacén indicado. Requiere `VENTAS_PEDIDOS_CONFIRMAR`; el permiso se comprueba en la API, el servicio de Ventas y el servicio interno de Inventario. La confirmación es idempotente mientras el pedido permanece `CONFIRMADO`: un reintento devuelve el documento existente, sin repetir la reserva o la auditoría.

## Decisiones aprobadas

- La cotización asociada debe seguir aceptada y vigente al confirmar. Su último día es inclusivo según `America/Mexico_City`. Si venció, no se revalida automáticamente: se requiere una nueva cotización y un pedido nuevo. Un reintento de un pedido ya confirmado no vuelve a comprobar vigencia ni catálogos.
- Se respetan los precios, descuentos e impuestos aceptados. La confirmación requiere el permiso indicado y no agrega una autorización especial al descuento. El backend compara la instantánea del pedido con la cotización y recalcula los totales con la misma fórmula de cotizaciones; no sustituye precios desde el catálogo. Solo se admite `CONTADO`, lo cual no acredita el pago.
- La reserva actualiza la cantidad reservada de la existencia y la línea del pedido y registra auditoría, sin generar un movimiento de kardex. El movimiento de salida corresponde al despacho. La liberación y cancelación se implementaron en la parte 11.

## Integridad y concurrencia

Ventas bloquea el pedido y la cotización y comprueba cliente y almacén activos. Inventario valida producto y almacén activos, bloquea cada existencia en orden ascendente de producto y comprueba `física − reservada ≥ cantidad`. Una existencia ausente no se crea y se devuelve conflicto; una entrada concurrente puede hacer posible un reintento. La reserva incrementa `existencias.version` para invalidar fotografías de conteo anteriores.

Cada línea se vincula a su reserva; pedido, existencias, detalles y auditoría se confirman o revierten juntos. Inventario exige una transacción existente y comprueba antes del commit que el pedido se haya confirmado. La confirmación no cambia cantidad física ni costo. Ante stock insuficiente, referencias inactivas, condiciones alteradas o errores de auditoría no se dejan reservas parciales.

## Diseño, contratos y límites

El controlador delega a `PedidoService`; este coordina reglas comerciales, y `InventarioService` realiza los cambios de existencia. `CalculoCotizacion` y `VigenciaCotizacion` mantienen cálculo puro separado de operaciones con efectos. El bloqueo de filas, auditoría e idempotencia son mecanismos de integridad, no programación funcional. El [contrato OpenAPI](../api/openapi-sprint-3-ventas.yaml), el DBML y la [secuencia UML](../diagrams/arquitectura/10-secuencia-confirmacion-reserva.puml) describen esta parte. No se modificaron migraciones existentes ni se añadió una nueva.

Descuentos excepcionales, crédito, cobros y pruebas de rendimiento bajo carga siguen pendientes. El registro y confirmación de despachos se añadieron en las partes 9 y 10. Un pedido confirmado sigue siendo un documento pendiente de despacho, no una constancia de pago o entrega.

## Verificación

El 11 nov. 2026, con Java 21 y PostgreSQL, la regresión completa pasó 67 pruebas Surefire y 133 Failsafe. La ejecución focalizada anterior pasó una prueba de contrato y 11 de integración: cubrió sobreventa, reintentos, reserva de varias líneas, fallas de auditoría, conteos, y carreras con ajustes y recepciones. Después se añadieron dos pruebas sin cambiar comportamiento de producción; ambas pasaron junto con la prueba de contrato: una comprueba el recorrido HTTP desde cotización hasta reserva, y otra que Inventario no pueda dejar una reserva sin confirmar el pedido. El primer intento de la prueba de contrato falló porque aún esperaba que la ruta estuviera en diseño; se actualizó y la repetición pasó.

Para reproducir la regresión desde `erp-backend/`, con las dependencias previamente descargadas:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

Para ejecutar las pruebas de la parte, agregar `-Dtest=PedidoOpenApiContractTest -Dit.test=ConfirmarPedidoIT` al comando. Las pruebas MockMvc y PostgreSQL no sustituyen las de frontend ni una medición de carga.
