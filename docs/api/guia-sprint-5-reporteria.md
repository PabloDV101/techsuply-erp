# Guía para frontend — Reportería y dashboard del sprint 5

**Estado al 23 nov. 2026:** el dashboard y su CSV, las facturaciones de proveedores y ventas, el resumen de resultado y cuatro consultas financieras, además de la alerta global y su resumen, existen en backend. Esta guía prepara la adaptación del cliente; no acredita coordinación con el compañero, pruebas en navegador ni despliegue. El contrato detallado está en [OpenAPI de Reportería](openapi-reporteria.yaml), y sus reglas y límites en el [diseño del sprint](../fase-3/SPRINT_5_DISENO_REPORTERIA.md).

## Análisis y administración de políticas

| Solicitud | Permiso | Definición |
|---|---|---|
| `/api/v1/reports/inventory/low-stock?warehouseId=1&productId=1&page=0&size=20` | `REPORTERIA_INVENTARIO_LEER` | Solo pares producto/almacén con mínimo explícito configurado. No hereda el mínimo global. |
| `/api/v1/reports/inventory/minimums?productId=1` | `REPORTERIA_INVENTARIO_LEER` o `REPORTERIA_MINIMOS_CONFIGURAR` | Lista umbrales asignados al producto. |
| `PUT /api/v1/reports/inventory/minimums` | `REPORTERIA_MINIMOS_CONFIGURAR` | JSON `{ "productId": 1, "warehouseId": 1, "minimum": 5 }`; operación idempotente y auditada. |
| `/api/v1/reports/inventory/inactive-products?asOf=2026-11-24&days=90&warehouseId=1` | `REPORTERIA_INVENTARIO_LEER` | Identifica `SIN_MOVIMIENTO` y `NUNCA_REGISTRADO`; no califica obsolescencia. |
| `/api/v1/reports/inventory/turnover?from=2026-11-01&to=2026-11-24&warehouseId=1` | `REPORTERIA_INVENTARIO_LEER` | Salidas en unidades / existencia física media diaria; `NO_DISPONIBLE` ante huecos del kardex o falta de conciliación. |
| `/api/v1/reports/inventory/abc?from=2026-11-01&to=2026-11-24` | `REPORTERIA_INVENTARIO_LEER` | Costo de despachos confirmados; acumulado anterior: A <80 %, B <95 %, resto C; desempate por producto. |
| `/api/v1/reports/sales/customer-profitability?from=...&to=...&customerId=1` | `VENTAS_FACTURAS_LEER` y `CONTABILIDAD_REPORTES_LEER` | Facturación neta de impuestos y descuentos menos costo de despachos conciliados. Rentabilidad bruta. |
| `/api/v1/reports/finance/operating-classifications?accountId=1` | `CONTABILIDAD_REPORTES_LEER` o `CONTABILIDAD_MARGEN_CLASIFICAR` | Historial de clasificación explícita. |
| `POST /api/v1/reports/finance/operating-classifications` | `CONTABILIDAD_MARGEN_CLASIFICAR` | Registra tipo operativo/no operativo y vigencia inclusiva; no clasifica el histórico automáticamente. |
| `POST /api/v1/reports/finance/operating-classifications/{id}/void` | `CONTABILIDAD_MARGEN_CLASIFICAR` | Anula con motivo, sin borrar historial. |
| `/api/v1/reports/finance/operating-margin?periodId=1` | `CONTABILIDAD_REPORTES_LEER` | (Ingresos operativos − costos − gastos) / ingresos × 100. Si falta clasificación o ingreso positivo, devuelve `NO_DISPONIBLE`. |

Paginación: `page` empieza en cero y `size` admite 1–100. `sort` y `direction` pasan por allowlist por reporte. Las fechas analíticas usan `America/Mexico_City`, límite inclusivo y máximo de diez años. Las políticas se auditan. V25 crea la tabla de mínimos por almacén y la clasificación versionada de margen; no asigna defaults a datos existentes.

## Exportación genérica

`GET /api/v1/reports/export?report=inventory/abc&format=xlsx&from=2026-11-01&to=2026-11-24&page=0&size=100` exporta la consulta fuente ya autorizada. `format`: `csv`, `pdf` o `xlsx`. Se exporta la página solicitada, con filtros, total y disponibilidad; no se afirma que una sola página contenga todos los resultados. CSV protege valores que podrían interpretarse como fórmula; XLSX escribe celdas de texto; PDF incluye una fuente Unicode. Las celdas nulas quedan vacías, los decimales conservan precisión, y la respuesta incluye `Cache-Control: no-store`. Se registra `REPORT_FILE_GENERATED` en auditoría antes de responder. Una falla de consulta, serialización o auditoría aborta la respuesta.

