# Sprint 1 — documentación de Gestión Maestra y Compras

**Estado:** V6 aporta las cuatro tablas físicas y permisos; la parte 2 añadió entidades JPA/repositorios. Las partes 3 y 4 implementaron API de los cuatro catálogos; el dominio y API de órdenes están implementados; la API de recepción permite registro, consulta y confirmación. Semana 27 de octubre–2 de noviembre de 2026. [Plan operativo](SPRINT_1_GESTION_MAESTRA_COMPRAS.md) · [Migraciones](SPRINT_1_MIGRACIONES.md) · [OpenAPI del sprint](../api/openapi-sprint-1-maestros-compras.yaml).

La [secuencia de entregas y verificaciones](SPRINT_1_EJECUCION_POR_PARTES.md) gobierna el avance y contiene el registro de evidencia de cada parte.

### Parte 1 — por qué se creó V6 así

V6 materializa únicamente los catálogos que necesitan la orden y la recepción del sprint. Las FK de `productos` apuntan a la categoría V3 y al proveedor V6; `UNIQUE` protege códigos/SKU y el identificador fiscal informado; `CHECK` impide identificadores no canónicos, nombres vacíos e importes o umbrales negativos incluso si se escribe fuera de la API. La base conserva `numeric` con las escalas del DBML para evitar pérdida de precisión. Se añadió un índice `(categoria_id, activo)` para la consulta por categoría/estado prevista por RF-MAE-02 y otro para la FK de proveedor principal. Los ocho permisos se registran en el catálogo de seguridad existente y se asignan solo al rol `ADMINISTRADOR` ya existente; V6 no crea cuentas, productos ni proveedores de ejemplo. La aplicación de V5→V6 preservó una categoría previa en la prueba de integración.

La parte 1 no incorporó clases ni patrones de diseño Java: su evidencia fue integridad del esquema y compatibilidad de migración. Las entidades JPA se comprobaron en la parte 2; el contrato HTTP de tres catálogos se construyó en la parte 3. El [DBML](../../database/modelo.dbml) y su copia identifican las cuatro tablas como SQL físico, y la vista PlantUML muestra los cuatro catálogos con API.

### Parte 2 — mapeo JPA y límites

Se agregaron `Departamento`, `Proveedor`, `Almacen` y `Producto` en `maestros/domain/model`, con repositorios Spring Data en `maestros/infrastructure/persistence`. Se mantuvo la convención de `CategoriaProducto`: entidad simple, `Long` para `bigint`, identidad de PostgreSQL, `@Column` con nombres/longitudes reales, sin exponer entidades como respuesta HTTP. `Producto` usa `BigDecimal` con precisión/escala de V6 para precios, impuesto y umbrales; `@ManyToOne(fetch = LAZY)` con categoría obligatoria y proveedor principal opcional. No se agregó cascada: categorías y proveedores conservan ciclo de vida propio y sus referencias históricas. `Proveedor.plazoCreditoDias` es `short`, coherente con `smallint`.

Se reutilizó Repository/DAO de Spring Data para encapsular persistencia; no se añadió un patrón propio ni funciones puras. Las reglas de normalización, estados y autorización quedan para los servicios/API de las partes 3–4. [`MasterCatalogPersistenceIT`](../../erp-backend/src/test/java/com/techsupply/erp_backend/maestros/MasterCatalogPersistenceIT.java) confirmó `ddl-auto=validate`, persistencia y recarga de los cuatro catálogos, relaciones, importes y proveedor opcional/inactivo en PostgreSQL 17. `./mvnw -o verify` pasó 40 Surefire y 54 Failsafe. Este resultado no demuestra todavía los criterios HTTP de RF-MAE-01/02/03/05.

### Parte 3 — API de catálogos maestros

`DepartamentoService`, `ProveedorService` y `AlmacenService` coordinan alta, lectura, edición y activación/desactivación; los controladores usan DTO y permisos separados de lectura/administración. Las listas aceptan filtros exactos `codigo` y `activo`, paginación `page`/`size` y campos de ordenación permitidos por recurso. No existe `DELETE`: los registros inactivos siguen consultables y preservan referencias históricas. Los códigos se normalizan con `trim` y mayúsculas; las restricciones `UNIQUE`/`CHECK` de V6 siguen protegiendo escrituras externas y carreras. Las altas, ediciones efectivas y cambios de estado se auditan en la transacción usando solo nombres de campos y estados, sin valores personales. `MasterCatalogText` y `MasterCatalogSpecifications` comparten únicamente normalización y filtros que los tres recursos usan. Esto justifica Repository, Specification y separación controlador/servicio, sin un CRUD genérico.

