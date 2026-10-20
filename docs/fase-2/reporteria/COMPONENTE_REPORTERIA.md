# Componente de reportería — diseño aprobado, implementación pendiente

## Estado y trazabilidad

Este documento concreta el diseño de lectura de RF-INV-07, RF-CON-07 y RF-REP-01 a RF-REP-05 del [SRS](../../SRS_TechSupply_Solutions.md). El contrato HTTP propuesto está en [`openapi-reporteria.yaml`](../../api/openapi-reporteria.yaml). No describe endpoints existentes: las migraciones ejecutables V1–V5 solo contienen seguridad, categorías, auditoría y notificaciones. Las tablas de compras, inventario, ventas, contabilidad, nómina y reportería del [DBML](../../diagrams/modelodatos/modelo.dbml) son objetivo. El catálogo de categorías sí implementa parte de RF-REP-02 (filtros, orden y página), sin cerrar el requisito de reportes.

La primera implementación de cada consulta depende de que el módulo dueño cree y valide su fuente transaccional. No se crea una plataforma BI, consultas SQL configurables por usuarios ni una migración solo para almacenar definiciones o valores calculados. Las tablas `definiciones_reporte`, `ejecuciones_reporte`, `indicadores` y `valores_indicador` son diseño futuro, no prerrequisito de las consultas bajo demanda.

## Inventario y contratos

Los identificadores siguientes nombran contratos propuestos, no rutas activas. Todas las respuestas entregan DTO, nunca entidades JPA. Las cantidades usan decimal; `null` o estado `NO_DISPONIBLE` distingue una métrica sin definición/fuente de un cero calculado. Periodos de fecha son inclusivos en la API; en columnas `timestamptz` se traducen a intervalo semiabierto `[inicio, día posterior a fin)` con zona de negocio acordada antes de implementar. No se presupone UTC para fechas contables (`date`).

| Requisito | Contrato propuesto | Entrada | Salida y fuente | Pendiente de definición |
|---|---|---|---|---|
| RF-REP-01 | `GET /reports/dashboard` | `from`, `to` | Lista de indicadores autorizados con código, periodo, fuente, estado y valor opcional. Consultas por dominio. | Selección, fórmulas y unidad de indicadores de compras, ventas, inventario y finanzas. |
| RF-INV-07 | `GET /reports/inventory/low-stock` | `warehouseId?`, `productId?`, página/orden | Producto, almacén, existencia física, reservada, disponible y umbral; `existencias` y `productos.punto_reorden`. Instantánea de consulta, no periodo histórico. | Confirmar si `punto_reorden` equivale al stock mínimo del SRS, si se compara con física o disponible y si basta un umbral global por producto. |
| RF-INV-07 | `GET /reports/inventory/inactive-products` | `asOf`, `days`, `warehouseId?`, página/orden | Producto y última fecha de movimiento válido; `movimientos_inventario`. Incluye umbral aplicado. | Determinar si «sin movimiento» incluye productos nunca movidos y qué tipos cuentan; «obsoleto» requiere política adicional. |
| RF-INV-07 / RF-REP-03 | `GET /reports/inventory/turnover` | `from`, `to`, `warehouseId?`, página/orden | Producto, componentes de cálculo y valor o `NO_DISPONIBLE`; kardex y existencias/costos cuando exista historia suficiente. | Fórmula, costo medio inicial/final, devoluciones y movimientos incluidos. |
| RF-REP-03 | `GET /reports/inventory/abc` | `from`, `to`, `warehouseId?`, página/orden | Producto, base del cálculo, porcentaje acumulado y clase o `NO_DISPONIBLE`; ventas/despachos válidos. | Base de valoración, umbrales A/B/C y tratamiento de devoluciones. |
| RF-REP-03 | `GET /reports/sales/customer-profitability` | `from`, `to`, `customerId?`, página/orden | Cliente, ingresos y costos aplicables, resultado o `NO_DISPONIBLE`; facturas y costos de despacho válidos. | Fórmula, impuestos, descuentos, devoluciones y costos indirectos. |
| RF-REP-03 | `GET /reports/finance/operating-margin` | `periodId` | Componentes contables y valor o `NO_DISPONIBLE`; partidas de asientos contabilizados. | Fórmula y clasificación de cuentas operativas. |
| RF-CON-07 | `GET /reports/finance/trial-balance` | `periodId`, página/orden | Cuenta, debe, haber y saldo, con totales del periodo; partidas contabilizadas. | Convención de signo y presentación de saldo inicial. |
| RF-CON-07 | `GET /reports/finance/income-statement` | `periodId` | Líneas, subtotales y total del periodo; cuentas y partidas contabilizadas. | Mapeo de cuentas y tratamiento de contraasientos. |
| RF-CON-07 | `GET /reports/finance/balance-sheet` | `periodId` | Líneas de activo, pasivo y capital; saldos contables válidos. | Fecha de corte y cuentas de resultados al cierre. |
| RF-CON-07 | `GET /reports/finance/cash-flow` | `periodId` | Líneas y total o `NO_DISPONIBLE`. | Método directo/indirecto y identificación confiable de flujos de efectivo; el DBML actual no lo resuelve por sí solo. |
| RF-REP-05 | `GET /reports/payroll/department-costs` | `payrollPeriodId`, `departmentId?`, página/orden | Departamento, percepciones, deducciones y costo definido, con origen de importes; `nominas` y `detalles_nomina`. | Qué conceptos integran «costo de personal» y qué estados de nómina son válidos. |

El SRS exige indicadores de compras y ventas en el dashboard, pero no identifica métricas particulares. Los diagramas también muestran «reporte de compras/ventas» sin contenido verificable. Se mantienen como alcance por definir; no se añade un informe genérico ni valores supuestos. PDF/CSV/XLSX están previstos condicionalmente por RF-REP-02: quedan fuera de esta etapa, sin rutas de exportación.

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

El SRS, sección 12.2, pide validar las fórmulas de rotación, productos obsoletos, rentabilidad y margen operativo. También faltan la selección de KPI, reglas de ABC, mapeo de estados financieros/flujo de efectivo, definición de costo laboral, zona de negocio y asignación de permisos por reporte. Hasta entonces esos valores se identifican como no disponibles; no se inventan fórmulas. La existencia de este contrato documental no habilita endpoints ni migraciones.
