# Componente de reportería — diseño y estado de implementación

## Estado y trazabilidad

Este documento conserva el diseño de lectura de RF-INV-07, RF-CON-07 y RF-REP-01 a RF-REP-05. V1–V25 incluyen fuentes de Compras, Inventario, Ventas y Contabilidad y políticas de reportería. Nómina y las tablas opcionales de definiciones, ejecuciones y materializaciones no están implementadas. Están implementados balance de prueba, resultados, balance general, flujo indirecto condicionado, alerta global y los análisis descritos en el [diseño del sprint 5](../../fase-3/SPRINT_5_DISENO_REPORTERIA.md). El [contrato](../../api/openapi-reporteria.yaml) distingue rutas implementadas y fuera de alcance.

La primera implementación de cada consulta depende de que el módulo dueño cree y valide su fuente transaccional. No se crea una plataforma BI, consultas SQL configurables por usuarios ni una migración solo para almacenar definiciones o valores calculados. Las tablas `definiciones_reporte`, `ejecuciones_reporte`, `indicadores` y `valores_indicador` son diseño futuro, no prerrequisito de las consultas bajo demanda.

El sprint 5 incorpora consultas de facturación, alertas globales, resultado y dashboard por permiso; análisis de mínimo por almacén, inactividad, rotación, ABC, rentabilidad de clientes y margen operativo; además de exportación CSV/PDF/XLSX auditada. V25 mantiene mínimos explícitos y clasificaciones con vigencia. Las fórmulas y límites están en el [diseño actualizado](../../fase-3/SPRINT_5_DISENO_REPORTERIA.md).

## Inventario y contratos

La tabla siguiente conserva contratos de consulta; las rutas analíticas marcadas originalmente como propuestas se implementaron en el sprint 5 según OpenAPI y el diseño actualizado. Las respuestas entregan DTO, nunca entidades JPA. Las cantidades usan decimal; `null` o estado `NO_DISPONIBLE` distingue falta de evidencia de cero real. Los periodos y zonas se especifican en cada contrato activo.

| Requisito | Contrato | Entrada | Salida y fuente | Definición y estado |
|---|---|---|---|---|
| RF-REP-01 | `GET /reports/dashboard` | `from`, `to`, `periodId?` | Indicadores autorizados con código, fuente, periodo, estado y valor opcional. | Cuatro métricas iniciales implementadas con permiso por fuente; alcance global autorizado. |
| RF-INV-07 | `GET /reports/inventory/low-stock` | `warehouseId?`, `productId?`, página/orden | Producto, almacén, existencias, disponible y umbral explícito V25. | Solo pares configurados; no hereda `punto_reorden` global. |
| RF-INV-07 | `GET /reports/inventory/inactive-products` | `asOf`, `days`, `warehouseId?`, página/orden | Último movimiento confirmado por kardex y estado de registro. | Distingue `NUNCA_REGISTRADO` de `SIN_MOVIMIENTO`; no etiqueta obsoleto. |
| RF-INV-07 / RF-REP-03 | `GET /reports/inventory/turnover` | `from`, `to`, `warehouseId?`, página/orden | Unidades despachadas / existencia física media diaria. | Implementado; historia inconciliable o denominador cero devuelve `NO_DISPONIBLE`; sin devoluciones/reversas. |
| RF-REP-03 | `GET /reports/inventory/abc` | `from`, `to`, `warehouseId?`, página/orden | Costo de despachos confirmados, orden costo descendente/producto ascendente. | Implementado con cortes acumulados 80/95%; base no fiable devuelve `NO_DISPONIBLE`. |
| RF-REP-03 | `GET /reports/sales/customer-profitability` | `from`, `to`, `customerId?`, página/orden | Subtotal facturado neto de descuentos/impuestos menos costo conciliado de despachos. | Margen bruto implementado; excluye gastos indirectos y falla como no disponible si factura/despacho no concilian. |
| RF-REP-03 | `GET /reports/finance/operating-margin` | `periodId` | Resultado operativo / ingreso operativo × 100 de partidas clasificadas. | Clasificación explícita V25, vigencia y anulación auditada; no infiere datos históricos. |
| RF-CON-07 | `GET /reports/finance/trial-balance` | `periodId`, página/orden | Cuenta, debe, haber y saldo, con totales del periodo; partidas contabilizadas. | Convención de signo y presentación de saldo inicial. |
| RF-CON-07 | `GET /reports/finance/income-statement` | `periodId` | Líneas, subtotales y total del periodo; cuentas y partidas contabilizadas. | Mapeo de cuentas y tratamiento de contraasientos. |
| RF-CON-07 | `GET /reports/finance/balance-sheet` | `periodId` | Líneas de activo, pasivo y capital; saldos contables válidos. | Fecha de corte y cuentas de resultados al cierre. |
| RF-CON-07 | `GET /reports/finance/cash-flow` | `periodId` | Líneas y total o `NO_DISPONIBLE`. | Flujo indirecto implementado con clasificación explícita V24 y límites del cierre de Contabilidad. |
| RF-REP-05 | `GET /reports/payroll/department-costs` | `payrollPeriodId`, `departmentId?`, página/orden | Departamento y costo según nómina. | No implementado; nómina queda fuera del sprint por decisión del usuario. |

