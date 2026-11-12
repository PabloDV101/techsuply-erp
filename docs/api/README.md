# Contratos HTTP de TechSupply ERP

## Sprint 1 de la Fase 3

[`openapi-sprint-1-maestros-compras.yaml`](openapi-sprint-1-maestros-compras.yaml)
documenta Gestión Maestra, Compras y consulta de existencias.
Tiene `x-implementation-status: implemented`: las operaciones de `/departments`, `/suppliers`, `/warehouses`, `/products`, `/purchase-orders`, `/purchase-receipts` (incluida confirmación) y `/inventory/stock`
están marcadas `implemented` en cada ruta y se comprobaron con
`MasterCatalogOpenApiContractTest`, `MasterCatalogApiIT`, `OrdenCompraOpenApiContractTest`, `OrdenCompraApiIT`, `InventarioOpenApiContractTest`, `InventarioIntegrationIT`, `RecepcionCompraOpenApiContractTest`, `RecepcionCompraApiIT` y `ConfirmarRecepcionIT`. El estado implementado aplica a las operaciones de este contrato, no al alcance completo del ERP. Consultar
la [guía de integración para frontend](guia-sprint-1.md), la [documentación de módulos](../fase-3/SPRINT_1_MODULOS.md) y el
[diseño de migraciones](../fase-3/SPRINT_1_MIGRACIONES.md).

## Sprint 2 de la Fase 3

[`openapi-sprint-2-inventario.yaml`](openapi-sprint-2-inventario.yaml) documenta las rutas implementadas de conteos, ajustes, lectura de movimientos y alertas globales calculadas. El estado `implemented` se refiere a esas rutas, no a la totalidad de Inventario ni al contrato separado de Reportería. La consulta de existencias implementada permanece en el contrato del sprint 1. Las decisiones y diferencias con Reportería están en el [diseño de Inventario](../fase-3/SPRINT_2_DISENO_INVENTARIO.md); ver la [guía de consumo](guia-sprint-2-inventario.md).

## Sprint 3 de la Fase 3

[`openapi-sprint-3-ventas.yaml`](openapi-sprint-3-ventas.yaml) y la [guía propuesta](guia-sprint-3-ventas.md) describen tres rutas de clientes **implementadas** y 13 rutas de cotizaciones, pedidos y despachos **en diseño**. El contrato global permanece `design-only` porque Ventas aún no está disponible; la fecha en Ciudad de México, modalidad de contado, conservación del precio acordado y cancelación del saldo son decisiones acordadas, con detalles aún por definir en el [diseño de Ventas](../fase-3/SPRINT_3_DISENO_VENTAS.md). No se proponen rutas activas de facturación o crédito.

Para validar la estructura y las referencias de todos los contratos, instalar el
validador temporalmente fuera del repositorio y ejecutar:

```bash
npm install --prefix /tmp/techsupply-openapi-tools --no-save --ignore-scripts @apidevtools/swagger-parser@10.1.1
SWAGGER_PARSER_PATH=/tmp/techsupply-openapi-tools/node_modules/@apidevtools/swagger-parser node docs/api/validar-openapi.cjs
SWAGGER_PARSER_PATH=/tmp/techsupply-openapi-tools/node_modules/@apidevtools/swagger-parser node docs/api/validar-ejemplos-sprint3.cjs
```

La comprobación adicional de ejemplos usa Ajv incluido en el validador temporal y no valida formatos (como fechas) ni la precisión decimal del backend. Estas validaciones comprueban el diseño, no sustituyen las pruebas HTTP ni demuestran
que las rutas de reportería o las propuestas de Ventas estén implementadas.

## Contratos implementados en la Fase 2

La base de las rutas es `/api/v1`. Los contratos ejecutables son
[`openapi-auth.yaml`](openapi-auth.yaml),
[`openapi-crud-categorias.yaml`](openapi-crud-categorias.yaml),
[`openapi-audit.yaml`](openapi-audit.yaml) y
[`openapi-notifications.yaml`](openapi-notifications.yaml).
[`openapi-reporteria.yaml`](openapi-reporteria.yaml) lleva
`x-implementation-status: design-only`: sus 12 rutas son propuestas, no existen en el backend.

Todas las API activas devuelven errores con `status`, `code` y `message`.
`security.api` conserva sus mensajes funcionales y `WWW-Authenticate: Bearer` en `401`;
el tipo Java que serializan seguridad y los otros módulos es `common.api.ApiError`.
El componente de esquema `AuthError` se mantiene en el contrato de autenticación
para evitar cambiar referencias de clientes de OpenAPI: tiene los mismos campos
y señala su tipo Java mediante `x-java-type`. Los permisos se verifican en el backend;
la respuesta de `403` intenta registrar un evento de auditoría de mejor esfuerzo.

Las pruebas `AuthOpenApiContractTest`, `CategoriaOpenApiContractTest`,
`AuditOpenApiContractTest` y `NotificationOpenApiContractTest` comparan rutas,
parámetros y DTO principales con los controladores. Las pruebas HTTP de integración
complementan esa comprobación sobre Spring Security, Flyway y PostgreSQL desechable.
Ver la [matriz de integración](../fase-2/integracion/COMPONENTES_FASE_2.md).

Para recorrer manualmente las rutas activas en una base local de prueba, usar
[`pruebas-manuales-fase-2.http`](pruebas-manuales-fase-2.http). Ejecutar las
peticiones en orden con un cliente compatible con archivos `.http`, reemplazando
las contraseñas de ejemplo en una copia privada del archivo.