La prueba `MasterCatalogApiIT` verifica HTTP, permisos, errores, baja lógica y auditoría con PostgreSQL/Testcontainers; `MasterCatalogOpenApiContractTest` coteja las operaciones y campos de los DTO con OpenAPI. No hubo migración nueva: V6 ya contiene columnas, claves, restricciones y permisos de estos tres recursos. La regla para impedir nuevas órdenes con proveedores inactivos corresponde a la implementación de compras.

## Estado de partida y objetivo

El backend actual tiene seguridad, permisos, categorías, auditoría y bandeja APP; el esquema Flyway llega a V7; orden y detalle tienen entidades JPA, mientras las demás tablas transaccionales aún no tienen entidades ni API. El módulo `maestros` expone categorías, departamentos, proveedores, almacenes y productos. El objetivo del sprint es entregar el recorrido catálogos → orden aprobada/enviada → recepción parcial confirmada → existencias y kardex. Se reutilizan el token Bearer opaco, `PageRequests`/`PageResponse`, `ApiError` y `AuditTrail` existentes.

El [SRS](../SRS_TechSupply_Solutions.md) gobierna las reglas RN-01/03/04/05/06/07/08/09/18/19/20/22 y CA-01. El [DBML](../../database/modelo.dbml) gobierna el modelo objetivo; las [migraciones](SPRINT_1_MIGRACIONES.md) delimitan el incremento físico. Los diagramas de [casos de uso de Gestión Maestra](../diagrams/casos-uso/gestion-maestra.puml), [Compras](../diagrams/casos-uso/compras.puml), [clases Compras–Inventario](../diagrams/clases/02-compras-inventario.puml), [proceso](../diagrams/procesos/01-proceso-compras.puml) y [secuencia de recepción](../diagrams/arquitectura/05-secuencia-recepcion-compra.puml) representan el objetivo completo. Requisición, factura y devolución que aparecen allí no se construirán esta semana.

## Límites de los módulos

| Módulo | Responsabilidad en este sprint | Relación permitida |
|---|---|---|
| `maestros` | Mantener cuatro catálogos y la asociación producto–categoría. Desactivar sin borrar historial. | Compras consulta productos/proveedores activos mediante un servicio/contrato de aplicación; no modifica su persistencia. |
| `compras` | Crear orden con líneas y valores pactados, transiciones de aprobación/envío, registrar y confirmar recepción parcial. | Al confirmar solicita al servicio de inventario la entrada aceptada, dentro de la transacción de negocio. |
| `inventario` | Mantener una existencia por producto–almacén y un movimiento por detalle aceptado. Exponer consulta paginada. | No ofrece escritura directa por HTTP en este sprint. Compras no escribe sus repositorios. |
| `audit`/`security` | Autorizar cada operación y auditar creación/cambio de estado/confirmación con campos seguros. | Reutilizar API interna y formatos existentes; no guardar datos personales, precios o secretos en el detalle de auditoría. |

El controlador traduce HTTP a DTO y delega; la capa de aplicación coordina transacciones y permisos; reglas deterministas de cantidades y totales se aíslan en funciones comprobables sin acceso a repositorios. JPA/Flyway mantienen los detalles de persistencia. No se introduce un repositorio genérico ni un framework de patrones. La justificación y la evidencia posterior de programación funcional/patrones se registran en [su documento](../PATRONES_Y_PROGRAMACION_FUNCIONAL.md).

## Contratos propuestos para el frontend

El archivo OpenAPI contiene el detalle de campos, validaciones, permisos y respuestas. Tiene `x-implementation-status: implemented`: todas las rutas del incremento de maestros, órdenes, recepciones y existencias están implementadas y tienen pruebas; esto no declara completo el ERP. Mantiene base `/api/v1`, JSON, paginación `page/size`, errores `{status, code, message}`, `401` sin token válido, `403` sin permiso, `404` si falta un recurso y `409` para duplicado/conflicto. La ruta de categorías existente permanece en [su contrato](../api/openapi-crud-categorias.yaml).

