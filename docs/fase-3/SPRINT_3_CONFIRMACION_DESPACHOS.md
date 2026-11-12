# Sprint 3, parte 10 — Confirmación de despacho

`POST /api/v1/sales/dispatches/{id}/confirm` confirma un despacho en borrador de un pedido confirmado o parcialmente despachado. Exige `VENTAS_DESPACHOS_CONFIRMAR` en el controlador, el servicio de Ventas y el servicio interno de Inventario. Un reintento de un despacho ya confirmado devuelve `200` sin repetir sus efectos.

## Integridad y valoración

La operación bloquea primero el pedido y después el despacho. Para cada línea, en orden ascendente de producto, Inventario comprueba producto y almacén activos, correspondencia de pedido y almacén, reserva propia, saldo pendiente y disponibilidad de la existencia bajo bloqueo. Una referencia inválida, existencia insuficiente, versión agotada o fallo de auditoría revierte la transacción entera.

El costo unitario de salida se fija al costo promedio vigente de la existencia bajo el mismo bloqueo, con precisión `numeric(16,4)`. Por línea se reduce en igual cantidad la existencia física y reservada, se incrementa su versión, se actualizan las cantidades reservada y despachada del pedido y se inserta una salida `DESPACHO_VENTA` con `detalle_despacho_id` único. El detalle conserva el costo aplicado aun si el promedio cambia después. El pedido pasa a `PARCIALMENTE_DESPACHADO` mientras quede alguna línea pendiente, y a `DESPACHADO` cuando todas se hayan completado. Despacho, pedido, existencias, movimientos y auditoría se confirman o revierten juntos.

V11 exige que el detalle tenga costo antes de insertar el movimiento y que el despacho siga en borrador durante esa inserción. Inventario requiere una transacción existente y verifica antes del commit que se hayan actualizado el despacho y pedido. Los reintentos y las confirmaciones concurrentes se serializan mediante los bloqueos; no hay eliminación de movimientos ni reversa en este incremento. La cancelación del despacho en borrador y la liberación del saldo del pedido se implementaron en la parte 11.

## Contrato y compatibilidad

El [OpenAPI de Ventas](../api/openapi-sprint-3-ventas.yaml) marca activa la confirmación. Se amplió la respuesta de `GET /api/v1/inventory/movements` con el campo nullable `detalleDespachoId` y el motivo `DESPACHO_VENTA`; la versión del documento cambia de `0.1.0` a `0.2.0` y los consumidores que validan estrictamente la respuesta deben actualizarse al [contrato de Inventario](../api/openapi-sprint-2-inventario.yaml). La [secuencia UML](../diagrams/arquitectura/06-secuencia-venta-despacho.puml) muestra el flujo; en esta parte ambas copias del DBML describían la estructura V11 y no se añadió migración. La parte 11 agregó V12 para cancelaciones.

Ventas coordina la transacción y usa repositorios; Inventario es responsable de modificar existencias y generar el kardex. Los bloqueos, auditoría y escritura son efectos controlados, no programación funcional. Las pruebas y la modularidad actuales no acreditan portabilidad o sustitución del componente en otro proyecto.

## Verificación

El 11 nov. 2026, con Java 21 y PostgreSQL, la regresión completa pasó 68 pruebas Surefire y 154 Failsafe. La ejecución focalizada anterior pasó dos pruebas de contrato y 11 de integración: despacho parcial/completo, reintentos, carreras entre despachos y con recepción/ajuste, conteo obsoleto, permisos, errores y rollback, y recorrido HTTP hasta kardex. Después se ampliaron pruebas sin cambiar código de producción; una ejecución focalizada pasó una prueba de contrato y tres de integración, incluyendo CA-03 con cuatro de diez unidades, costo máximo de cuatro decimales y fuentes anteriores de kardex.

Para reproducir la regresión desde `erp-backend/`, con las dependencias previamente descargadas:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

Para las pruebas de la parte, agregar `-Dtest=DespachoOpenApiContractTest,MovimientoOpenApiContractTest -Dit.test=ConfirmarDespachoIT,MovimientoConsultaIT` al comando. MockMvc y PostgreSQL no demuestran integración de frontend, red ni rendimiento bajo carga.
