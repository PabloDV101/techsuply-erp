# Sprint 1 — diseño de migraciones V6 y V7

**Estado:** V6 y las API de los cuatro catálogos están implementadas. V7 incorpora el núcleo SQL de órdenes, recepciones, existencias y movimientos; las entidades y API de órdenes, el núcleo de inventario, consulta de existencias y registro, consulta y confirmación de recepciones están implementados. Fuente de columnas: [DBML canónico](../../database/modelo.dbml); esquema físico: Flyway V1–V7.

## Orden y compatibilidad

1. [`V6__master_purchase_catalogs.sql`](../../erp-backend/src/main/resources/db/migration/V6__master_purchase_catalogs.sql): crea catálogos y permisos de Gestión Maestra; aplicada en PostgreSQL desechable tanto desde cero como desde V5.
2. `V7__purchase_receipt_inventory_core.sql`: crear órdenes, recepciones, existencias y movimientos; añadir permisos de Compras/Inventario.
3. No editar V1–V5 ni ejecutar el DBML completo sobre una base existente. Ejecutar Flyway en PostgreSQL vacío y en una base que ya tenga V1–V5; validar después el mapeo JPA con `ddl-auto=validate`.

Las migraciones no insertarán proveedores, productos, almacenes, órdenes ni usuarios de demostración. Los permisos nuevos se asignarán al rol `ADMINISTRADOR` existente mediante `INSERT ... SELECT`, sin crear credenciales. El equipo podrá crear roles operativos con los mecanismos actuales.

Convención técnica fijada en el preflight: códigos maestros y SKU se guardarán sin espacios extremos y en mayúsculas, siguiendo `CategoriaProductoService`; además de `UNIQUE`, un `CHECK` de valor canónico impedirá variantes creadas fuera de la API. V7 creó secuencias PostgreSQL independientes para folios de orden y recepción. El servidor emitirá `OC-`/`RC-` más 12 dígitos; los saltos por rollback son válidos y no cambian la unicidad exigida por RN-01.

## V6: catálogos maestros

| Tabla V6 | Columnas del DBML | Restricciones e índices implementados |
|---|---|---|
| `departamentos` | `id bigint`, `codigo varchar(20)`, `nombre varchar(100)`, `activo boolean` | PK identidad, código único/canónico, nombre no vacío, `activo` predeterminado `true`. |
| `proveedores` | `id`, `codigo varchar(20)`, `nombre_razon_social varchar(160)`, `identificador_fiscal varchar(20)`, `contacto varchar(100)`, `correo varchar(150)`, `telefono varchar(30)`, `direccion text`, `plazo_credito_dias smallint`, `activo boolean` | PK, código único/canónico, nombre no vacío, identificador fiscal único si se informa, plazo no negativo, `activo=true`, plazo predeterminado `0`. No borrar físicamente si tiene órdenes. |
| `almacenes` | `id`, `codigo varchar(20)`, `nombre varchar(100)`, `sede varchar(120)`, `ubicacion varchar(200)`, `activo boolean` | PK, código único/canónico, nombre no vacío, `activo=true`. |
| `productos` | `id`, `sku varchar(30)`, `nombre varchar(160)`, `descripcion text`, `unidad_medida varchar(20)`, `categoria_id bigint`, `proveedor_principal_id bigint`, `precio_lista numeric(14,2)`, `costo_referencia numeric(14,2)`, `tasa_impuesto numeric(5,2)`, `punto_reorden numeric(14,3)`, `cantidad_reorden numeric(14,3)`, `activo boolean` | PK, SKU único/canónico, nombre y unidad no vacíos, FK a `categorias_producto` y `proveedores`, índices de categoría/estado y proveedor principal. Importes y umbrales no negativos; tasa entre 0 y 100. Predeterminados del DBML: `PIEZA`, 0, 0, 16, 0, 1, `true`. |

`categorias_producto` ya existe en V3; V6 solo crea su referencia desde `productos`. Los códigos y SKU se normalizarán en aplicación según el patrón del catálogo de categorías; la unicidad final se protege en PostgreSQL. Desactivar un registro conserva su historial. No se agregarán aún `puestos`, `empleados` ni `clientes`.