| Recurso | Operaciones propuestas | Regla visible |
|---|---|---|
| `/departments`, `/suppliers`, `/warehouses`, `/products` | `GET` paginado, `POST`; `GET/PUT` por ID; `PATCH /{id}/status` | Códigos/SKU únicos; baja lógica; producto se filtra por `categoriaId`. |
| `/purchase-orders` | `GET`, `POST`, `GET /{id}`; `POST /{id}/submit`, `/approve`, `/send` | Orden creada en `BORRADOR`, luego `PENDIENTE_APROBACION`, `APROBADA`, `ENVIADA`. Nunca altera stock al crearse. El servidor asigna folio, fecha y actor. |
| `/purchase-receipts` | `GET`, `POST`, `GET /{id}`, `POST /{id}/confirm` | Se registra `PENDIENTE`; la confirmación transaccional puede pasar la orden a `PARCIALMENTE_RECIBIDA` o `RECIBIDA`. `claveIdempotencia` evita crear dos documentos para la misma operación. |
| `/inventory/stock` | `GET` paginado por producto/almacén | `cantidadDisponible = cantidadFisica - cantidadReservada`; no hay escritura directa. |

No se propone editar/cancelar órdenes ni recepciones en este contrato: la matriz del SRS no define aún sus consecuencias tras operaciones asociadas. No se expondrá rechazo de orden sin acordar su estado y motivo, ni factura de proveedor esta semana. Las transiciones solo aceptan estados de origen válidos y devuelven `409` para una transición incompatible. Repetir `confirm` sobre la misma recepción ya confirmada devuelve `200` con el documento actual sin nuevo movimiento; duplicar la clave de creación con contenido diferente devuelve `409`.

**Ejemplo CA-01** (IDs y folios ilustrativos; no son datos sembrados): crear orden con línea `{ "productoId": 7, "cantidad": 20.000, "precioUnitario": 10.00, "descuentoPct": 0, "tasaImpuesto": 16 }`; enviarla a aprobación, aprobarla y registrar envío. Crear recepción con `{ "detalleOrdenCompraId": 31, "cantidadRecibida": 20.000, "cantidadAceptada": 18.000, "cantidadRechazada": 2.000, "precioUnitarioRecibido": 10.00 }` y clave estable. Tras confirmar, consultar stock: física aumenta 18; la línea muestra 18 aceptadas y 2 pendientes; la orden queda `PARCIALMENTE_RECIBIDA`. El motivo del rechazo se conserva en el detalle.

## Secuencia de construcción y archivos

1. **Preparación:** comprobar rama/estado, V5, pruebas base y decisiones abiertas. No tocar cambios locales en `.gitignore` ni `application.yaml` ajenos a este sprint.
2. **V6 y Gestión Maestra:** crear [V6](SPRINT_1_MIGRACIONES.md), entidades/repositories bajo `maestros/domain/model` y `maestros/infrastructure/persistence`, servicios en `maestros/application`, DTO en `maestros/application/dto` y controladores en `maestros/api`. Mantener nombres del paquete real `com.techsupply.erp_backend`; buscar clases equivalentes antes de crearlas. Pruebas HTTP de permisos, validación y unicidad.
3. **V7 y orden:** crear tablas de orden y recepción, `compras/domain/model/{OrdenCompra,DetalleOrdenCompra,RecepcionCompra,DetalleRecepcion}` y enums de estado, repositorios y servicios/controladores/DTO correspondientes. Separar cálculo puro de importes y pendiente; congelar valores de línea tras aprobación. Verificar orden de una o varias líneas, folio único y ninguna variación de stock al crear.
4. **Inventario y confirmación:** `inventario/domain/model/{Existencia,MovimientoInventario}`, repositorios y `inventario/application/InventarioService`; añadir consulta HTTP de stock. Bloquear orden/líneas y la fila de existencia; resolver creación concurrente de la primera fila con unicidad y reintento seguro. Confirmación, movimiento y auditoría en la misma transacción.
5. **Cierre:** ampliar `AuditAction`/`AuditField` solo con etiquetas seguras, alinear OpenAPI con controladores y DTO, actualizar UML que cambie realmente, enlazar requisito–código–prueba y registrar resultado de `./mvnw -o verify`. Entregar al compañero del frontend el contrato ya implementado y ejemplos comprobados.

La lista de clases es orientativa; el detalle de archivos se decidirá al implementar cada historia para no duplicar servicios ni crear abstracciones anticipadas. Las migraciones sí tienen nombres previstos y se crearán solamente cuando se codifique su funcionalidad.

## Trazabilidad y evidencia de cierre

