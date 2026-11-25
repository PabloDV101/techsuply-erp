# Sprint 5 — definiciones de Reportería y dashboard

**Estado (24 nov. 2026):** el alcance backend aprobado del sprint está implementado y verificado; este documento conserva las definiciones y límites. V25 incorpora mínimos por almacén y clasificación explícita de margen. La [guía de frontend](../api/guia-sprint-5-reporteria.md) entrega el contrato; la integración en navegador sigue pendiente. Nómina permanece fuera del sprint.

La parte 3 incorporó `PrepararDashboardService`, `CriteriosDashboard` e `IndicadorDashboard` como base interna: identifica las autoridades actuales y valida rango y periodo sin consultar datos. La selección autorizada se construye en cada petición, sin aceptar permisos del cliente ni reutilizarlos para otra sesión; cada consulta de dominio conserva su control. La parte 8 agregó la composición y la API.

La parte 4 añade `GET /api/v1/reports/purchases/invoiced` para consultar la suma de Compras; usa la fuente y el permiso de la tabla siguiente. La respuesta contiene `from`, `to` y `subtotal`, sin impuestos; la fecha de emisión se compara como `date` con límites inclusivos y la suma vacía devuelve cero. Es una consulta global de facturas validadas y no prueba pagos ni completitud histórica. El servicio aplica autorización y validación también cuando se invoca desde backend; el dashboard lo reutiliza.

La parte 5 añade `GET /api/v1/reports/sales/invoiced` para facturas comerciales emitidas. Responde `from`, `to` y `subtotal`, suma sin impuestos los subtotales que ya incluyen descuentos y usa `fecha_emision` inclusiva; requiere `VENTAS_FACTURAS_LEER` también en el servicio. Incluye facturas con contabilización pendiente y no prueba cobro ni CFDI. Pedidos y despachos sin factura, pedidos cancelados y borradores no forman parte de la suma. La ruta no reconstruye ventas históricas ajenas al sistema; el dashboard reutiliza su servicio.

La parte 6 añade `GET /api/v1/reports/inventory/low-stock-summary` con `INVENTARIO_ALERTAS_LEER`, que retorna `productosEnAlerta` y `asOf`. La API analítica implementa alertas por almacén solo cuando existe un mínimo explícito V25; no copia `productos.punto_reorden`. Productos sin movimiento distinguen `NUNCA_REGISTRADO` de `SIN_MOVIMIENTO` según movimientos confirmados. Rotación y ABC usan kardex conciliado; sin cobertura fiable se devuelve `NO_DISPONIBLE`. Las operaciones temporales observan `America/Mexico_City`. Sigue sin existir reconstrucción completa anterior al sistema ni soporte de devoluciones/reversas.

La parte 7 añade `GET /api/v1/reports/finance/result-summary?periodId=...`, protegido por `CONTABILIDAD_REPORTES_LEER` en controlador y servicio. El servicio llama al estado de resultados existente y proyecta su rubro `RESULTADO` y fechas, conservando la transacción de solo lectura con instantánea `REPEATABLE_READ`; no recalcula partidas ni cambia el flujo indirecto. El periodo es obligatorio para esta consulta independiente; el dashboard devuelve `NO_DISPONIBLE` si se omite. El margen operativo se calcula aparte, mediante clasificaciones explícitas V25, y no infiere históricos.

## Dashboard inicial: RF-REP-01 y RF-REP-04

`GET /api/v1/reports/dashboard` acepta `from` y `to` inclusivos para facturas y `periodId` opcional para finanzas. El periodo contable se obtiene por su identificador y puede diferir del intervalo de facturas: sus fechas se devuelven por indicador. Inventario es una instantánea actual, no una cifra histórica. El intervalo de facturas usa columnas `date`, sin conversión de zona. El servicio selecciona los permisos del actor y llama solo a fuentes autorizadas; cada servicio valida también su propio permiso. Sin ninguno de los cuatro permisos devuelve `403`, y sin autenticación `401`. Un `periodId` inexistente aportado por un usuario con permiso financiero produce `404`. La ausencia de `periodId` produce indicador financiero `NO_DISPONIBLE` sin valor.

La composición corre en una transacción de solo lectura `REPEATABLE_READ`. Una primera sentencia establece la instantánea compartida; su `statement_timestamp()` se entrega como `asOf` del indicador de inventario. El resumen independiente de Inventario conserva su propio `asOf`: es el inicio de su sentencia, por lo que puede diferir del instante del dashboard. Una falla de cualquier fuente aborta la respuesta completa y no se transforma en cero.