**Permisos insertados:** `MAESTROS_DEPARTAMENTOS_LEER/ADMIN`, `MAESTROS_PROVEEDORES_LEER/ADMIN`, `MAESTROS_ALMACENES_LEER/ADMIN` y `MAESTROS_PRODUCTOS_LEER/ADMIN`; todos con `modulo='MAESTROS'` y `accion` igual al sufijo. Se conserva `MAESTROS_CATEGORIAS_*` de V3. V6 asigna los ocho permisos al rol `ADMINISTRADOR` existente, sin crear cuentas.

**Evidencia V6 (28 oct. 2026):** [`MasterCatalogMigrationIT`](../../erp-backend/src/test/java/com/techsupply/erp_backend/maestros/MasterCatalogMigrationIT.java) pasó 3/3 en PostgreSQL 17: arranque limpio, V5→V6 conservando categoría previa, permisos y `UNIQUE`/FK/`CHECK`. La primera versión de la prueba falló por asumir que la base compartida seguía sin productos después de otra prueba; se movió esa aserción al esquema aislado y pasó. Al cerrar la parte 1, `./mvnw -o verify` pasó 40 pruebas Surefire y 52 Failsafe. El mapeo JPA se validó después en la parte 2.

**Evidencia de mapeo (parte 2):** [`MasterCatalogPersistenceIT`](../../erp-backend/src/test/java/com/techsupply/erp_backend/maestros/MasterCatalogPersistenceIT.java) pasó 2/2 con `ddl-auto=validate`; `./mvnw -o verify` pasó 40 Surefire y 54 Failsafe. Probó lectura tras limpiar el contexto JPA, asociaciones, importes `BigDecimal`, proveedor opcional y referencia conservada al desactivarlo.

## V7: núcleo de compra, recepción e inventario

| Tabla nueva | Columnas del DBML | Restricciones e índices implementados |
|---|---|---|
| `ordenes_compra` | `id`, `folio varchar(30)`, `proveedor_id`, `departamento_id`, `creada_por_usuario_id`, `aprobada_por_usuario_id`, `aprobada_en timestamptz`, `motivo_rechazo text`, `fecha timestamptz`, `estado varchar(25)`, `subtotal/impuestos/total numeric(14,2)`, `observaciones text` | PK, folio único, FK a proveedor/departamento/usuarios, importes no negativos, estado restringido a los valores del DBML, índices por proveedor, fecha y estado. `requisicion_id` se pospone: la tabla destino aún no existe. |
| `detalles_orden_compra` | `id`, `orden_compra_id`, `producto_id`, `cantidad numeric(14,3)`, `precio_unitario numeric(14,2)`, `descuento_pct/tasa_impuesto numeric(5,2)` | PK, FK a orden/producto, `UNIQUE(orden_compra_id, producto_id)`, cantidad positiva, precio no negativo, porcentajes de 0 a 100; índice por producto. |
| `recepciones_compra` | `id`, `folio varchar(30)`, `orden_compra_id`, `almacen_id`, `registrada_por_usuario_id`, `clave_idempotencia varchar(120)`, `fecha timestamptz`, `estado varchar(20)`, `referencia_externa varchar(80)`, `confirmada_en timestamptz` | PK, folio y clave únicos, FK a orden/almacén/usuario, estado `PENDIENTE/CONFIRMADA/CANCELADA`, índices por orden, fecha y estado. |
| `detalles_recepcion` | `id`, `recepcion_id`, `detalle_orden_compra_id`, `cantidad_recibida/aceptada/rechazada numeric(14,3)`, `precio_unitario_recibido numeric(14,2)`, `motivo_rechazo text` | PK, FK a recepción/línea, par único `(recepcion_id, detalle_orden_compra_id)`, cantidades no negativas, recibida positiva, precio no negativo, `aceptada + rechazada <= recibida` mientras esté pendiente; igualdad al confirmar se valida transaccionalmente. Índice por línea para acumular aceptadas. |
| `existencias` | `id`, `producto_id`, `almacen_id`, `cantidad_fisica/cantidad_reservada numeric(14,3)`, `costo_promedio numeric(14,4)`, `version int` | PK, FK a producto/almacén, par único, física y reservada no negativas, reservada ≤ física, costo y versión no negativos. Se bloqueará/creará la primera fila de forma segura ante concurrencia. |
| `movimientos_inventario` | `id`, `producto_id`, `almacen_id`, `tipo varchar(12)`, `motivo varchar(35)`, `cantidad numeric(14,3)`, `costo_unitario numeric(14,4)`, `fecha timestamptz`, `usuario_id`, `detalle_recepcion_id`, `clave_operacion varchar(120)`, `referencia text` | PK, FK correspondientes, clave única, cantidad positiva y costo no negativo. En esta versión solo `ENTRADA/RECEPCION_COMPRA` con `detalle_recepcion_id NOT NULL`; índice por producto/almacén/fecha y detalle. Otras fuentes/tipos del DBML se añadirán cuando existan sus tablas. |

