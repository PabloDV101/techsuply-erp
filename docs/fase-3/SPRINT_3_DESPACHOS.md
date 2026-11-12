# Sprint 3, parte 9 — Registro de despachos

La API permite registrar y consultar despachos en `BORRADOR` para pedidos confirmados o parcialmente despachados. El pedido determina el almacén; cada detalle identifica una línea del mismo pedido y una cantidad positiva. La creación no cambia existencia física, reserva, costo, versión, kardex ni estado del pedido. El costo del detalle permanece nulo hasta su confirmación, implementada en la parte 10.

## Reglas y decisión acordada

El usuario aprobó descontar de cada línea las cantidades ya asignadas a otros despachos en borrador. La cantidad nueva debe caber en el menor de la reserva y el saldo pendiente, después de ese descuento. Así se impide asignar la misma reserva dos veces, incluso con solicitudes concurrentes. El pedido se bloquea antes de leer las asignaciones y guardar el nuevo documento.

Un despacho creado consume capacidad de asignación hasta que se confirme o se cancele. La confirmación se añadió en la parte 10 y la cancelación en la parte 11; no hay edición del borrador y este no acredita entrega.

La clave de idempotencia se recorta y es única entre despachos. El mismo actor, pedido y líneas equivalentes —independientemente del orden o escala decimal— reciben el mismo documento con `200`; otro actor o contenido con la misma clave recibe `409`. Una primera creación responde `201` y `Location`. El reintento equivalente recupera el documento antes de revalidar el estado del pedido o la reserva.

## Seguridad, integridad y diseño

Se requieren `VENTAS_DESPACHOS_REGISTRAR` para crear y `VENTAS_DESPACHOS_LEER` para consultar; ambos permisos se verifican en el controlador y el servicio. La transacción usa un bloqueo advisory por clave y bloqueo de pedido para serializar creaciones. La auditoría se registra con el borrador y sus líneas; un fallo revierte todo. La secuencia de folios puede dejar huecos tras rollback. Los documentos preexistentes sin líneas se pueden consultar, pero las solicitudes nuevas exigen al menos una línea.

El controlador delega al servicio de aplicación y a repositorios; los DTO son contratos explícitos y la lista de respuesta es inmutable. La comparación de cantidades por valor y la equivalencia sin depender del orden son funciones sin efectos externos. Los bloqueos y la auditoría son efectos de infraestructura y no se presentan como programación funcional. No se afirma portabilidad de componentes sin comprobarla.

El [OpenAPI](../api/openapi-sprint-3-ventas.yaml), el DBML y el [diagrama de secuencia](../diagrams/arquitectura/11-secuencia-registro-despacho.puml) reflejan el registro; no se añadieron ni editaron migraciones. La confirmación implementada bloquea primero el pedido y después el despacho, manteniendo el mismo orden que el registro, y vuelve a validar reservas y cantidades antes de afectar inventario.

## Verificación

El 11 nov. 2026, con Java 21 y PostgreSQL, la regresión completa pasó 68 pruebas Surefire y 141 Failsafe. La prueba focalizada inicial pasó una prueba de contrato y cinco de integración. Tras ampliar las pruebas sin cambiar el código de producción, la ejecución focalizada pasó una de contrato y ocho de integración; comprueba equivalencia de reintentos, cantidades y borradores concurrentes, permisos, errores y rollback, pedido parcialmente despachado y el flujo HTTP desde la cotización. No se ha medido rendimiento bajo carga ni probado el frontend.

Para reproducir la regresión desde `erp-backend/`, con las dependencias previamente descargadas:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

Para las pruebas focalizadas, agregar `-Dtest=DespachoOpenApiContractTest -Dit.test=DespachoApiIT` al comando. Las pruebas MockMvc y PostgreSQL no demuestran integración de navegador ni React.
