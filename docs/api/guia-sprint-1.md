# Integración del frontend — sprint 1

El [OpenAPI](openapi-sprint-1-maestros-compras.yaml) describe las operaciones entregadas. La base es `/api/v1`; los cuerpos son JSON. El inicio de sesión y el token Bearer opaco se detallan en [autenticación](openapi-auth.yaml). Enviar `Authorization: Bearer <token>` en cada petición. Los permisos de lectura y escritura son independientes: una cuenta administrativa de un recurso no adquiere automáticamente su permiso de lectura.

## Flujo de prueba

Usar una base de prueba y dos usuarios activos: uno con permisos de administración y lectura de órdenes y de registro/confirmación de recepciones; otro, distinto del creador, con `COMPRAS_ORDENES_APROBAR`. Asignar permisos mediante roles; no compartir contraseñas ni tokens. Antes de la prueba deben existir un proveedor y producto activos, un departamento y un almacén activo. Sus ID se obtienen de los catálogos bajo los permisos correspondientes.

1. Con el primer usuario, enviar `POST /purchase-orders`:

   ```json
   {
     "proveedorId": 1,
     "departamentoId": 1,
     "detalles": [
       {"productoId": 1, "cantidad": 20.000, "precioUnitario": 10.00, "descuentoPct": 0, "tasaImpuesto": 16}
     ]
   }
   ```

   Sustituir los ID de ejemplo por los reales. La respuesta `201` trae `id`, `folio` y el `id` del detalle; guardar ambos ID. Todavía no cambia el inventario.

2. Con el primer usuario llamar `POST /purchase-orders/{id}/submit`; con el segundo llamar `POST /purchase-orders/{id}/approve`; con el primero llamar `POST /purchase-orders/{id}/send`. Cada transición devuelve `200`; un estado incompatible devuelve `409`.
3. Con el primer usuario, enviar `POST /purchase-receipts` con el ID de orden y del detalle devueltos:

   ```json
   {
     "ordenCompraId": 1,
     "almacenId": 1,
     "claveIdempotencia": "sustituir-por-una-clave-unica-de-esta-operacion",
     "detalles": [
       {"detalleOrdenCompraId": 1, "cantidadRecibida": 20.000, "cantidadAceptada": 18.000, "cantidadRechazada": 2.000, "precioUnitarioRecibido": 10.00, "motivoRechazo": "Dañados"}
     ]
   }
   ```

   Generar una clave nueva para cada operación y conservarla para reintentos. `201` crea la recepción `PENDIENTE`; repetir la misma clave y contenido devuelve `200` con el mismo documento, y reutilizar la clave con contenido distinto devuelve `409`. Registrar no cambia el inventario.

4. Con `COMPRAS_RECEPCIONES_CONFIRMAR`, llamar `POST /purchase-receipts/{id}/confirm` usando el ID devuelto. Repetir la confirmación devuelve `200` sin duplicar movimientos. Consultar `GET /purchase-orders/{id}`: la línea tiene 18 aceptadas y 2 pendientes, y la orden está `PARCIALMENTE_RECIBIDA`. Con `INVENTARIO_EXISTENCIAS_LEER`, consultar `GET /inventory/stock?productoId=1&almacenId=1`: la cantidad física aumentó en 18 respecto a la anterior. Para completar la orden, registrar y confirmar una segunda recepción por 2 unidades aceptadas.

Las listas usan `page` desde cero, `size` entre 1 y 100 y los campos de `sort`/`direction` indicados por recurso en OpenAPI; responden con `content`, `page`, `size`, `totalElements` y `totalPages`. Los errores devuelven `status`, `code` y `message`; `401` indica token ausente o inválido, `403` falta de permiso, `404` referencia inexistente y `409` un conflicto. No mostrar datos de autenticación en registros del frontend.

## Límites actuales

Este incremento no implementa requisiciones, facturas de proveedor, devoluciones, alertas de reorden ni ajustes de inventario. La confirmación de recepción no es una factura fiscal. El cálculo monetario y la valoración de inventario siguen las [políticas del sprint](../fase-3/SPRINT_1_POLITICAS.md). Las cifras de disponibilidad y rendimiento del SRS son objetivos pendientes de medición, no garantías verificadas.
