# Sprint 2 — diseño propuesto de Inventario

**Estado: lectura de movimientos, conteos y registro/consulta/aprobación/rechazo/aplicación de ajustes y alertas globales implementados.** V9 ya creó el esquema descrito en [migraciones](SPRINT_2_MIGRACIONES.md). Complementa la [ruta por partes](SPRINT_2_INVENTARIO_POR_PARTES.md), el [SRS](../SRS_TechSupply_Solutions.md), el [DBML objetivo](../../database/modelo.dbml) y el [contrato de Inventario](../api/openapi-sprint-2-inventario.yaml). Las reglas indicadas como MVP son decisiones técnicas propuestas para ejecutar el sprint; no sustituyen requisitos aprobados ni políticas contables. Se actualizarán con la implementación y sus pruebas. Flyway V1–V10 y la API de stock del sprint 1 son el estado ejecutable actual; conteos y operaciones de ajustes tienen servicios y API; la consulta del kardex está implementada.

## Alcance y trazabilidad

| Requisito | Diseño del incremento | Evidencia exigida al implementar |
|---|---|---|
| RF-INV-01, RN-05/06 | Conservar stock físico, reservado y disponible; consulta existente. | Una fila por producto/almacén; física ≥ reservada ≥ 0; regresión HTTP y concurrencia. |
| RF-INV-02, RN-03/18/19 | Lectura de kardex implementada; SQL admite recepción o detalle de ajuste como fuente única. V10 protege movimientos y documentos finales frente a DML ordinario y comprueba atributos de fuentes nuevas; las garantías y límites se detallan en la parte 9. | FK/CHECK/índices y API probados en PostgreSQL; fuentes de ventas, devoluciones y reversas permanecen pendientes. |
| RF-INV-05 | Conteo por almacén y producto, referencia, conteo y diferencia, sin cambiar existencias. | Conteo cero, duplicados, cierre y cambio concurrente; comprobar ausencia de movimientos/stock alterados. |
| RF-INV-06, CA-06 | Ajuste solicitado, aprobado por otro usuario y aplicado por actor autorizado; stock, movimiento y auditoría transaccionales. | 401/403, evento de denegación según política existente, segregación, rollback, idempotencia, restricciones y carreras. |
| RF-INV-07 | Consulta de alerta de stock bajo global; sin órdenes automáticas. | Cero, umbral, productos sin filas, activos, permisos y paginación. Rotación y obsolescencia siguen condicionadas a fórmulas y fuentes. |
| CA-01, RN-07/08 | Preservar confirmación de recepción y costo promedio actuales. | Repetir flujo 20/18/2 y actualización desde V8 con datos y movimiento de recepción. |

## Conteo: captura y concurrencia