`RF-REP-05` (costos de nómina) permanece fuera del sprint por decisión del usuario; no se expone la ruta salarial. El proveedor de costos de personal no existe en el backend.

## Autenticación y acceso

Las rutas usan la base `/api/v1` y `Authorization: Bearer <accessToken>`. El token opaco y `usuario.permisos` vienen de la respuesta de login; consultar [OpenAPI de autenticación](openapi-auth.yaml). Usa los permisos para adaptar la interfaz, pero el backend autoriza cada solicitud. No se debe guardar el token en el código ni registrar su valor. Para seleccionar periodos desde `GET /api/v1/accounting/periods` se requiere además `CONTABILIDAD_PERIODOS_LEER`; tener permiso de reportes no concede automáticamente acceso a ese listado.

Los errores usan `ApiError` con `status`, `code` y `message`: `400` señala parámetros inválidos, `401` autenticación ausente o vencida, `403` permiso insuficiente y `404` un recurso inexistente. Muestra un error de carga ante fallos de red o servidor; no conviertas un error en cero. La configuración de proxy, CORS, TLS y conexión desde navegador debe acordarse con el equipo y aún no se ha validado.

## Consultas disponibles hoy

| Solicitud `GET` | Permiso | Uso y límites |
|---|---|---|
| `/api/v1/reports/dashboard?from=2026-11-01&to=2026-11-30&periodId=1` | Al menos un permiso de fuente | Solo incluye indicadores autorizados; sin periodo el resultado financiero no está disponible. |
| `/api/v1/reports/dashboard/export.csv?from=2026-11-01&to=2026-11-30&periodId=1` | Los mismos del dashboard | CSV UTF-8 con auditoría de generación; conservar filtros y manejar errores antes de descargar. |
| `/api/v1/reports/purchases/invoiced?from=2026-11-01&to=2026-11-30` | `COMPRAS_FACTURAS_PROVEEDOR_LEER` | Subtotal de facturas de proveedor validadas por fecha de emisión inclusiva; excluye impuestos. |
| `/api/v1/reports/sales/invoiced?from=2026-11-01&to=2026-11-30` | `VENTAS_FACTURAS_LEER` | Subtotal de facturas comerciales emitidas por fecha de emisión inclusiva; excluye impuestos. |
| `/api/v1/reports/inventory/low-stock-summary` | `INVENTARIO_ALERTAS_LEER` | Conteo global actual de productos con alerta e instante de la consulta. |
| `/api/v1/reports/finance/result-summary?periodId=1` | `CONTABILIDAD_REPORTES_LEER` | Resultado contable de un periodo; usa el estado de resultados existente. |
| `/api/v1/reports/finance/trial-balance?periodId=1&page=0&size=20` | `CONTABILIDAD_REPORTES_LEER` | Balance de prueba paginado; totales de todo el resultado, no solo de la página. |
| `/api/v1/reports/finance/income-statement?periodId=1` | `CONTABILIDAD_REPORTES_LEER` | Ingresos, costos, gastos y resultado del periodo. |
| `/api/v1/reports/finance/balance-sheet?periodId=1` | `CONTABILIDAD_REPORTES_LEER` | Saldos acumulados al cierre del periodo, no solo movimientos del periodo. |
| `/api/v1/reports/finance/cash-flow?periodId=1` | `CONTABILIDAD_REPORTES_LEER` | Flujo indirecto condicionado; puede responder `200` con `estado: NO_DISPONIBLE`, motivo en `detalle` y `rubros` vacío. |
| `/api/v1/inventory/low-stock-alerts?page=0&size=20` | `INVENTARIO_ALERTAS_LEER` | Alertas globales actuales, paginadas por producto. No reconstruye stock histórico ni muestra umbral por almacén. |

Sustituye `1` por un periodo existente. Los tres estados financieros devuelven `periodoId`, `inicio`, `fin`, `estado`, `detalle` y `rubros`; cada rubro tiene `nombre` e `importe`. `inicio` puede ser `null` en el balance general. Consulta los rubros por `nombre`, evitando depender de la posición del arreglo. El balance de prueba devuelve `content`, `page`, `size`, `totalElements`, `totalPages` y totales contables; el orden permitido y los campos completos están en OpenAPI. Los listados comienzan en página 0 y admiten `size` de 1 a 100.