Todas las columnas `id` son `bigint GENERATED BY DEFAULT AS IDENTITY`. Las FK obligatorias se crean `NOT NULL`; las opcionales conservan `NULL`. No se usa borrado en cascada para documentos ni kardex. El `CHECK` de igualdad de recepción depende del estado del encabezado y se valida en la transacción de confirmación; la base protege los límites por fila. Los totales de orden se recalculan desde sus líneas en aplicación y se comparan con los importes persistidos antes de aprobar. Una clave de movimiento estable puede derivarse de la línea de recepción confirmada, pero el formato concreto debe quedar fijo antes de codificar.

**Permisos registrados en V7:** `COMPRAS_ORDENES_LEER`, `COMPRAS_ORDENES_ADMIN`, `COMPRAS_ORDENES_APROBAR`, `COMPRAS_RECEPCIONES_LEER`, `COMPRAS_RECEPCIONES_REGISTRAR`, `COMPRAS_RECEPCIONES_CONFIRMAR` e `INVENTARIO_EXISTENCIAS_LEER`. La autorización se aplica por endpoint y, en confirmación, también en el caso de uso. La [política de actor](SPRINT_1_POLITICAS.md) exige que quien aprueba sea distinto del creador; aún debe implementarse.

## Transacción y validación de datos

- Crear orden y líneas en una transacción; no alterar existencias. La orden requiere proveedor activo, departamento existente, productos activos y al menos una línea.
- Registrar recepción `PENDIENTE` sin afectar existencias. Al confirmar, bloquear orden y líneas, comprobar estado y saldo por **cantidad aceptada acumulada**; cada detalle debe cumplir `recibida = aceptada + rechazada`. Solo las aceptadas generan entrada.
- Confirmación: estado de recepción, estado de orden, existencias, movimiento por detalle aceptado y auditoría de éxito se persisten juntos. Un error revierte todo. La clave de idempotencia y la unicidad de movimiento impiden duplicar entradas en reintentos; la misma clave con contenido distinto debe producir `409`.
- Prueba CA-01: orden por 20, recepción de 18 aceptadas y 2 rechazadas, existencia +18, movimiento +18, saldo pendiente 2 y orden `PARCIALMENTE_RECIBIDA`. Verificar rollback y confirmaciones concurrentes.

## Diferencias temporales con el modelo objetivo

`ordenes_compra.requisicion_id` y las fuentes futuras de `movimientos_inventario` no pueden tener FK aún. Se incorporarán en migraciones nuevas cuando existan requisiciones, despachos, ajustes y devoluciones. `usuarios.empleado_id/cliente_id` tampoco recibe FK en este sprint. El documento de [módulos](SPRINT_1_MODULOS.md) registra los requisitos parciales; no se editará una migración aplicada para cerrar estas diferencias.

### Implementación V7

La migración `V7__purchase_receipt_inventory_core.sql` crea las seis tablas y dos secuencias, con las FK disponibles, restricciones e índices de esta sección y siete permisos asignados al rol administrador existente. Cada detalle de recepción admite un único movimiento de entrada, además de la clave de operación única. Se exige que las claves de idempotencia no estén vacías. No se agregaron datos de negocio ni credenciales. Los folios serán generados por aplicación en una parte posterior; la base garantiza unicidad.

El DBML sigue representando el modelo objetivo completo: `requisicion_id` y fuentes futuras de movimientos aún no existen en V7. La validación de coherencia entre orden y recepción, igualdad de cantidades al confirmar, saldo acumulado, transacciones y cálculo de costo requiere los servicios de partes posteriores; V7 no implementa endpoints ni esas reglas.
