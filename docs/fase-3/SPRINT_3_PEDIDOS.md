# Sprint 3, parte 7 — Conversión a pedido

La API crea pedidos internos en `BORRADOR` desde una cotización aceptada y vigente, y permite consultar su detalle y listar pedidos con paginación y filtro de cliente. La parte 8 añadió confirmación con reserva; despachos y cancelaciones se incorporaron en partes posteriores.

## Reglas y decisiones

- El usuario aprobó rechazar conversiones nuevas de cotizaciones vencidas y requerir una nueva cotización; no hay revalidación automática. La fecha de negocio utiliza `America/Mexico_City` y el último día es inclusivo.
- Al convertir, cliente, almacén y productos deben existir y estar activos. La solicitud solo admite `CONTADO`, como modalidad comercial, sin registrar ni acreditar cobro. Las líneas, cantidades, precio, descuento, impuesto, totales y vendedor se copian de la cotización; el actor que crea el pedido se registra por separado. Cambios posteriores de precio en el catálogo no reemplazan el precio acordado.
- Una cotización produce como máximo un pedido. Crear usa una clave de idempotencia recortada y única entre pedidos. Un reintento con el mismo actor, cotización, almacén y modalidad devuelve `200` y el pedido existente, incluso si posteriormente vence la cotización o cambia un catálogo. Con otra clave para una cotización ya convertida, o con contenido o actor distinto para la misma clave, responde `409`. La primera creación responde `201` con `Location`.
- Se requiere `VENTAS_PEDIDOS_REGISTRAR` para crear y `VENTAS_PEDIDOS_LEER` para consultar; controlador y servicio aplican autorización. No se exponen entidades JPA como respuesta.

## Integridad y diseño

La transacción serializa la clave mediante bloqueo advisory de PostgreSQL, bloquea la cotización y verifica su vínculo único; los índices únicos de V11 respaldan ambas restricciones. Lee cliente, almacén y productos con bloqueo compartido y productos en orden estable. La auditoría y las líneas se guardan en la misma transacción que el pedido; una falla revierte la operación. No se cambia stock ni kardex. La secuencia de folios puede dejar huecos si una transacción se revierte.

El diseño mantiene el controlador, servicio y repositorios separados. Los DTO de respuesta son inmutables y copian su lista; la conversión copia valores comerciales sin recalcular desde el catálogo. `VigenciaCotizacion` conserva el cálculo puro de fechas y el reloj permanece inyectado. La idempotencia y los bloqueos son controles de integridad, no programación funcional. No se introdujo una abstracción genérica ni se afirma que el módulo sea intercambiable en otros proyectos sin una prueba de portabilidad.

El [contrato OpenAPI](../api/openapi-sprint-3-ventas.yaml) distingue las rutas implementadas de las que siguen en diseño. El [diagrama de secuencia](../diagrams/arquitectura/09-secuencia-conversion-pedido.puml) representa la conversión. No se cambió la estructura de V11 ni se añadió migración.

## Pendientes

La cancelación del saldo se implementó en la parte 11. El estado `BORRADOR` no demuestra disponibilidad, pago, despacho ni venta completada. Los documentos existentes sin líneas pueden consultarse, pero una cotización sin líneas no puede convertirse. No se ha validado rendimiento con carga ni el frontend. Véase también [confirmación y reserva](SPRINT_3_RESERVAS.md).

## Verificación

El 11 nov. 2026, con Java 21 y PostgreSQL, la regresión completa pasó 67 pruebas Surefire y 121 Failsafe. Después se ampliaron pruebas sin cambiar comportamiento de producción: la ejecución focalizada pasó una prueba unitaria de contrato y cinco pruebas de integración, incluido el recorrido HTTP desde cotización hasta pedido, concurrencia y rollback de auditoría. No se ejecutaron pruebas de carga ni frontend.

Para reproducir la regresión desde `erp-backend/`, con las dependencias ya descargadas:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

Para la ejecución focalizada, agregar `-Dtest=PedidoOpenApiContractTest -Dit.test=PedidoApiIT` al comando.