| Requisito | Alcance del sprint | Evidencia de prueba prevista |
|---|---|---|
| RF-MAE-01, RN-01 | Producto CRUD, SKU único, importes no negativos y baja lógica | Integración de producto y colisión de SKU. |
| RF-MAE-02 | Producto asociado y filtrable por categoría existente | Consulta por categoría; categoría inválida/inactiva según regla acordada. |
| RF-MAE-03 | Proveedor y estado; orden nueva exige proveedor activo | HTTP de proveedor; orden con proveedor inactivo rechazada e historia preservada. |
| RF-MAE-05 | Departamento y almacén; empleados y puestos pendientes | Integración de catálogos y FK de orden/recepción. |
| RF-COM-03, RN-04/07 | Orden con líneas y valores pactados; sin entrada de inventario | Prueba de creación/folio, cambio posterior de precio de catálogo y stock intacto. |
| RF-COM-04/05, RN-08/09, CA-01 | Recepciones parciales, saldo por aceptadas, rechazo registrado | Caso 20/18/2, segunda recepción, orden completa y límites de saldo. |
| RF-INV-01/02 parcial, RN-05/06/19 | Existencia única y movimiento de recepción | PostgreSQL: FK/CHECK/UNIQUE; entrada por aceptadas, fuente única. Otros tipos de movimiento pendientes. |
| RN-18/20/22, RF-SEG-03 | Idempotencia, permisos, auditoría | Reintento, dos confirmaciones concurrentes, rollback, 401/403 y evento sin datos sensibles. |
| RNF-17 y rúbrica | API, trazabilidad, pruebas y patrones justificados | Contrato OpenAPI comparado con controlador/DTO; `verify`; reporte de cobertura solo si se mide. |

Las pruebas usarán PostgreSQL desechable con Flyway y las bases de integración del proyecto. Se ejecutarán casos positivos y negativos, `./mvnw -o verify` y `git diff --check`. No se declarará cobertura, rendimiento ni módulo completo por el mero diseño de las pruebas.

## Políticas para las siguientes partes

Las decisiones de cálculo, aprobación, umbral, costo, idempotencia y referencias están fijadas para el MVP en [políticas del sprint](SPRINT_1_POLITICAS.md). Son decisiones de diseño del equipo, todavía sin implementación ni pruebas de negocio; no implican cumplimiento fiscal. El proceso y la secuencia UML ya representan la recepción `PENDIENTE` y la clave al registrar.

El alcance no cierra RF-MAE-04 ni RF-COM-01/02/06/07/08. Requisiciones, clientes, empleados/puestos, facturas, cuentas por pagar, devoluciones y movimientos de otras fuentes se programan en incrementos posteriores según la [ruta de fase 3](../FASE_3_RUTA_BACKEND.md). La [rúbrica](../rubrica/rubrica_final.pdf) evalúa el producto final; esta documentación aporta diseño y trazabilidad, no evidencia de ejecución.

### Parte 4 — API de productos

`ProductoService` coordina alta, consulta, búsqueda, edición y cambio de estado. El SKU se normaliza y la unicidad también se protege en PostgreSQL. La categoría es obligatoria y el proveedor principal opcional; ambos deben existir. No se ha definido una política que prohíba asignar referencias inactivas. Los importes y umbrales se validan como `BigDecimal` dentro de la precisión de V6. `puntoReorden` se guarda como umbral: todavía no calcula alertas ni stock por almacén. El controlador exige permisos de productos; cada cambio efectivo se audita en la transacción sin almacenar valores. La búsqueda filtra por SKU, categoría exacta y estado, con paginación y orden permitido.

### Parte 6 — dominio y persistencia de órdenes

`OrdenCompra`, `DetalleOrdenCompra` y `EstadoOrdenCompra` mapean V7; sus relaciones son perezosas y no hay cascada de eliminación. La orden admite líneas solo en borrador, calcula totales antes de incorporarlas, exige al menos una para enviar a aprobación y valida las transiciones y separación de aprobador. Las líneas conservan cantidad, precio, descuento e impuesto pactados; no ofrecen setters públicos. `CalculoOrden` es una transformación determinista de valores `BigDecimal`, con redondeo por línea y límites del esquema; `pendiente` resta aceptadas. Los repositorios Spring Data representan la persistencia, no un contrato HTTP.

La autorización por permisos, proveedor/productos activos, generación de folio, auditoría transaccional y escritura HTTP corresponden a la parte 7. Recepciones y saldo acumulado real siguen pendientes; la función de saldo solo opera sobre valores suministrados. El constructor del borrador admite una orden sin líneas, pero no puede enviarse a aprobación vacía.

### Parte 7 — casos de uso y API de órdenes

`OrdenCompraController` y `OrdenCompraService` exponen alta, lista, detalle y transiciones `submit`, `approve` y `send`. Cada operación exige su permiso tanto en HTTP como en aplicación; el actor se obtiene del contexto autenticado. La creación comprueba proveedor y productos activos mediante `CatalogoParaCompras`, bloquea sus filas durante la transacción, valida duplicados y genera el folio con la secuencia V7. El borrador y sus líneas se guardan junto al evento de auditoría; no se altera inventario.

