# Sprint 1 — Gestión Maestra y Compras (backend)

**Estado:** partes 0–11 cerradas para el alcance backend: catálogos, órdenes, recepción transaccional y núcleo de inventario con consulta de existencias disponibles. **Rama observada:** `maestra-compras`. **Semana:** 27 de octubre al 2 de noviembre de 2026. Este documento sustituye la distribución anterior de los sprints 1 y 2 en la [ruta de fase 3](../FASE_3_RUTA_BACKEND.md).

## Decisión de alcance para una semana

El [enunciado](../rubrica/proyecto%20final%20dise%C3%B1o.pdf) prioriza proveedores, productos, órdenes de compra y recepción de bienes. El [SRS](../SRS_TechSupply_Solutions.md) define más funciones para Gestión Maestra (RF-MAE-01 a 05) y Compras (RF-COM-01 a 08). Para entregar un flujo completo y comprobable esta semana, el objetivo es:

1. Administrar departamentos, proveedores, almacenes y productos; asociar el producto a la categoría ya existente.
2. Crear, consultar, listar y aprobar/enviar órdenes de compra con una o más líneas, proveedor activo, folio único y precios pactados inmutables tras la aprobación.
3. Registrar y confirmar recepciones parciales contra la orden; aumentar existencias solo por unidades aceptadas y registrar el movimiento y la auditoría en la misma transacción.
4. Demostrar CA-01 del SRS y un reintento idempotente. La orden permanece abierta con 18 aceptadas y 2 rechazadas de 20; un fallo no deja stock, movimiento ni estado parcial.

Este alcance **no cierra todo RF-MAE-05 ni RF-COM-01 a 08**. Requisiciones, factura de proveedor, cuenta por pagar, devolución, historial consolidado, clientes, empleados y puestos quedan identificados para incrementos posteriores. El núcleo de inventario de esta semana existe porque RF-COM-05 y RN-07 exigen actualizar existencias al confirmar recepción; los ajustes, conteos, reservas y reportes de inventario siguen pendientes.

## Fuentes obligatorias al implementar

Documentos operativos de este sprint: [cierre y evidencia](SPRINT_1_CIERRE.md), [migraciones](SPRINT_1_MIGRACIONES.md), [documentación de módulos y trazabilidad](SPRINT_1_MODULOS.md) y [OpenAPI implementado](../api/openapi-sprint-1-maestros-compras.yaml). V6 y V7 están implementadas; las rutas de este contrato cuentan con pruebas.

La [ejecución por partes](SPRINT_1_EJECUCION_POR_PARTES.md) fija el orden detallado, las pruebas de cada entrega y el registro que permitirá continuar en otro chat con «siguiente parte».

- [SRS §§4, 5.2, 5.3, 5.4 y CA-01](../SRS_TechSupply_Solutions.md): estados, invariantes y aceptación. En particular RN-01/03/04/05/06/07/08/09/18/19.
- [DBML canónico](../../database/modelo.dbml): nombres, columnas y relaciones objetivo. Las migraciones V1–V7 son el esquema físico real; no exportar ni ejecutar el DBML completo.
- [Casos de uso de Gestión Maestra](../diagrams/casos-uso/gestion-maestra.puml), [Compras](../diagrams/casos-uso/compras.puml), [clases](../diagrams/clases/02-compras-inventario.puml), [actividad](../diagrams/procesos/01-proceso-compras.puml) y [secuencia de recepción](../diagrams/arquitectura/05-secuencia-recepcion-compra.puml). Distinguir el diseño objetivo de las rutas existentes.
- [Rúbrica final](../rubrica/rubrica_final.pdf): integridad de datos, flujos integrados, reutilización, pruebas, documentación y patrones justificados.

## Tablas previstas

V6 se creó tras comprobar V1–V5 y se verificó en PostgreSQL. V7 está implementada como `V7__purchase_receipt_inventory_core.sql` y probada desde cero y desde V6. No editar migraciones aplicadas. Los campos y escalas se toman del DBML y se complementan con `CHECK`, FK e índices necesarios.