1. Un actor autorizado crea un conteo `PROGRAMADO` para un almacén activo con fecha programada y clase `CICLICO` o `GENERAL`; el servidor asigna folio y actor. Los productos se agregan durante la captura mediante identificador de producto y cantidad contada no negativa; no se incluyen productos inactivos nuevos.
2. Al capturar cada línea se bloquea primero el encabezado del conteo para serializar captura, cierre y cancelación; en esa transacción se bloquea la fila de existencia si existe y se toman **cantidad física**, `version` y el indicador `existencia_previa`. Sin fila: referencia cero, versión nula e indicador falso; no se crea existencia por contar. `diferencia = cantidad_contada − cantidad_sistema`, sin redondeos intermedios y con máximo tres decimales. La primera línea pasa el encabezado a `EN_CAPTURA`.
3. Durante `EN_CAPTURA`, volver a enviar la línea sustituye la captura y renueva su referencia; la diferencia anterior no es un historial definitivo. Al cerrar, exigir al menos una línea capturada y congelar cantidades, versiones y diferencias. Cerrar, incluso con diferencias, **no modifica stock**. Cancelar solo desde `PROGRAMADO` o `EN_CAPTURA`; por API y dominio, los documentos cerrados o cancelados no se editan ni eliminan; V10 también protege estas filas y sus detalles ante DML ordinario. Todas estas decisiones y su auditoría se probarán.
4. Un detalle de ajuste puede referir una línea de conteo **cerrado**, del mismo producto y almacén, con diferencia distinta de cero; sentido y cantidad deben coincidir exactamente con dicha diferencia. Puede haber solicitudes sucesivas si fueron rechazadas, pero como máximo una aplicación exitosa por línea. Antes de aplicar, bloquear la línea de conteo y comprobar bajo bloqueo que no se aplicó anteriormente.
5. Al aplicar un ajuste basado en conteo, comprobar la misma presencia/ausencia y versión registradas, bajo bloqueo; si cambió, responder `409` y requerir un nuevo conteo. Para una existencia ausente, crear la fila cero de forma segura bajo una única estrategia de bloqueo común; comprobar en la misma transacción que no se introdujo desde la captura y evitar que otra transacción la cree entre la comprobación y el cambio. **La ausencia de fila no es un bloqueo:** se requiere una exclusión transaccional estable por producto/almacén coordinada con las entradas de compra, o un mecanismo equivalente probado con carreras. Documentar y probar explícitamente su integración antes de elegirlo. No se acepta comparar solo la cantidad: puede volver al mismo valor tras movimientos. Todas las escrituras de existencias implementadas deben incrementar `version`, incluso si el saldo regresa al valor previo; SQL externo que no mantenga esa regla queda fuera de esta garantía y debe restringirse o controlarse en base de datos.

El conteo se cierra antes de crear la solicitud, para congelar la evidencia. El proceso UML refleja esta secuencia y la aplicación implementada.

## Ajustes y valoración

- **Estados:** `BORRADOR` no se usa en esta primera API; crear produce `PENDIENTE`. `PENDIENTE → APROBADO → APLICADO`, o `PENDIENTE → RECHAZADO`. No hay operación de cancelación de ajuste en el contrato inicial; `BORRADOR`/`CANCELADO` permanecen como estados objetivo no disponibles. Rechazo requiere motivo. El solicitante no aprueba su propio ajuste; un permiso de administrador no exceptúa esta regla. El aplicador debe tener su propio permiso; puede coincidir con el aprobador, sin una segregación adicional aprobada.
- **Entrada:** motivo obligatorio y al menos una línea por producto, cantidad positiva `numeric(14,3)`, sentido `POSITIVO` o `NEGATIVO`, y referencia de conteo opcional. Productos y almacén activos al crear y al aplicar. La creación no cambia stock y requiere clave de idempotencia global. Misma clave y contenido normalizado equivalente devuelve el documento original; contenido distinto devuelve `409`. Orden de líneas no altera equivalencia; duplicados se rechazan.
- **Costos:** para una entrada positiva se requiere costo unitario no negativo `numeric(16,4)` y se recalcula el promedio ponderado con la política de recepción existente (`HALF_UP`, cuatro decimales). Para una salida negativa el servidor toma el costo promedio vigente bajo bloqueo; el cliente no lo aporta y el promedio permanece igual, incluso si la cantidad queda cero. No puede reducirse la física por debajo de la reservada. No se producen movimientos con cantidad cero. Estas reglas son operativas del MVP y requieren validación contable antes de considerarse política contable definitiva.
- **Aplicación:** bloquear encabezado y detalles, después las fuentes de conteo en orden estable y las existencias en orden `(producto_id, almacen_id)` mediante una estrategia consistente con recepción; validar estado, cantidad, versión, costo y reserva dentro de la transacción. Una línea aplicada genera exactamente un movimiento con clave estable `AJUSTE:<idDetalle>` y referencia de fuente; modificar existencias, registrar movimientos, estado `APLICADO` y auditoría se confirma o revierte junto. Repetir aplicación exitosa retorna el documento sin crear otro movimiento. Dos solicitudes concurrentes deben serializarse; ninguna ruta pública permite escribir stock arbitrariamente.
- Si el mecanismo de bloqueo propuesto no puede coexistir con la secuencia de bloqueos de recepción sin interbloqueos, la parte 2/8 debe definir un orden común y demostrarlo en PostgreSQL con transacciones reales. Una excepción revertida no implica que el diseño de concurrencia esté correcto por sí sola.