| Código | Cálculo, fuente y estado | Periodo / unidad | Permiso | Exclusiones y límites |
|---|---|---|---|---|
| `COMPRAS_FACTURADO_SIN_IMPUESTOS` | Suma de `facturas_proveedor.subtotal` con estado `VALIDADA`. | `fecha_emision` entre `from` y `to`; importe decimal a dos decimales. | `COMPRAS_FACTURAS_PROVEEDOR_LEER` | Excluye registradas e impuestos; no mide pagos ni todas las compras. Fecha de emisión y validación pueden diferir. |
| `VENTAS_FACTURADO_SIN_IMPUESTOS` | Suma de `facturas_venta.subtotal` con estado `EMITIDA`. | `fecha_emision` entre `from` y `to`; importe decimal a dos decimales. | `VENTAS_FACTURAS_LEER` | Excluye borradores e impuestos; factura comercial interna, no cobro ni CFDI. No depende de que su asiento se haya procesado. |
| `INVENTARIO_PRODUCTOS_ALERTA` | Contar los productos devueltos por la regla global de alerta: activos, umbral positivo, suma de físico menos reservado en almacenes activos menor o igual al umbral. | Instante de consulta; número entero de productos. | `INVENTARIO_ALERTAS_LEER` | No es stock por almacén ni representa un periodo histórico. Productos sin existencias cuentan con disponibilidad cero si cumplen la regla. |
| `FINANZAS_RESULTADO` | Rubro `RESULTADO` del servicio de estado de resultados: ingresos netos menos costos y gastos netos de partidas válidas, incluyendo contraasientos. | Fechas del `periodId` inclusivas; importe decimal a dos decimales. | `CONTABILIDAD_REPORTES_LEER` | Excluye borradores/cancelados; no equivale a margen operativo ni garantiza que todas las fuentes estén contabilizadas. |

Las sumas y recuentos vacíos producen cero cuando la consulta está disponible; `NO_DISPONIBLE` lleva valor nulo y motivo, nunca cero por falta de datos. La unidad es `IMPORTE` para montos y `PRODUCTOS` para el conteo. Los resultados describen registros existentes, sin afirmar completitud histórica o contabilización integral. Si falla una consulta, no convertir el error en cero. No se define moneda ni conversión cambiaria: el esquema no acredita política multimoneda; el contrato describe importes sin atribuirles un código monetario. No se introduce precisión ni redondeo adicional en la suma.

El usuario aprobó acceso global por permiso para estos indicadores operativos. Las consultas actuales revisadas usan permisos por operación y no evidencian un mecanismo general de asignación de filas; un filtro de almacén, departamento, vendedor o creador no se usa como control de acceso. Esta decisión no otorga acceso financiero ni salarial y no sustituye la autorización específica de otras API. El acceso financiero conserva su permiso existente. No se asignan permisos nuevos a roles en esta parte.

## Informes adicionales: matriz de decisiones y estado

Los análisis RF-REP-03/04 están implementados dentro de las fórmulas aprobadas y aplican permisos propios. Se conserva la distinción entre cero real y evidencia insuficiente. No hay permiso genérico de dashboard que sustituya los permisos de fuente.

| Informe | Fórmula implementada y límites |
|---|---|
| Rotación | Unidades despachadas / inventario físico medio diario; solo disponible si el kardex desde inicio concilia con existencia actual y nunca genera saldo negativo. |
| ABC | Costo de despachos confirmados; acumula en orden costo descendente/producto ascendente. Clase por acumulado previo: A <80 %, B <95 %, C restante. Base vacía/cero no da falsos resultados. |
| Rentabilidad por cliente | Subtotal de facturas emitidas (descuentos aplicados, sin impuestos) menos costo de kardex de despachos del mismo pedido; cantidad facturada/despachada debe conciliar. Es bruta, no utilidad neta. |
| Margen operativo | (ingresos operativos − costos − gastos) / ingresos operativos × 100. Requiere clasificación explícita de cuentas compatible con su tipo; no infiere historia. |
| Productos sin movimiento | Fecha corte local, movimientos registrados por almacén; diferencia `SIN_MOVIMIENTO` de `NUNCA_REGISTRADO`. No los llama obsoletos. |
| Mínimo por almacén | Tabla V25 por par producto/almacén; solamente explícitos, no copia el umbral global. |
| Costos de personal | Sin nómina. Fuera del sprint por decisión del usuario; RF-REP-05 no se declara implementado. |

`REPORTERIA_INVENTARIO_LEER` autoriza análisis de inventario; la configuración de mínimos requiere `REPORTERIA_MINIMOS_CONFIGURAR`. Rentabilidad necesita a la vez permiso comercial y financiero. Las clasificaciones requieren `CONTABILIDAD_MARGEN_CLASIFICAR`; el margen conserva `CONTABILIDAD_REPORTES_LEER`. Filtros por cliente/producto/almacén no sustituyen autorización de fila.
Las rutas analíticas anteriores y mínimos explícitos están implementadas. `GET /api/v1/reports/export` exporta CSV/PDF/XLSX de la página seleccionada de una consulta permitida, con filtros, metadatos y auditoría; CSV específico del dashboard se conserva. La exportación no implica descargar todas las páginas. Nómina no está implementada. Los cuatro reportes financieros ya implementados mantienen sus contratos y límites del [diseño contable](SPRINT_4_DISENO_CONTABILIDAD.md).