| Tabla | Estado | Razón y relaciones principales |
|---|---|---|
| `categorias_producto` | Existente V3; no recrear | `productos.categoria_id` la referencia. |
| `departamentos` | SQL/JPA/API implementados en partes 1–3 | `ordenes_compra.departamento_id` es obligatorio en el DBML; API disponible para mantenerlo. |
| `proveedores` | SQL/JPA/API implementados en partes 1–3 | Orden con `proveedor_id`; proveedor debe estar activo al crear la orden, sin perder referencias históricas al desactivarlo. |
| `almacenes` | SQL/JPA/API implementados en partes 1–3 | Recepción y existencias por almacén activo. |
| `productos` | SQL/JPA/API implementados en partes 1–4 | SKU único, FK de categoría y proveedor principal opcional; precios/costos y cantidades con `numeric`, no `float`/`double`. |
| `ordenes_compra` | Crear V7 | Folio único, proveedor, departamento, actor, estados y totales. `requisicion_id` se añadirá cuando exista requisición; el DBML describe el modelo objetivo. |
| `detalles_orden_compra` | Crear V7 | FK de orden/producto, cantidad y condiciones comerciales copiadas; `UNIQUE(orden_compra_id, producto_id)` según DBML. |
| `recepciones_compra` | Crear V7 | FK de orden, almacén y usuario; folio y clave de idempotencia únicos. |
| `detalles_recepcion` | Crear V7 | FK de recepción y línea de orden; cantidades recibida/aceptada/rechazada, precio recibido y unicidad por línea. |
| `existencias` | Crear V7 | `UNIQUE(producto_id, almacen_id)`; cantidades física/reservada no negativas y reservada ≤ física. La creación concurrente de la primera fila requiere tratamiento explícito. |
| `movimientos_inventario` | Crear V7 para entradas de recepción | FK a producto, almacén, actor y `detalle_recepcion`; clave de operación única y cantidad positiva. El esquema inicial aceptará solo movimientos con fuente `detalle_recepcion`; nuevas fuentes se añadirán mediante migraciones al implementar otros procesos. |

No se crearán todavía `puestos`, `empleados`, `clientes`, `requisiciones_compra`, `detalles_requisicion`, `facturas_proveedor`, `detalles_factura_proveedor`, `cuentas_por_pagar`, `devoluciones_proveedor` ni `detalles_devolucion_proveedor`. Tampoco se añadirán ahora las FK futuras de `usuarios.empleado_id`/`cliente_id`: antes habrá que comprobar referencias existentes y crear las tablas destino. Las tablas de inventario para ajustes, ventas y reportes corresponden a sus flujos futuros. Cada desviación temporal respecto del DBML se anotará en la documentación del esquema y se cerrará con una migración posterior, nunca modificando V6/V7 ya aplicadas.

## Archivos de backend previstos

Los nombres son propuestas bajo el paquete real `com.techsupply.erp_backend`; antes de crear cada clase se buscará una equivalente. Se reutilizan `common.api.PageRequests`, `PageResponse`, `ApiError`, Spring Security y `AuditTrail`; no se crea un CRUD genérico.

| Área | Archivos principales a crear o ampliar |
|---|---|
| Migraciones | `erp-backend/src/main/resources/db/migration/V6__master_purchase_catalogs.sql`, `V7__purchase_receipt_inventory_core.sql`. |
| Gestión Maestra, dominio/persistencia | `maestros/domain/model/{Departamento,Proveedor,Almacen,Producto}.java`; `maestros/infrastructure/persistence/{Departamento,Proveedor,Almacen,Producto}Repository.java`; `ProductoSpecifications.java` y filtros equivalentes solo donde aporten búsqueda real. |
| Gestión Maestra, aplicación/API | `maestros/application/{Departamento,Proveedor,Almacen,Producto}Service.java`; `maestros/api/{Departamento,Proveedor,Almacen,Producto}Controller.java`; DTO `Crear*Request`, `Actualizar*Request`, `*Response`, `*SearchCriteria` y estado para cada recurso implementado bajo `maestros/application/dto/`. Mantener el patrón HTTP de `CategoriaProductoController`. |
| Compras, dominio/persistencia | `compras/domain/model/{OrdenCompra,DetalleOrdenCompra,RecepcionCompra,DetalleRecepcion}.java` y enums de estado; `compras/infrastructure/persistence/{OrdenCompra,RecepcionCompra}Repository.java` más consultas de líneas si son necesarias. |
| Compras, aplicación/API | `compras/application/{OrdenCompraService,RecepcionCompraService}.java`; reglas deterministas de cantidades/totales en clases de dominio acotadas; `compras/api/{OrdenCompraController,RecepcionCompraController}.java`; DTO de creación, transición, búsqueda y respuesta bajo `compras/application/dto/`. |
| Inventario mínimo | `inventario/domain/model/{Existencia,MovimientoInventario}.java`; `inventario/application/InventarioService.java`; adaptador de persistencia para bloqueo/alta concurrente de existencias y movimiento. Exponer consulta de existencias autorizada si se entrega a frontend, sin permitir escrituras directas ajenas a la recepción. |
| Integración existente | Ampliar `audit/application/{AuditAction,AuditField}.java` con acciones y nombres de campos seguros; migración V6/V7 con permisos de lectura/administración/confirmación necesarios. No guardar precios, direcciones, datos personales ni tokens en el detalle de auditoría. |
| Contratos y pruebas | Crear OpenAPI de maestros/compras y sus pruebas de contrato en `docs/api/` y `erp-backend/src/test/`; pruebas de integración por catálogo y `CompraRecepcionIntegrationIT` usando `PostgresIntegrationSupport`. Actualizar la trazabilidad SRS, el DBML canónico y su copia de diagramación, las vistas UML afectadas y la bitácora de fase 3. |