## Alertas, informes y contratos

Para este incremento la alerta es **calculada**, sin crear tabla `alertas_inventario`, registros históricos ni notificaciones automáticas. Se listan productos activos con `punto_reorden > 0` cuyo disponible global de almacenes activos sea **menor o igual** al umbral; producto sin existencias contribuye cero, y un producto sin almacenes activos también tiene disponible global cero. El umbral cero deshabilita esta alerta para el producto. Se devuelven producto, umbral, cantidad global y cantidad de reorden configurada; una alerta no genera una requisición u orden. Este criterio de umbral cero y comparación inclusiva es una convención MVP propuesta; el SRS no la fija. Si se requiere alertar con umbral cero o por almacén, debe cambiarse la política y el contrato antes de implementar.

La ruta implementada es `GET /inventory/low-stock-alerts`: consulta operativa global. La ruta `/reports/inventory/low-stock` del contrato previo sigue `design-only`; su filtro `warehouseId` no representa la misma semántica global y debe redefinirse antes de implementarla. No se ofrecen dos APIs supuestamente equivalentes con criterios diferentes. `GET /inventory/stock` existente no cambia. Rotación, obsolescencia y ABC conservan estado de diseño hasta resolver fórmula, fuentes, período y alcance; no se presentan como cero cuando faltan salidas de Ventas.

## Permisos, errores y auditoría propuestos

Permisos registrados por V9; lectura de movimientos, conteos, ajustes y alertas ya aplican sus permisos: `INVENTARIO_MOVIMIENTOS_LEER`, `INVENTARIO_CONTEOS_LEER`, `INVENTARIO_CONTEOS_ADMIN`, `INVENTARIO_AJUSTES_LEER`, `INVENTARIO_AJUSTES_REGISTRAR`, `INVENTARIO_AJUSTES_APROBAR`, `INVENTARIO_AJUSTES_APLICAR` e `INVENTARIO_ALERTAS_LEER`. Se asignan mediante migración al rol administrador existente, sin usuarios ni credenciales; los roles operativos se configuran explícitamente. No se concede escritura libre de existencias. La autorización se aplica en backend y el actor se obtiene de la sesión.

Reutilizar `ApiError` (`status`, `code`, `message`) y `PageResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`): 400 entrada inválida, 401 sin autenticación, 403 permiso insuficiente, 404 recurso inexistente, 409 clave incompatible/estado/versión/saldo; 201 creación y 200 reintento equivalente/consultas/transiciones. Eventos de éxito de cambios críticos se registran en su transacción sin secretos. La denegación actual es de mejor esfuerzo; CA-06 exige verificar su registro en condiciones normales y documentar el comportamiento ante falla del subsistema de auditoría, sin afirmar garantía absoluta.

## Diseño de migración posterior a V8

V9 implementa el esquema descrito en el [registro de migraciones](SPRINT_2_MIGRACIONES.md). No editar migraciones anteriores. Las columnas adicionales del diseño se incorporaron a **ambas** copias del DBML al implementar V9; la aplicación de ajustes ya implementa reglas adicionales en la transacción:

| Tabla | Diseño propuesto y restricciones |
|---|---|
| `conteos_inventario` | Cabecera del DBML; `folio` único, FK a almacén y usuario, fecha, clase/estado con CHECK e índices por almacén/estado/fecha. Secuencia para folios sin promesa de consecutividad. |
| `detalles_conteo` | FK conteo/producto, par único, cantidades `numeric(14,3)` y diferencia con signo; añadir `existencia_previa boolean` y `version_existencia int` nullable con CHECK coherente. La versión capturada es evidencia, no FK. |
| `ajustes_inventario` | Cabecera DBML y clave global única; agregar `motivo_rechazo text` y tiempos de aprobación/rechazo si se necesitan para trazabilidad. FK a almacén/actores y CHECK de estados/actores/motivos. Secuencia de folios propia. |
| `detalles_ajuste_inventario` | FK al ajuste, producto y opcional detalle de conteo, producto único por ajuste, sentido, cantidad positiva y costo `numeric(16,4)` nullable hasta aplicación para salida negativa; snapshot del costo aplicado. Integridad de almacén/producto entre tablas en aplicación y, donde sea viable, constraints/FK compuestas. |
| `movimientos_inventario` | Conservar recepción y sus datos; hacer nullable `detalle_recepcion_id`, añadir `detalle_ajuste_id` FK única, y CHECK mutuamente excluyente de fuente y parejas válidas `ENTRADA/RECEPCION_COMPRA`, `ENTRADA/AJUSTE`, `SALIDA/AJUSTE`. No anticipar fuentes sin tablas. Conservar `clave_operacion` única y consultar restricciones reales antes de reemplazarlas. |
| `alertas_inventario` | No se crea para alerta calculada. Permanece en DBML como objetivo. |

La aplicación por detalle de conteo requiere exclusión bajo bloqueo y comprobación de aplicación previa, no una restricción UNIQUE indiscriminada que impida volver a solicitar un ajuste rechazado. V10 protege el kardex y documentos finales con triggers; el dueño/superusuario puede modificar tales controles. Migración limpia y V8→nueva con datos, CHECK/FK/UNIQUE, recepción histórica, y `ddl-auto=validate` son obligatorias. Evaluar bloqueos de ALTER y respaldos antes de aplicarla a entornos compartidos.

## Patrones y programación funcional

Se prevé reutilizar Repository para persistencia y funciones puras para diferencia y valoración; no se añade Factory/Strategy/Observer solo para cubrir la rúbrica. Documentar clases, métodos y pruebas en [patrones y programación funcional](../PATRONES_Y_PROGRAMACION_FUNCIONAL.md) cuando existan. Los métodos transaccionales, repositorios y auditoría producen efectos y no se presentan como funciones puras.

## Parte 3 — dominio y persistencia implementados

`ConteoInventario` administra las transiciones `PROGRAMADO → EN_CAPTURA → CERRADO` y cancelación previa al cierre. `capturar` crea o recaptura una línea por producto; `DetalleConteo` conserva el valor de sistema, cantidad contada, diferencia y referencia de versión, y valida que una referencia ausente tenga cantidad y versión coherentes. El dominio no consulta ni crea existencias y no comprueba que la versión permanezca vigente.

`AjusteInventario` inicia `PENDIENTE`, conserva sus líneas y valida aprobación/rechazo por un actor distinto; no permite aprobar sin detalles ni modificar líneas tras salir de pendiente. Las líneas exigen cantidad positiva, costo para ajustes positivos y ausencia de costo para negativos. `APLICADO` es un estado mapeado de V9, pero no hay método de dominio ni servicio para aplicarlo todavía: hacerlo requiere la transacción de inventario de la parte 8. No hay API, autorización ni auditoría de estas acciones en esta parte.

`CalculoConteo` normaliza y valida cantidades con tres decimales y costos con cuatro, y calcula la diferencia sin efectos ni redondeo; `InventarioDominioTest` comprueba rangos y transiciones. Los repositorios Spring Data exponen lectura/guardado acotados, sin operación de borrado. `InventarioDocumentosPersistenceIT` comprueba las entidades y ambos orígenes del movimiento con PostgreSQL; los movimientos insertados por SQL son datos sintéticos en una transacción de prueba, no prueba de aplicación del ajuste. Los servicios futuros deberán validar catálogos, permisos, vínculo con conteo, idempotencia, bloqueos y persistencia atómica con auditoría.

## Parte 4 — consulta de movimientos implementada