## Verificación y coordinación

Las pruebas HTTP/PostgreSQL prueban dashboard, informes analíticos, permisos, exportaciones reales CSV/PDF/XLSX, políticas, migración V24→V25 y ausencia de clasificación inferida. La regresión completa del cierre y sus comandos constan en la ruta por partes. No hay confirmación de integración en navegador ni medición de rendimiento representativa.

## Parte 9 — exportación CSV aprobada

El usuario aprobó exportar los indicadores actuales y diferir los análisis adicionales. `GET /api/v1/reports/dashboard/export.csv` recibe `from`, `to` y `periodId` opcional con las mismas reglas y permisos del dashboard. Descarga `dashboard.csv`, UTF-8, delimitador coma, líneas CRLF, encabezado y hasta cuatro filas. Las columnas son `requestedFrom,requestedTo,periodId,code,source,from,to,asOf,availability,value,unit,unavailabilityReason`. Las celdas nulas quedan vacías; el valor no disponible nunca se convierte en cero. Los importes usan punto decimal; los textos se entrecomillan y escapan comillas. No admite textos libres ni fórmulas del cliente.

La exportación reutiliza la consulta existente en una transacción `REPEATABLE_READ` que permite insertar el evento `DASHBOARD_CSV_GENERATED` en la auditoría existente. Si falla la consulta o el registro, no entrega el CSV. El evento conserva actor y fecha sin copiar los importes; acredita generación, no descarga recibida por el cliente. La respuesta usa `Cache-Control: no-store`. El GET JSON conserva su lectura sin registro de generación CSV.

El dashboard CSV reutiliza auditoría V4. Las políticas analíticas requieren V25 para mínimos por almacén y clasificación de margen; las exportaciones genéricas no persisten resultados y registran `REPORT_FILE_GENERATED`. Las tablas objetivo de ejecuciones e indicadores no se materializan. RF-REP-02/03 están cubiertos parcialmente en el alcance implementado; no se ofrece un motor arbitrario de reportes parametrizables. RF-REP-05 está fuera del sprint.

## Trazabilidad reconciliada — parte 10

Las pruebas citadas se ejecutaron en las partes 4–9; esta reconciliación documental no es una ejecución nueva del backend.

| Requisito | Implementación y evidencia existente | Límite |
|---|---|---|
| RF-REP-01 | DashboardController/Service; DashboardApiIT: indicadores, permisos parciales y snapshot concurrente. | Cuatro indicadores aprobados; navegador pendiente. |
| RF-REP-02 | Exportación de dashboard CSV y exportación genérica CSV/PDF/XLSX por página, filtro y permisos; pruebas focalizadas de parsers y auditoría. | No es generador arbitrario; página exportada limitada por paginación. |
| RF-REP-03 | Servicios de rotación, ABC, rentabilidad bruta de cliente y margen operativo; políticas de mínimo e historial V25; IT con fuentes reales. | Sin devoluciones/reversas ni historia anterior confiable; casos incompletos devuelven `NO_DISPONIBLE`. |
| RF-REP-04 / CA-08 | PrepararDashboardService y servicios de fuente protegidos; PrepararDashboardIT y DashboardApiIT prueban selección y denegación. | Sin fuente de nómina ni autorización salarial implementada; no acredita RF-REP-05. |
| Indicadores Compras/Ventas | ConfirmarRecepcionIT y FacturaApiIT: facturas reales y composición; pruebas OpenAPI de cada resumen. | No equivalen a pagos/cobros ni acreditan historia completa. |
| RF-INV-07 parcial | Alerta global y consultas protegidas por almacén con mínimos explícitos, inactividad, rotación y ABC; pruebas de inventario y analítica. | No se etiqueta obsolescencia; reconstrucción histórica incompleta y devoluciones/reversas pendientes. |
| RF-CON-07 parcial | ReportesContablesApiIT y ResultadoFinancieroOpenApiContractTest; resumen del resultado existente. | Flujo condicionado, sin margen operativo ni informes fiscales. |

El contrato de Reportería documenta endpoints implementados y nómina fuera de alcance; consultar `x-implementation-status` por operación. El modelo conserva 80 tablas objetivo/documentadas, no 80 tablas implementadas; Flyway V1–V25 determina el esquema ejecutable en la rama de sprint. El CSV utiliza eventos_auditoria y no las tablas objetivo de ejecuciones o materialización. La meta de cobertura unitaria, siete componentes reutilizables en tres contextos, portabilidad y rendimiento siguen sin acreditarse por esta documentación.