Rutas activas: `/api/v1/products`, `/suppliers`, `/warehouses`, `/departments`. `/purchase-orders` y `/purchase-receipts`, incluida la confirmación, están activos. Confirmar nombres, DTO, códigos y errores en OpenAPI antes de pasarlos al compañero del frontend. Los permisos se aplican en cada endpoint; si otro módulo llama un servicio directamente, la autorización del caso de uso se valida también en el límite de aplicación apropiado.

## Secuencia de implementación

Este resumen se desglosa en las partes 0–11 de [ejecución por partes](SPRINT_1_EJECUCION_POR_PARTES.md). Cada parte se valida y documenta antes de seguir.

1. **Preflight (realizado):** rama y cambios locales, última migración, pruebas base y diferencias entre DBML/SRS/UML registrados en [parte 0](SPRINT_1_EJECUCION_POR_PARTES.md). Reglas de negocio abiertas se resolverán antes de usarlas.
2. **V6 y catálogos (partes 1–3):** restricciones, FK, índices, permisos y mapeos JPA probados. DTO, `@Valid`, listas paginadas, orden permitido, activación/desactivación y auditoría ya se implementaron para departamentos, proveedores y almacenes. Producto se implementó en la parte 4.
3. **Orden:** crear encabezado y líneas en una transacción, verificar proveedor y productos activos, al menos una línea, folio y cantidades; preservar valores acordados. Aprobar/enviar mediante transiciones explícitas y actor autorizado. Crear orden **no** altera existencias.
4. **Recepción + inventario:** registrar recepción PENDIENTE y confirmar con bloqueo de orden/líneas y existencias. Validar que aceptada + rechazada = recibida y que la suma aceptada no excede pendiente. Usar una clave estable para que dos confirmaciones de la misma recepción no dupliquen stock ni movimientos. Actualizar estado de orden, stock, kardex y auditoría en la misma transacción.
5. **Pruebas y entrega del contrato:** PostgreSQL desechable/Flyway limpio, permisos 401/403, validaciones 400, conflictos 409, transacciones/rollback, idempotencia y dos confirmaciones concurrentes. Actualizar OpenAPI, UML y matriz requisito→código→prueba; enviar al compañero del frontend ejemplos reales de solicitud/respuesta, no solo diagramas.

## Decisiones de negocio por cerrar antes de codificar cada regla

- Confirmar si `punto_reorden` del DBML es el «stock mínimo» de RF-MAE-01 y cómo se usará por almacén. Esta semana se puede persistir el dato sin emitir alertas ni requisiciones automáticas.
- Acordar escala/redondeo de subtotal, descuento e impuesto; si la tasa del DBML se interpreta en porcentaje, el tratamiento de descuentos y qué hacer con diferencias entre precio pactado y precio recibido. No asignar significado fiscal a una factura comercial.
- Confirmar quién puede aprobar/enviar órdenes y si se exige separación entre creador y aprobador; el DBML registra ambos, pero no define esa política.
- Decidir cómo se determina el costo promedio de `existencias` tras una recepción. No presentar como oficial un costo que permanezca en cero por falta de política.
- Resolver cuándo se considera completa la línea de orden: el caso CA-01 y el proceso dibujado cuentan unidades **aceptadas**, de modo que 18 aceptadas y 2 rechazadas de 20 dejan saldo 2. Mantener esta interpretación salvo corrección aprobada.

## Criterios de cierre y evidencia

El sprint se considera cerrado solo si el flujo de orden y recepción pasa en PostgreSQL real de prueba, con Flyway V1–V7, transacción y restricciones; los cambios de API se reflejan en OpenAPI y UML; auditoría y permisos se comprueban por HTTP; `./mvnw -o verify` termina correctamente y se registra el resultado real. Medir cobertura cuando exista herramienta configurada; no declarar cumplimiento del porcentaje de rúbrica sin reporte. Documentar lo que quedó pendiente de RF-MAE y RF-COM, y actualizar [patrones y programación funcional](../PATRONES_Y_PROGRAMACION_FUNCIONAL.md) con métodos y pruebas reales, no patrones agregados solo para la evaluación.