`GET /api/v1/inventory/movements` requiere `INVENTARIO_MOVIMIENTOS_LEER` tanto en controlador como en aplicación. Acepta `page` (desde 0), `size` (1–100), `productoId` y `almacenId` positivos y opcionales; devuelve una página de DTO con costo, actor, tipo, motivo y las dos posibles referencias de origen. El orden es fijo: fecha descendente y, en empate, id descendente. En la parte 4 el ajuste aún no se aplicaba; desde la parte 8 sí genera movimientos y el contrato de Inventario se completó en la parte 10.

La lectura usa cuatro consultas Spring Data parametrizadas según los filtros, sin exponer operaciones de modificación en el repositorio. El índice V7 `(producto_id, almacen_id, fecha DESC)` ayuda a búsquedas por producto y almacén; consultas globales, por almacén sin producto o desempate por id pueden requerir ordenación adicional. No se realizó una prueba de carga ni se garantiza que cumplan el objetivo de rendimiento: medir con datos representativos antes de añadir índices o afirmarlo. La paginación por offset es determinista para un conjunto sin cambios; inserciones concurrentes entre páginas pueden desplazar resultados.

## Parte 5 — conteos implementados

`ConteoInventarioService` ofrece creación, consulta, lista, captura/recaptura, cierre y cancelación. Los permisos `INVENTARIO_CONTEOS_ADMIN` y `INVENTARIO_CONTEOS_LEER` se comprueban en controlador y servicio. El servidor genera folios `CI-` con doce dígitos desde la secuencia V9; saltos por rollback son válidos. Se valida que el almacén exista y esté activo al crear y capturar, y que el producto esté activo al capturar. Se rechazan propiedades desconocidas de los cuerpos HTTP.

Cada modificación bloquea primero el encabezado; la captura bloquea la existencia si ya existe y toma cantidad y versión en la misma transacción. Si no hay fila, registra cero y versión nula sin crear stock. Cerrar exige una línea y congela el conteo; cancelar solo es posible antes del cierre. La recaptura sustituye la línea y su referencia, por lo que reintentarla después de un movimiento puede registrar una nueva fotografía. Repetir cierre o cancelación devuelve `409`. La consulta lista usa paginación por id descendente y carga las líneas de la página en una consulta adicional.

Los eventos de creación, primera captura, captura de línea, cierre y cancelación se insertan en la transacción de negocio. Los campos auditados contienen nombres y estados, no cantidades ni cuerpos de solicitud. `ConteoInventarioIT` prueba permisos, validaciones, estados, auditoría, conservación de stock, dos capturas concurrentes, carrera captura/cierre, lectura de una existencia bloqueada y rollback de la creación ante fallo real de auditoría. En la parte 5 aún faltaba verificar la vigencia de una referencia ausente y coordinarla con recepciones; esta garantía se implementó en la parte 8, descrita abajo.

## Parte 6 — solicitudes de ajuste implementadas

`AjusteInventarioService` y su controlador registran, listan y consultan solicitudes `PENDIENTE` con permisos separados de registro y lectura. El servidor genera `AI-` y doce dígitos mediante la secuencia V9. Solicitud y líneas se guardan con el evento `INVENTORY_ADJUSTMENT_CREATED` en una transacción; no se registra el cuerpo ni la clave en auditoría y no se toca stock ni kardex.

Se normalizan espacios extremos de clave y motivo; un motivo de línea que solo contiene espacios equivale a ausente, mientras que una cadena vacía se rechaza por validación. La clave global se serializa con `pg_advisory_xact_lock` transaccional; la base conserva la restricción única. Los reintentos comparan almacén, motivo, producto, tipo, cantidades, costo positivo, referencia y motivo de línea, sin importar el orden y comparando decimales por valor. El costo negativo es asignación futura del servidor, no parte del contenido de entrada. Una repetición equivalente devuelve el documento aun si luego se desactivó un catálogo; diferente devuelve `409`.

Para una solicitud nueva se bloquean en modo compartido el almacén y los productos activos. Los detalles de conteo, cuando se aportan, se leen bajo bloqueo compartido en orden estable y deben pertenecer a un conteo cerrado del mismo producto y almacén, con diferencia no nula y sentido/cantidad exactos. Puede existir más de una solicitud para el mismo detalle; impedir más de una **aplicación** y comprobar vigencia contra movimientos posteriores requieren la transacción de la parte 8. No se aprueba ni aplica en esta parte.

