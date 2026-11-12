# Guía de consumo — Inventario, sprint 2

**Disponible:** consulta de movimientos, conteos, ajustes y alertas globales calculadas en el [OpenAPI](openapi-sprint-2-inventario.yaml).

Con un token Bearer que tenga `INVENTARIO_MOVIMIENTOS_LEER`:

```http
GET /api/v1/inventory/movements?page=0&size=20&productoId=42&almacenId=3
Authorization: Bearer <token>
```

`productoId` y `almacenId` son opcionales y, si se envían, deben ser positivos. `page` empieza en cero y `size` admite de 1 a 100; los valores predeterminados son 0 y 20. Se ordena por `fecha` e `id` descendentes. El resultado tiene `content`, `page`, `size`, `totalElements` y `totalPages`. Una página con filtros sin resultados responde `200` y una lista vacía. Con inserciones entre solicitudes, la paginación por desplazamiento puede mover elementos entre páginas.

Cada movimiento incluye identificación de producto/almacén, tipo, motivo, cantidad, costo, fecha, usuario y `detalleRecepcionId`, `detalleAjusteId` o `detalleDespachoId`; las fuentes que no corresponden son `null`. El campo `detalleDespachoId` y el motivo `DESPACHO_VENTA` se agregaron en el sprint 3; consumidores con validación estricta deben actualizar su esquema. Las cantidades y costos se transmiten como números decimales JSON; el consumidor debe considerar los límites de precisión de su representación numérica. El permiso de lectura no habilita escritura directa del kardex. V10 impide modificar o borrar movimientos mediante DML ordinario; las correcciones por reversa aún no están disponibles.

La falta de autenticación devuelve `401`, un permiso insuficiente `403` y parámetros inválidos `400`, usando el formato de error común. El backend registra denegaciones de autorización con el mecanismo de auditoría de mejor esfuerzo existente.

## Conteos físicos

Con `INVENTARIO_CONTEOS_ADMIN`, crear un conteo para un almacén activo:

```http
POST /api/v1/inventory/counts
Authorization: Bearer <token>
Content-Type: application/json

{"almacenId":3,"fechaProgramada":"2026-11-10","clase":"CICLICO"}
```

La respuesta `201` incluye `Location`, el identificador y el estado `PROGRAMADO`. Capturar un producto con `PUT /api/v1/inventory/counts/{id}/lines/{productoId}` y cuerpo `{"cantidadContada":18}`. La respuesta incluye cantidad del sistema, contada, diferencia y versión observada. Si no existía stock, la cantidad del sistema es cero y la versión es `null`. Una recaptura reemplaza esa fotografía, por lo que un reintento puede observar stock distinto.

Usar `POST /api/v1/inventory/counts/{id}/close` para cerrar con al menos una línea o `POST /api/v1/inventory/counts/{id}/cancel` antes del cierre. Repetir una transición terminal responde `409`. Para consultar se requiere `INVENTARIO_CONTEOS_LEER`: `GET /api/v1/inventory/counts/{id}` o la lista `GET /api/v1/inventory/counts?page=0&size=20&almacenId=3&estado=EN_CAPTURA`. Los filtros son opcionales y los resultados se ordenan por id descendente. `401`, `403`, `404` y `409` siguen el contrato. Ninguna operación de conteo cambia existencias ni genera movimientos.

## Solicitudes de ajuste

Con `INVENTARIO_AJUSTES_REGISTRAR`, registrar una solicitud:

```http
POST /api/v1/inventory/adjustments
Authorization: Bearer <token>
Content-Type: application/json

{"almacenId":3,"motivo":"Diferencia de conteo","claveIdempotencia":"ajuste-2026-0001","detalles":[{"productoId":42,"tipo":"POSITIVO","cantidad":2,"costoUnitario":10,"detalleConteoId":17}]}
```

La referencia a conteo es opcional; si se envía, la línea debe corresponder a un conteo cerrado del mismo almacén y producto, con sentido y cantidad iguales a la diferencia. Los ajustes positivos requieren costo; los negativos deben omitir `costoUnitario` o enviarlo como `null`. Los campos opcionales también admiten `null`. El registro no altera stock ni crea movimientos.

Una clave nueva devuelve `201` y `Location`. Repetir la misma clave y contenido equivalente devuelve `200` con el documento existente; cambiar el contenido devuelve `409`. Los espacios extremos del motivo, clave y motivo de línea se recortan, y el orden de líneas y los ceros decimales no cambian la equivalencia. Para consultar por id o listar con filtros `almacenId` y `estado` se necesita `INVENTARIO_AJUSTES_LEER`; el estado inicial es `PENDIENTE`.

## Aprobar o rechazar un ajuste

