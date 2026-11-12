# Sprint 3, parte 6 — Cotizaciones

La API de cotizaciones permite crear, consultar, listar, emitir, aceptar y cancelar cotizaciones internas. Conserva cantidades, precios, descuentos e impuestos de cada línea y registra al vendedor a partir de la identidad autenticada. No reserva ni modifica existencias. La conversión a pedido se implementó en la parte 7; el portal, los pagos y las operaciones de crédito permanecen fuera del alcance.

## Reglas acordadas

- El usuario aprobó la política de Compras: por línea se calcula el bruto exacto, se aplica el descuento y se redondean la base y el impuesto a dos decimales con `HALF_UP`. El impuesto se calcula sobre la base antes de redondearla. Los totales son la suma de las bases e impuestos redondeados, y deben caber en `numeric(14,2)`.
- `vigenciaHasta` incluye todo el día indicado, usando la fecha de `America/Mexico_City`. Se obtiene la fecha a partir de un `Clock`; no se depende de la zona por defecto del servidor. La creación rechaza vigencias anteriores a la fecha de negocio, y emisión y aceptación requieren que la cotización siga vigente.
- El usuario aprobó que el permiso `VENTAS_COTIZACIONES_ADMIN` permita emitir y aceptar cotizaciones con descuento; no hay en este incremento un límite o aprobación adicional. El permiso `VENTAS_COTIZACIONES_REGISTRAR` permite capturar las condiciones comerciales. La aceptación la registra un usuario interno autorizado y no constituye firma ni aceptación autenticada del cliente.
- El precio guardado procede de la solicitud y no se reemplaza por cambios posteriores al catálogo. Las condiciones guardadas no se editan mediante esta API; el pedido preserva la instantánea desde la parte 7.

La creación requiere cliente y productos existentes y activos, líneas con cantidades positivas, valores decimales dentro de rango y sin productos duplicados. La consulta admite documentos preexistentes sin líneas, aunque no se pueden emitir ni aceptar. Los registros de cliente y productos se leen bajo bloqueo compartido durante la transacción; no se comprueba ni se reserva stock al cotizar. Emitir y aceptar requieren cliente activo, líneas y estado y vigencia compatibles. Cancelar permite `BORRADOR` o `VIGENTE`, conserva el documento y no modifica inventario.

El estado almacenado registra la última transición: **el paso del tiempo no cambia automáticamente `VIGENTE` a `VENCIDA`**. Si una cotización está vencida por fecha, emisión y aceptación devuelven `409` aunque el estado almacenado siga siendo `BORRADOR` o `VIGENTE`; una conversión nueva comprueba también la vigencia desde la parte 7. Repetir emisión, aceptación o cancelación en un estado incompatible devuelve `409`.

## HTTP, permisos y persistencia

El contrato en [OpenAPI](../api/openapi-sprint-3-ventas.yaml) marca como implementadas las cinco rutas de cotizaciones: colección, detalle, emisión, aceptación y cancelación. La colección admite `page`, `size` y filtro `clienteId`; usa orden descendente por identificador y recupera las líneas de la página en una consulta adicional. Se aplican los permisos `VENTAS_COTIZACIONES_LEER`, `VENTAS_COTIZACIONES_REGISTRAR` y `VENTAS_COTIZACIONES_ADMIN`, tanto en la API como en el servicio.

Crear acepta una clave idempotente recortada, única entre cotizaciones: una primera solicitud devuelve `201` y `Location`; un reintento con el mismo vendedor, cliente, fecha y líneas equivalentes devuelve `200` y la cotización existente. La comparación decimal es por valor y las líneas se identifican por producto, independientemente de su orden. Una clave con otro vendedor o contenido devuelve `409`. Un bloqueo advisory por clave y la unicidad de V11 serializan solicitudes concurrentes, incluso si la fila aún no existe. Los reintentos equivalentes se comprueban antes de validar nuevamente la vigencia o el estado del catálogo.

Cada transición bloquea la cabecera con `FOR UPDATE` y registra auditoría dentro de la misma transacción. Si falla la auditoría se revierten la creación o la transición. La secuencia de folios no es transaccional y puede dejar huecos después de un fallo; no se reutilizan folios. No se añade una migración ni se cambia V11.

## Programación funcional y patrones

`CalculoCotizacion` y `VigenciaCotizacion` separan cálculos deterministas de persistencia, reloj, autorización y auditoría. `ValoresVenta.Linea` es un record inmutable de valores `BigDecimal`; el cálculo de importes usa la misma fórmula de Compras, con una prueba que contrasta resultados. Se mantiene en Ventas mientras no exista una extracción compartida justificada y probada. Los controladores delegan en servicios de aplicación y repositorios; el patrón Repository, los límites transaccionales y la idempotencia no se atribuyen por sí mismos a programación funcional.

## Límites y verificación

Las pruebas unitarias comprueban redondeo, límites, fecha inclusiva y conversión de zona; las pruebas HTTP y PostgreSQL cubren autorizaciones, datos inválidos, instantáneas, reintentos, carreras y rollback de auditoría. Las pruebas MockMvc no demuestran navegación React, red o rendimiento bajo carga. La vigencia también se comprueba al confirmar un pedido; las reglas de excepciones comerciales y el tratamiento de despachos borrador al cancelar pedidos siguen pendientes.

El 11 nov. 2026, con Java 21 y PostgreSQL, la regresión completa pasó 66 pruebas Surefire y 116 Failsafe. El primer intento detectó una expectativa de la prueba de contrato de clientes que aún marcaba cotizaciones como diseño; se corrigió y se repitió la regresión. Después se ampliaron pruebas, sin cambiar código de producción, y la ejecución focalizada pasó 5 Surefire y 7 Failsafe. También validaron ocho contratos OpenAPI, sus ejemplos de Ventas, el DBML y 45 diagramas PlantUML.

Para reproducir la regresión desde `erp-backend/`:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

La ejecución focalizada posterior usa el mismo comando agregando `-Dtest=CotizacionOpenApiContractTest,CotizacionCreateRequestTest,CalculoCotizacionTest -Dit.test=CotizacionConsultaIT,CotizacionApiIT`. La opción `-o` requiere dependencias Maven previamente descargadas.