Las transiciones bloquean la orden con `FOR UPDATE`, aplican las reglas de dominio y registran auditoría en la misma transacción. La lectura calcula aceptadas solo desde recepciones confirmadas; la búsqueda carga las líneas por lote y usa paginación y orden permitidos. La prueba de integración crea datos de recepción directamente como fixture para verificar la lectura: la API de recepción aún no existe. Las órdenes no ofrecen edición de líneas en este incremento, por lo que los valores pactados quedan conservados.

### Parte 8 — núcleo de inventario y consulta de existencias

`Existencia` y `MovimientoInventario` mapean V7; el repositorio de movimientos expone solo lectura. `InventarioService.registrarEntradaRecepcion` es interno y exige permiso de confirmación y una transacción ya iniciada. Obtiene cantidad, costo, producto y almacén del detalle de recepción, valida orden, estado y almacén, e inserta la primera existencia con `ON CONFLICT DO NOTHING` antes de bloquear la fila. Calcula el promedio por producto/almacén y registra un movimiento único por detalle. Si la recepción no queda `CONFIRMADA` al finalizar la transacción, una comprobación previa al commit la revierte. La confirmación implementada en la parte 10 bloquea primero la orden y sus líneas, hace `flush` del estado de recepción antes del commit y valida saldo, estado de orden y auditoría en la misma transacción. Este núcleo no constituye por sí solo el flujo de confirmación de recepción.

`ValoracionInventario` es una función pura que aplica el promedio ponderado y `HALF_UP` a cuatro decimales. La API `GET /inventory/stock` es de solo lectura, exige `INVENTARIO_EXISTENCIAS_LEER`, aplica filtros y paginación, y devuelve disponibilidad como física menos reservada. No existe API de escritura directa. Las pruebas de integración usan transacciones y fixtures de recepción para verificar concurrencia, rollback y permisos; la API de recepciones sigue pendiente. La inmutabilidad total de kardex frente a SQL directo sigue siendo parte del modelo objetivo, no un trigger V7.

### Parte 9 — registro y consulta de recepción

`RecepcionCompra` y `DetalleRecepcion` se mapean a V7; el controlador y servicio ofrecen alta, detalle y lista paginada con permisos separados. La orden debe existir y estar `ENVIADA` o `PARCIALMENTE_RECIBIDA`; el almacén debe estar activo y cada línea debe pertenecer a la orden. Se validan unicidad de líneas, precisión y que aceptadas más rechazadas no excedan lo recibido. El registro guarda `PENDIENTE`, actor, folio y auditoría en una sola transacción sin tocar inventario.

La clave de idempotencia se recorta y es sensible a mayúsculas; su bloqueo advisory transaccional precede al bloqueo de la orden. Un reintento compara orden, almacén, referencia y líneas por ID, independientemente del orden del arreglo. Los decimales se comparan por valor y los textos opcionales se recortan, convirtiendo vacíos en `null`; mismo contenido devuelve 200 y contenido diferente 409. Este control también serializa llamadas simultáneas con la misma clave. La confirmación, igualdad de cantidades y saldo acumulado se implementaron en la parte 10.

### Parte 10 — confirmación transaccional de recepción

`RecepcionCompraService.confirmar` exige `COMPRAS_RECEPCIONES_CONFIRMAR` en controlador y servicio. Busca la orden de la recepción y bloquea, en ese orden, la orden, la recepción y las líneas; valida el almacén activo, la igualdad de cantidades y que las aceptadas confirmadas más las nuevas no excedan lo ordenado. Después solicita a `InventarioService` entradas solo para líneas con aceptadas positivas, ordenadas por producto para mantener una adquisición estable de bloqueos. Se actualizan estado de orden, recepción, movimientos, existencias y auditoría en la misma transacción; un fallo revierte todos los cambios. La auditoría registra los estados anteriores y nuevos mediante un catálogo cerrado, documentado en el contrato de auditoría.

El saldo usa las aceptadas, siguiendo CA-01: 20 recibidas, 18 aceptadas y 2 rechazadas dejan 2 pendientes y orden `PARCIALMENTE_RECIBIDA`. Tras completar todas las líneas, la orden queda `RECIBIDA`; si no hay aceptadas se mantiene `ENVIADA`. Un reintento de una recepción confirmada devuelve el documento sin crear otro movimiento ni otra auditoría. El caso 20/18/2, una segunda recepción, el reintento y las carreras se prueban en `ConfirmarRecepcionIT`; un caso recorre por HTTP alta, aprobación y envío de orden, registro y confirmación de recepción; otros usan fixtures SQL para carreras y rollback. No hay facturas ni requisiciones implementadas.