El usuario necesita `INVENTARIO_AJUSTES_APROBAR` y debe ser distinto de quien registró la solicitud. La identidad y fecha de autorización las asigna el servidor. Para aprobar un ajuste pendiente:

```http
POST /api/v1/inventory/adjustments/{id}/approve
Authorization: Bearer <token>
```

Para rechazarlo:

```http
POST /api/v1/inventory/adjustments/{id}/reject
Authorization: Bearer <token>
Content-Type: application/json

{"motivo":"Es necesario realizar un nuevo conteo"}
```

Ambas operaciones devuelven el documento actualizado con `autorizadoPorUsuarioId` y `autorizadoEn`; el rechazo incluye `motivoRechazo`. Un motivo ausente, vacío o no válido produce `400`; falta de permiso produce `403`, y autorizar una solicitud propia o repetir una transición ya realizada produce `409`. Aprobar o rechazar no altera existencias ni genera movimientos.


## Aplicar un ajuste aprobado

Con `INVENTARIO_AJUSTES_APLICAR`:

```http
POST /api/v1/inventory/adjustments/{id}/apply
Authorization: Bearer <token>
```

Devuelve `200` con estado `APLICADO`, fecha y costo unitario de cada salida; repetirlo devuelve el documento sin volver a modificar existencias. Las líneas positivas usan el costo capturado y recalculan el promedio; las negativas usan el costo promedio vigente y no pueden reducir el físico por debajo de lo reservado. La solicitud debe seguir aprobada y sus productos y almacén activos. Si una referencia de conteo está obsoleta o ya fue aplicada, o existe un conflicto de estado, reserva o límites, responde `409`: para un conteo obsoleto se necesita uno nuevo. También se aplican `401`, `403` y `404` según el caso. Todas las líneas y la auditoría se confirman juntas.


## Alertas de stock mínimo

Con `INVENTARIO_ALERTAS_LEER`:

```http
GET /api/v1/inventory/low-stock-alerts?page=0&size=20&productoId=42
Authorization: Bearer <token>
```

`productoId` es opcional y positivo; sin filtro se consultan todos los productos. `page` empieza en cero y `size` admite 1–100. Se devuelve una página ordenada por `productoId` ascendente, con `productoId`, `sku`, `nombre`, `disponibleGlobal`, `puntoReorden` y `cantidadReorden`. Esta última es la cantidad configurada para reordenar, no una diferencia calculada. El total y el contenido de cada respuesta corresponden a la misma consulta; entre páginas pueden ocurrir cambios de stock.

Se incluyen productos activos con umbral mayor que cero y disponible global menor o igual al umbral. El disponible resta reservas y suma solo almacenes activos; sin existencias activas se considera cero. Un producto inexistente o sin alerta devuelve `200` y contenido vacío; parámetros inválidos devuelven `400`, sin autenticación `401` y sin permiso `403`. La consulta no crea alertas persistidas ni órdenes. Los campos decimales deben tratarse con precisión decimal en el consumidor.


## Recorrido integrado para frontend

La prueba `FlujoCompraInventarioIT` verifica con MockMvc y PostgreSQL el siguiente recorrido backend; los catálogos y usuarios de esa prueba se preparan como datos de prueba. El frontend del equipo aún debe integrarse y validarse por separado.

1. Crear una orden de 20 unidades, enviarla a aprobación con `/purchase-orders/{id}/submit`, aprobarla con otro usuario y enviarla con `/purchase-orders/{id}/send`.
2. Registrar una recepción de 20 unidades con 18 aceptadas y 2 rechazadas; confirmarla con `/purchase-receipts/{id}/confirm`. La orden queda `PARCIALMENTE_RECIBIDA` con 2 pendientes y el stock físico pasa a 18. Repetir la confirmación devuelve el mismo documento sin duplicar la entrada.
3. Crear un conteo, capturar 16 unidades y cerrarlo. La diferencia es −2 y el stock sigue en 18 hasta aplicar el ajuste.
4. Crear una solicitud de ajuste `NEGATIVO` por 2, referenciando el detalle de conteo; otro usuario la aprueba y un usuario con permiso de aplicación la aplica. El stock pasa a 16 y el kardex muestra una salida de 2 con origen en el ajuste.
5. Con punto de reorden 17, la alerta global deja de aparecer tras la recepción de 18 y vuelve a aparecer al quedar 16 disponibles. La consulta de movimientos muestra la entrada y salida con sus respectivas fuentes.

Cada llamada requiere su permiso descrito en el contrato. Si la aplicación responde `403`, el frontend no debe asumir que se hizo el ajuste; la denegación se registra según la política de mejor esfuerzo. Un `409` puede indicar estado, reserva o conteo obsoleto; consultar el error y el estado antes de decidir el siguiente paso. El backend revierte la aplicación completa si falla su auditoría transaccional.