## Parte 7 — aprobación y rechazo implementados

Los endpoints de aprobación y rechazo exigen `INVENTARIO_AJUSTES_APROBAR` en el controlador y en el servicio. Se bloquea el encabezado antes de comprobar el estado y se toma el usuario desde la sesión; `AjusteInventario` exige un actor distinto del solicitante y estado `PENDIENTE`. El rechazo exige un motivo no vacío, que se guarda en la solicitud pero no se copia a la auditoría. La fecha y el actor quedan persistidos y expuestos mediante `autorizadoEn` y `autorizadoPorUsuarioId`.

La transición y el evento `INVENTORY_ADJUSTMENT_APPROVED` o `INVENTORY_ADJUSTMENT_REJECTED` se confirman en la misma transacción. Un permiso insuficiente responde `403` y registra la denegación según el mecanismo existente de mejor esfuerzo. Autorizar una solicitud propia, transitar desde otro estado o repetir la transición responde `409`. En una carrera aprobar/rechazar, el bloqueo permite una sola transición y un solo evento de éxito. Estas operaciones no modifican stock ni crean movimientos.

`AutorizarAjusteIT` comprueba permisos y registro de denegación, segregación, motivo, respuesta y actor/fecha, transiciones repetidas, carrera y rollback cuando PostgreSQL rechaza el evento de auditoría. En la parte 7 el caso CA-06 sobre aplicación aún estaba pendiente; se comprobó en la parte 8.


## Parte 8 — aplicación transaccional implementada

`POST /api/v1/inventory/adjustments/{id}/apply` exige `INVENTARIO_AJUSTES_APLICAR`; solo un ajuste aprobado con detalles puede aplicarse. El encabezado se bloquea y las líneas se procesan en orden de producto. Se revalidan producto, almacén, fuente y conteo cerrado; la línea de conteo se bloquea para impedir que dos solicitudes distintas la apliquen. Un reintento de un ajuste ya aplicado devuelve el mismo documento sin duplicar movimientos ni auditoría.

Recepción y ajuste comparten la estrategia de primera existencia: `INSERT ... ON CONFLICT DO NOTHING` sobre la unicidad producto/almacén, seguido de bloqueo de la fila. El resultado de inserción distingue una ausencia vigente de una fila creada después del conteo; para una existencia previa se compara su versión bajo bloqueo. Un cambio, aunque el saldo vuelva a ser el mismo, produce `409` y requiere un nuevo conteo. Cada escritura implementada incrementa la versión; escrituras SQL externas que omitan la regla quedan fuera de la garantía.

Las entradas usan el cálculo puro de costo promedio ponderado existente; las salidas conservan el promedio y registran ese costo en la línea y el movimiento. Se impide bajar del stock reservado y se validan los límites numéricos. Existencias, movimientos con fuente y clave `AJUSTE:<detalleId>`, costos, estado `APLICADO` y evento `INVENTORY_ADJUSTMENT_APPLIED` se confirman o revierten juntos. Una comprobación antes del commit impide que el servicio interno confirme un movimiento de ajuste sin estado y costo coherentes. En ese momento quedaba pendiente la protección ante SQL directo, abordada en la parte 9; el propietario o administrador puede eludir los controles.


## Parte 9 — historial e integridad implementados

V10 impide `UPDATE`, `DELETE` y `TRUNCATE` de movimientos, y protege encabezados y detalles de ajustes `APLICADO` y conteos `CERRADO` o `CANCELADO`. Los detalles no pueden agregarse, modificarse, borrarse ni trasladarse a o desde un documento final. Los triggers de detalle bloquean ambos encabezados en orden de id y se coordinan con la finalización; una carrera espera o una transacción es abortada si PostgreSQL detecta interbloqueo. Los documentos no finales conservan sus operaciones vigentes.