Los indicadores del dashboard y análisis de inventario, ventas y finanzas aprobados están implementados. `GET /reports/export` genera CSV/PDF/XLSX de la página pedida, conserva filtros y disponibilidad, y registra auditoría; el dashboard conserva su CSV dedicado. Informes de compras adicionales permanecen sujetos a definición. Nómina está fuera del sprint.

## Separación de lectura, seguridad y coherencia

Cada módulo conserva las operaciones de escritura y la interpretación de sus estados. Reportería usa servicios de consulta de solo lectura con criterios validados y proyecciones SQL/JPQL específicas; no llama comandos ni modifica entidades. El controlador autoriza el reporte y el servicio vuelve a aplicar cualquier alcance de filas del actor antes de agregar, contar o paginar. Una fila ajena no debe influir siquiera en un total. La lista de permisos concreta se definirá junto con los módulos fuente; no se asigna acceso financiero o salarial automáticamente al rol administrador. Autenticación ausente: `401`; permiso insuficiente: `403`; filtro inválido: `400`, con `ApiError` existente. No se registra el contenido de salarios ni filtros sensibles en auditoría.

Para resultados paginados se reutilizan `PageRequests` (página 0+, tamaño 1–100 y orden permitido) y `PageResponse`. Un `id` estable desempata el orden; la cuenta total usa exactamente los mismos filtros y restricciones de acceso. Se proyectan columnas escalares y se agregan en la base, sin cargar colecciones JPA ni consultas por fila. Los totales y detalles de una misma respuesta se consultarán con una instantánea coherente; se verificará el aislamiento concreto sobre PostgreSQL antes de escoger transacción de solo lectura o una sola sentencia con ventanas/CTE. Las reversiones se interpretan según los asientos/movimientos válidos y nunca se ocultan por conveniencia.

## Consultas e índices

Los predicados usan parámetros enlazados, rangos indexables y listas cerradas de campos de orden; nunca SQL recibido del cliente. Candidatos de consulta: agregación de `partidas_asiento` unida a `asientos_contables` por cuenta/periodo/estado, `detalles_nomina` unida a `nominas` por periodo/departamento/estado, y agregación de `movimientos_inventario` por producto/almacén/fecha/tipo. El estado permitido de cada documento es parte de la definición del reporte, no un filtro libre que permita sumar borradores a cifras oficiales.

Antes de crear índices se medirán consultas reales con `EXPLAIN (ANALYZE, BUFFERS)` sobre datos sintéticos representativos, verificando selectividad, plan, memoria, filas leídas y costo de escritura del índice. Se considerarán claves de unión y filtros por periodo/estado; no se añade una migración especulativa. No se usa caché ni tabla de indicadores precalculados mientras no exista evidencia de necesidad y política de invalidación.

## Plan de pruebas y medición

1. PostgreSQL desechable con Flyway y datos conocidos: totales de partidas y nómina, devoluciones/reversiones, límite de periodos, estado borrador/cancelado, cero verdadero frente a `NO_DISPONIBLE` y lista vacía.
2. API real con dos identidades y permisos distintos: `401`, `403`, ámbito de filas, totales sin fuga, `400` para rangos/identificadores/orden inválidos, página fuera de rango y orden estable.
3. Comparar cada agregado con sus filas fuente confirmadas dentro de la misma instantánea; comprobar que la consulta no cambia estado ni datos.
4. Medir latencia de extremo a extremo y SQL con volumen, distribución, hardware y concurrencia registrados. RNF-03 exige p95 menor a 5 s para reportes estándar en condiciones definidas; los analíticos complejos se informan aparte. Se evaluará también el objetivo de 200 usuarios concurrentes. No hay medición de reportería todavía.

El comando base de verificación del backend, cuando existan implementaciones y Docker disponible, es `cd erp-backend && ./mvnw -o verify`. Las pruebas de carga necesitarán escenario y conjunto de datos reproducibles antes de afirmar cumplimiento.

## Decisiones abiertas antes de implementar resultados

Las fórmulas aprobadas de rotación, ABC, rentabilidad bruta y margen están implementadas en backend. «Sin movimiento» no se etiqueta como obsoleto; productos nunca registrados se distinguen. Costos de nómina quedan fuera por decisión del usuario. V24 clasifica flujos y V25 habilita clasificación operativa explícita para el margen; ninguna infiere clasificaciones históricas. Consultar [diseño del sprint 5](../../fase-3/SPRINT_5_DISENO_REPORTERIA.md) para el estado por ruta.


## Revisión de fuentes del sprint 2, parte 11

En la revisión histórica del sprint 2 el backend registraba entradas por recepción y movimientos por ajuste. Desde el sprint 3 registra despachos de ventas; devoluciones y reversas siguen pendientes y no se reconstruye toda la historia previa al inicio del sistema. La ausencia de esos datos no demuestra inactividad. La consulta global de stock bajo difiere del reporte por almacén, que solo aplica mínimos explícitos V25.

La consulta de **rotación** usa unidades despachadas sobre existencia física media diaria; ante cobertura incompleta, desajuste con existencia actual o denominador cero responde `NO_DISPONIBLE`. Para **productos sin movimiento**, cuenta movimientos confirmados de kardex, distingue nunca registrados y no afirma obsolescencia. Para **rentabilidad**, calcula bruto facturado menos costo conciliado de despachos; para **margen**, usa clasificación explícita por vigencia. Esas reglas están aprobadas e implementadas; no incluyen devoluciones/reversas ni historia anterior no conciliada.
