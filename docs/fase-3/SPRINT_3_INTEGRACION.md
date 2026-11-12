# Sprint 3, parte 14 — Integración y regresión

## Flujo integrado de Ventas

`FacturaApiIT.flujoHttpDesdeCotizacionHastaFactura` prueba el recorrido HTTP de alta de cliente, cotización, emisión y aceptación, conversión a pedido de contado, confirmación con reserva, despacho confirmado y emisión de factura comercial interna. El usuario de prueba inicia sesión y obtiene permisos explícitos para cada operación. La factura conserva el cliente del pedido y los importes calculados.

La prueba también comprueba que el despacho produce el movimiento de salida del kardex y reduce la existencia física en tres unidades; la emisión de factura no vuelve a modificar Inventario. El catálogo base de producto, almacén y existencia se prepara como fixture de PostgreSQL; el cliente y las operaciones de negocio del flujo se crean por HTTP.

## Integración y límites de evidencia

Las pruebas existentes cubren casos complementarios: `ConfirmarPedidoIT` ejercita reserva, concurrencia y rollback; `ConfirmarDespachoIT` cubre salidas, costo, movimientos y reversión; `FacturaApiIT` cubre factura, idempotencia y su relación con despachos; `FlujoCompraInventarioIT` conserva la integración de Compras con Inventario. La suite completa verifica permisos, auditoría, migraciones y módulos anteriores.

MockMvc ejecuta las rutas dentro de la aplicación contra PostgreSQL/Testcontainers, pero no demuestra comunicación desde el frontend React, CORS, proxy, TLS, navegador ni red de despliegue. No se alteran reglas de crédito, pagos ni CFDI.

## Contratos y modelo

Los contratos OpenAPI, DBML y UML se validan durante esta parte para detectar divergencias con la implementación. No se modifica esquema, endpoint, permiso ni comportamiento de producción: el único cambio de código de esta parte amplía la prueba integrada para crear también al cliente mediante su API y verificar el kardex.

## Verificación

La regresión completa con Java 21 y PostgreSQL/Testcontainers pasó: **72 pruebas Surefire y 183 Failsafe, sin fallos**. La suite incluye el recorrido HTTP desde alta de cliente hasta factura y los flujos de compras/inventario. Los contratos OpenAPI pasaron (ocho archivos; Ventas tiene 18 rutas), así como siete ejemplos válidos y tres negativos rechazados. DBML pasó con 71 tablas y 143 relaciones; PlantUML validó/renderizó 50 diagramas. También pasaron `git diff --check` y la comprobación de enlaces locales.

Comando reproducible desde `erp-backend/`:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

Las validaciones OpenAPI y DBML están documentadas en [`docs/api/README.md`](../api/README.md) y [`docs/diagrams/README.md`](../diagrams/README.md). No se ejecutó frontend, navegador, CORS, red de despliegue ni prueba de carga.