Al insertar un movimiento, otro trigger comprueba contra su fuente producto, almacén, cantidad, sentido y costo de entrada; para salida de ajuste admite el costo aún no fijado y compara el costo cuando ya existe. En recepción también comprueba que la línea de orden pertenezca a la misma orden. Las claves foráneas, exclusión y unicidad de fuente siguen vigentes. Estas comprobaciones no ejecutan el flujo de negocio ni prueban por sí solas que el stock, el estado y la auditoría se hayan confirmado juntos: para ello debe usarse la API. Tampoco validan retrospectivamente datos anteriores a V10. La integridad frente a cambios directos posteriores en documentos de recepción y detalles de orden de compra no está cubierta por estos triggers.

El rol de ejecución necesita privilegios para leer y bloquear las filas fuente y padres, además de los permisos de DML que usa la aplicación. En despliegue se debe separar el rol dueño/migrador del rol de ejecución y conceder solo los permisos necesarios; V10 no crea ni configura roles de producción. Los triggers se aplican al DML ordinario aun si el rol tiene `UPDATE`, `DELETE` o `TRUNCATE`, pero un propietario o administrador puede desactivarlos, alterarlos o modificar funciones; operaciones como `TRUNCATE`, cambio de modo de replicación y SQL administrativo deben estar restringidas. Las reversas y políticas de mantenimiento siguen pendientes.


## Parte 10 — alertas globales calculadas

`GET /api/v1/inventory/low-stock-alerts` requiere `INVENTARIO_ALERTAS_LEER` tanto en el controlador como en el servicio. Aplica la convención MVP definida arriba: suma `cantidad_fisica - cantidad_reservada` solo en almacenes activos, compara con `punto_reorden` inclusivo y excluye umbrales cero y productos inactivos. Un producto sin existencias o sin almacenes activos aporta cero. `cantidadReorden` es la cantidad configurada en el producto, no la diferencia contra el umbral ni una orden automática.

Admite `productoId` positivo opcional, `page` desde cero y `size` entre 1 y 100 (por defecto 0 y 20). Devuelve `content`, `page`, `size`, `totalElements` y `totalPages`, con orden fijo por id de producto ascendente. Producto inexistente o no alertado devuelve página vacía con `200`; una página fuera de rango conserva el total de coincidencias. Una sola consulta SQL obtiene página y total en la misma instantánea PostgreSQL, incluso para una página vacía. Entre solicitudes distintas los saldos pueden cambiar y la paginación por desplazamiento puede mover resultados. La suma usa `numeric` de PostgreSQL sin limitarla a la precisión de una fila. La consulta materializa las coincidencias para obtener el total coherente; su coste y los objetivos de tiempo/concurrencia no se han medido con volúmenes representativos.

La operación es de lectura: no almacena alertas, no crea movimientos, requisiciones, órdenes ni notificaciones. `AlertaStockIT` verifica umbral, ausencia, reservas, estados, cifras grandes, cambios de stock, filtros, paginación y permisos; no es una prueba de carga. RF-INV-07 queda parcial porque rotación y obsolescencia siguen pendientes. El contrato de reportería sigue siendo diseño y conserva una semántica distinta por almacén.


## Parte 11 — informes y medición de cobertura