El resumen contable responde, por ejemplo, `{"periodoId":1,"inicio":"2026-11-01","fin":"2026-11-30","resultado":75.00}`. Reutiliza el rubro `RESULTADO` de `/reports/finance/income-statement`, incluidos contraasientos y exclusión de borradores. El resultado no es margen operativo ni garantiza que todas las fuentes estén contabilizadas. `periodId` es obligatorio y positivo: ausente, inválido o no positivo produce `400`; inexistente, `404`. En el dashboard, un usuario autorizado recibe `NO_DISPONIBLE` cuando omite `periodId`.

La consulta de Compras responde, por ejemplo, `{"from":"2026-11-01","to":"2026-11-30","subtotal":200.00}`. El importe suma únicamente facturas `VALIDADA`; fechas límite incluidas. No equivale a pagos ni a la suma de órdenes o recepciones, y no demuestra que el histórico anterior al ERP esté completo. Un periodo sin facturas devuelve cero. El rango invertido, la fecha inválida o faltante devuelven `400`.

La consulta de Ventas devuelve los mismos campos `from`, `to` y `subtotal`. Cuenta facturas internas `EMITIDA` incluso si su integración contable está pendiente; no suma pedidos o despachos sin factura y no acredita cobro ni CFDI. El subtotal excluye impuestos y aplica los descuentos ya guardados en la factura. Un rango vacío devuelve cero; fechas inválidas, ausentes o invertidas devuelven `400`. No asegura que el historial anterior a la operación del sistema esté completo.

La alerta devuelve `content` con `productoId`, `sku`, `nombre`, `disponibleGlobal`, `puntoReorden` y `cantidadReorden`, además de la paginación. `cantidadReorden` es una configuración, no una orden generada. `totalElements` refleja productos en alerta en esa consulta, pero puede cambiar entre páginas. Para uso actual consulta la [guía de Inventario](guia-sprint-2-inventario.md).

El resumen responde, por ejemplo, `{"asOf":"2026-11-23T21:30:00Z","productosEnAlerta":3}`. `asOf` es el inicio de la sentencia que calcula el conteo en PostgreSQL; es una fotografía actual, no un filtro histórico ni una selección de almacén. Utiliza la misma regla global que el listado, pero dos solicitudes separadas pueden observar cambios de existencias entre ellas. Sin alertas devuelve cero. La ruta de diseño `/reports/inventory/low-stock` con filtro por almacén sigue sin implementar.

## Dashboard integrado

La solicitud implementada es:

```http
GET /api/v1/reports/dashboard?from=2026-11-01&to=2026-11-30&periodId=1
Authorization: Bearer <accessToken>
```

`from` y `to` son fechas `YYYY-MM-DD` obligatorias e inclusivas, con `from <= to`, aplicadas a la fecha de emisión de las facturas. `periodId` es opcional, entero positivo y selecciona un periodo contable que puede tener fechas diferentes; presenta las fechas de cada indicador. El indicador de inventario muestra `asOf`, inicio de la sentencia que establece la instantánea del dashboard; no reconstruye stock histórico ni depende del rango de fechas. Los indicadores autorizados se leen en una única transacción `REPEATABLE_READ`. Sin `periodId`, un usuario con permiso financiero recibe ese indicador como `NO_DISPONIBLE`. Con permiso financiero, un `periodId` inexistente produce `404`; sin ese permiso no se consulta el periodo para ese indicador. Parámetros inválidos producen `400`.

| Código | Permiso requerido | Presentación sugerida |
|---|---|---|
| `COMPRAS_FACTURADO_SIN_IMPUESTOS` | `COMPRAS_FACTURAS_PROVEEDOR_LEER` | Suma de subtotales de facturas de proveedor validadas; no representa pagos ni todas las compras. |
| `VENTAS_FACTURADO_SIN_IMPUESTOS` | `VENTAS_FACTURAS_LEER` | Suma de subtotales de facturas comerciales emitidas; no representa cobros ni CFDI. |
| `INVENTARIO_PRODUCTOS_ALERTA` | `INVENTARIO_ALERTAS_LEER` | Cantidad actual de productos con alerta global; unidad `PRODUCTOS`. |
| `FINANZAS_RESULTADO` | `CONTABILIDAD_REPORTES_LEER` | Resultado contable del periodo seleccionado; no representa margen operativo. |

Las tres cantidades monetarias tienen unidad `IMPORTE` sin código de moneda acordado. El backend omite por completo los indicadores sin permiso: una ausencia no significa cero ni falta de datos. Si el usuario no posee ninguno de los cuatro permisos, devuelve `403`; sin autenticación, `401`. Los valores describen registros existentes, sin asegurar que el historial o la contabilización estén completos.

Ejemplo ilustrativo, con permisos para ventas, inventario y finanzas, pero sin permiso para compras y sin periodo contable:

```json
{
  "from": "2026-11-01",
  "to": "2026-11-30",
  "indicators": [
    {
      "code": "VENTAS_FACTURADO_SIN_IMPUESTOS",
      "source": "facturas_venta: EMITIDA",
      "from": "2026-11-01",
      "to": "2026-11-30",
      "asOf": null,
      "availability": "DISPONIBLE",
      "value": 1250.50,
      "unit": "IMPORTE",
      "unavailabilityReason": null
    },
    {
      "code": "INVENTARIO_PRODUCTOS_ALERTA",
      "source": "alerta global de inventario",
      "from": null,
      "to": null,
      "asOf": "2026-11-23T19:00:00Z",
      "availability": "DISPONIBLE",
      "value": 0,
      "unit": "PRODUCTOS",
      "unavailabilityReason": null
    },
    {
      "code": "FINANZAS_RESULTADO",
      "source": "estado de resultados contable",
      "from": null,
      "to": null,
      "asOf": null,
      "availability": "NO_DISPONIBLE",
      "value": null,
      "unit": "IMPORTE",
      "unavailabilityReason": "Seleccione un periodo contable"
    }
  ]
}
```

El texto exacto de `source` y del motivo es descriptivo; identifica métricas por `code`. En `DISPONIBLE`, `value` es numérico y el motivo es `null`; cero es un resultado real. En `NO_DISPONIBLE`, `value` es `null` y el motivo explica la ausencia de cálculo. Muestra carga, error, sin permiso, cero y no disponible como estados distintos. No supongas un orden fijo del arreglo. `from`, `to` y `asOf` no aplicables son `null`.

Los importes son números decimales JSON, pero el tipo `number` de JavaScript puede perder precisión incluso durante `JSON.parse`, en especial en sumas grandes. Usa una estrategia decimal que preserve el texto numérico antes de convertirlo y evita cálculos financieros con punto flotante binario. Formatea solo para presentación y no inventes un símbolo de moneda. Maneja los identificadores `int64` de acuerdo con los límites de precisión del cliente.

## Lista para la integración

1. Revisar con backend el OpenAPI y la configuración del entorno antes de activar el cliente; no usar datos del ejemplo como respuesta real.
2. Probar un usuario con un permiso, otro con varios, uno sin ninguno y una sesión vencida; verificar omisión de métricas y respuestas `401`/`403`.
3. Probar cero, `NO_DISPONIBLE`, periodo ausente e inexistente, fechas inválidas y periodos distintos; mostrar `asOf` para inventario y periodos por indicador.
4. Comprobar importes grandes, errores de red, recarga y paginación; verificar que nunca se muestre un dato anterior tras cambiar de usuario o permisos.
5. Validar en el navegador real autenticación, conectividad, proxy/CORS y formato visual. Comunicar a backend cualquier incompatibilidad antes de declarar integrada la pantalla.

Nómina queda fuera del sprint. Los análisis y formatos CSV/PDF/XLSX se detallan arriba. La confirmación del compañero y la prueba integrada en navegador aún están pendientes.

## Parte 9 — exportación CSV aprobada

El usuario aprobó exportar los indicadores actuales y diferir los análisis adicionales. `GET /api/v1/reports/dashboard/export.csv` recibe `from`, `to` y `periodId` opcional con las mismas reglas y permisos del dashboard. Descarga `dashboard.csv`, UTF-8, delimitador coma, líneas CRLF, encabezado y hasta cuatro filas. Las columnas son `requestedFrom,requestedTo,periodId,code,source,from,to,asOf,availability,value,unit,unavailabilityReason`. Las celdas nulas quedan vacías; el valor no disponible nunca se convierte en cero. Los importes usan punto decimal; los textos se entrecomillan y escapan comillas. No admite textos libres ni fórmulas del cliente.

La exportación reutiliza la consulta existente en una transacción `REPEATABLE_READ` que permite insertar el evento `DASHBOARD_CSV_GENERATED` en la auditoría existente. Si falla la consulta o el registro, no entrega el CSV. El evento conserva actor y fecha sin copiar los importes; acredita generación, no descarga recibida por el cliente. La respuesta usa `Cache-Control: no-store`. El GET JSON conserva su lectura sin registro de generación CSV.

El CSV dedicado del dashboard reutiliza auditoría V4. La exportación genérica CSV/PDF/XLSX registra `REPORT_FILE_GENERATED` sin persistir resultados. V25 crea las políticas de mínimos por almacén y clasificación de margen. Las tablas objetivo de ejecuciones e indicadores siguen sin materializar. Rotación, ABC, rentabilidad, margen y productos sin movimiento están implementados dentro de los límites descritos arriba; nómina permanece fuera del sprint. La integración frontend y el rendimiento con datos representativos siguen pendientes.
