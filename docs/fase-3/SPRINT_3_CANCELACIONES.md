# Sprint 3, parte 11 — Cancelación y recuperación

La parte 11 incorpora cancelación de despachos en borrador y de pedidos en borrador, confirmados o parcialmente despachados. No implementa devolución ni reversa de despachos confirmados, facturación o cobros.

## Reglas acordadas

- Para cancelar un pedido con despachos en borrador, primero hay que cancelar cada borrador. El backend responde `409` mientras quede alguno. No cancela despachos implícitamente.
- Un pedido en borrador puede cancelarse con motivo y sin alterar Inventario. Un pedido confirmado o parcialmente despachado libera únicamente su reserva pendiente por línea. Se conservan las cantidades despachadas y todos los despachos y movimientos confirmados. Un pedido completamente despachado no puede cancelarse con esta operación.
- La cancelación del despacho en borrador libera su asignación para otros borradores, pero no libera la reserva del pedido ni genera movimiento de kardex. Un despacho confirmado no se puede cancelar con esta operación.
- `POST /api/v1/sales/orders/{id}/cancel` requiere `VENTAS_PEDIDOS_CANCELAR` y un `motivo` no vacío de hasta 1.000 caracteres. Se guarda recortado con el actor y la fecha de la primera cancelación. Reintentar con el mismo motivo devuelve `200` sin repetir efectos, incluso si el actor autorizado es otro; un motivo distinto devuelve `409`. Una cancelación histórica sin metadatos también devuelve `409`, pues no se puede comprobar la equivalencia.
- `POST /api/v1/sales/dispatches/{id}/cancel` requiere `VENTAS_DESPACHOS_REGISTRAR`; repetir la cancelación devuelve `200`. Solo se cancela un borrador; un despacho confirmado devuelve `409`.

## Integridad, concurrencia y migración

La cancelación bloquea el pedido antes que el despacho y recorre las líneas por producto en orden estable. Inventario comprueba que la reserva atribuida corresponda al saldo pendiente, bloquea la existencia, disminuye únicamente la cantidad reservada e incrementa su versión. Puede liberar una reserva aunque el producto o almacén se haya desactivado. Si falla una línea o la auditoría, se revierte todo. La existencia física, costo y kardex no cambian. Las operaciones concurrentes de creación, confirmación y cancelación se serializan por el bloqueo del pedido.

La migración [V12](../../erp-backend/src/main/resources/db/migration/V12__sales_order_cancellation_metadata.sql) agrega a `pedidos_venta` motivo, actor y fecha, con FK y una restricción de coherencia. Los tres campos admiten `null` juntos para preservar cancelaciones anteriores sin datos conocidos; V12 no fabrica metadatos ni modifica V11. Los triggers de V12 impiden modificaciones y borrados ordinarios de cabeceras y detalles cancelados; un propietario o administrador con capacidad de alterar controles conserva privilegios sobre la base. Inventario exige transacción existente y verifica antes del commit que el pedido haya quedado cancelado. Los eventos de auditoría registran acción, actor y estados anterior/nuevo, sin copiar el texto libre del motivo. La parte 12 corrigió la enumeración del [contrato de auditoría](../api/openapi-audit.yaml) para incluir los estados de Ventas; los consumidores estrictos deben actualizarla.

## Contrato y arquitectura

Al cierre de la parte 11, el [OpenAPI de Ventas](../api/openapi-sprint-3-ventas.yaml) describía 16 rutas implementadas; la parte 12 agregó facturación interna. Crédito, cobros y portal siguen pendientes. La respuesta de pedido agrega `motivoCancelacion`, `canceladoPorUsuarioId` y `canceladoEn`, que pueden ser nulos. Los consumidores que validan estrictamente las respuestas deben actualizar su esquema al contrato `0.4.0`.

Ventas coordina la transacción y delega la modificación de existencias a Inventario. Los repositorios separan la persistencia y los DTO mantienen los contratos; bloqueos, auditoría y escrituras son efectos de infraestructura, no programación funcional. No se afirma que los componentes sean portables sin la demostración prevista. La [secuencia UML](../diagrams/arquitectura/12-secuencia-cancelacion-ventas.puml), el modelo DBML y ambas guías se mantienen alineados con la implementación.

## Verificación

El 11 nov. 2026, con Java 21 y PostgreSQL, la regresión completa sobre la V12 final pasó 68 pruebas Surefire y 170 Failsafe. Después se amplió la auditoría para registrar estados anterior y nuevo; la ejecución focalizada posterior pasó tres pruebas de contrato y 24 de integración, incluidas 16 de cancelaciones, siete de auditoría y una de migración. Las pruebas cubren permisos, reintentos, motivos, liberación parcial, preservación de kardex, inmutabilidad por DML ordinario, conteos, concurrencia, fallos y rollback.

La primera prueba de actualización desde V11 falló por el `search_path` del esquema aislado de pruebas; se corrigió el montaje y la repetición pasó. Una regresión posterior se interrumpió intencionalmente para incluir las protecciones definitivas de V12 y se ejecutó nuevamente. El backend de prueba aplicó la migración; no se aplicó a un entorno compartido.

Para reproducir la regresión desde `erp-backend/`, con las dependencias Maven previamente descargadas:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

Para la comprobación focalizada final, agregar `-Dtest=PedidoOpenApiContractTest,DespachoOpenApiContractTest,ClienteOpenApiContractTest -Dit.test=CancelacionVentasIT,CancelacionMigrationIT,AuditIntegrationIT` al comando. MockMvc y PostgreSQL no demuestran integración de React, red ni rendimiento bajo carga.