La alerta de stock es el único alcance de RF-INV-07 disponible. La revisión de [fuentes y decisiones pendientes](../fase-2/reporteria/COMPONENTE_REPORTERIA.md#revisión-de-fuentes-del-sprint-2-parte-11) concluyó que aún no existen despachos de Ventas, devoluciones ni reversas en el kardex, y que faltan fórmula, ventana, zona horaria, política de productos sin movimientos y permisos de reportería. No se implementaron los endpoints de rotación o productos sin movimiento y el [OpenAPI de Reportería](../api/openapi-reporteria.yaml) sigue `design-only`. La ausencia de movimientos registrados no se interpreta como ausencia de actividad ni como rotación cero. No se cambiaron esquema ni UML.

El perfil opcional `coverage` de JaCoCo 0.8.12 instrumenta por separado Surefire y Failsafe y combina ambos archivos antes de producir el tercer informe. Sin exclusiones configuradas de clases de producción y con Java 21/PostgreSQL de Testcontainers, la ejecución completa del 9 nov. 2026 produjo los siguientes contadores de **líneas de código Java**:

| Suite | Líneas cubiertas / líneas totales | Cobertura | Ramas cubiertas / ramas totales |
|---|---:|---:|---:|
| Surefire (unitarias, contrato y otras aisladas) | 290 / 2077 | 13,96 % | 133 / 1125 (11,82 %) |
| Failsafe (integración) | 1967 / 2077 | 94,70 % | 768 / 1125 (68,27 %) |
| Combinada | 1981 / 2077 | 95,38 % | 792 / 1125 (70,40 %) |

Surefire incluye pruebas de contrato, de modo que 13,96 % **no es una medición exclusiva de unitarias**. Es inferior al objetivo mínimo del 70 % de cobertura unitaria de RNF-12 y no acredita su cumplimiento; la cifra combinada tampoco lo acredita. En `inventario/application`, Surefire cubrió 0 de 269 líneas mientras Failsafe cubrió 264: conviene añadir pruebas unitarias significativas de reglas y coordinación, sin reemplazar las pruebas PostgreSQL. JaCoCo mide ejecución, no calidad de aserciones; no mide SQL de Flyway/triggers, infraestructura, frontend, rendimiento ni cobertura funcional de requisitos. El perfil no impone un umbral automático y la meta sigue pendiente.

Los comandos, rutas y condiciones para repetir la medición están en el [README](../../README.md#ejecutar-y-verificar-el-backend). Los informes son artefactos generados bajo `target/` y no deben incluirse en Git.


## Parte 12 — integración y trazabilidad

`FlujoCompraInventarioIT` prepara usuarios y catálogos en PostgreSQL desechable y usa las API para crear, enviar a aprobación, aprobar y enviar una orden; registra y confirma una recepción 20/18/2; consulta stock, captura y cierra conteo, solicita y aprueba un ajuste negativo, aplica y consulta kardex y alerta. La misma prueba verifica identidades distintas para solicitar y aprobar, denegación y auditoría CA-06, fallo real de auditoría con rollback, reintento concurrente idempotente y trazabilidad de ambas fuentes. La prueba usa MockMvc con la aplicación Spring y PostgreSQL reales; no ejecuta navegador, React ni red HTTP real.

| Evidencia | Alcance comprobado | Límite |
|---|---|---|
| CA-01 / RF-COM-03/04/05 | Orden de 20, recepción 18 aceptadas y 2 rechazadas, saldo 2, stock 18; `FlujoCompraInventarioIT` y `ConfirmarRecepcionIT`. | No hay factura, devolución ni cancelación correctiva de recepción pendiente. |
| RF-INV-01/02/05/06 | Stock, conteo de 16 sin escritura, ajuste aprobado de −2 y kardex con fuentes; rollback y reintento concurrente. | RF-INV-02 sigue parcial: no hay despachos, devoluciones ni reversas. |
| RF-INV-07 parcial | Con umbral global 17, alerta aparece con stock 0, desaparece con 18 y vuelve con 16. | Rotación, obsolescencia e informes por almacén siguen pendientes. |
| CA-06 | Usuario sin permiso recibe `403` y se registra denegación en condiciones normales; el stock no cambia. | Auditoría de denegaciones de mejor esfuerzo; autorización del frontend no está probada. |

Respecto a la [rúbrica final](../rubrica/rubrica_final.pdf), esto aporta evidencia acotada de integración de un proceso, integridad y pruebas, junto con los contratos y vistas documentados. No demuestra los cinco procesos integrados, los siete módulos, cobertura unitaria mínima, calidad de frontend ni carga. Los patrones y funciones reales se explican en [su documento](../PATRONES_Y_PROGRAMACION_FUNCIONAL.md); no se suman patrones o componentes por existir una prueba.
