import { useState, type FormEvent } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField, Typography } from '@mui/material'
import PlayArrowIcon from '@mui/icons-material/PlayArrow'
import { errorMessage } from '../../api/http'
import { PATHS, PERM } from '../../config/contract'
import { normalizePage, type Row } from '../../shared/api'
import { today } from '../../shared/format'
import { useAuth } from '../auth/AuthContext'
import ExportReportButton from './ExportReportButton'
import { exactDecimal, getReport, reportLabel, type ReportParams } from './reportingApi'

type Definition = {
  id: string
  title: string
  endpoint?: string
  permission: string
  dateRange?: boolean
  asOf?: boolean
  days?: boolean
  warehouse?: boolean
  product?: boolean
  customer?: boolean
  period?: boolean
  paged?: boolean
  description: string
}

const definitions: Definition[] = [
  { id: 'purchases/invoiced', title: 'Facturado por proveedores', endpoint: PATHS.facturadoCompras, permission: PERM.facturaProveedorLeer, dateRange: true, description: 'Subtotal sin impuestos de facturas de proveedor validadas. No representa pagos.' },
  { id: 'sales/invoiced', title: 'Facturado en ventas', endpoint: PATHS.facturadoVentas, permission: PERM.facturasLeer, dateRange: true, description: 'Subtotal sin impuestos de facturas comerciales emitidas. No representa cobros ni CFDI.' },
  { id: 'inventory/low-stock-summary', title: 'Productos con alerta global', endpoint: PATHS.alertasResumen, permission: PERM.alertasLeer, description: 'Conteo actual según la regla global de alerta; no filtra por almacén ni es un dato histórico.' },
  { id: 'inventory/low-stock', title: 'Bajo mínimo configurado', endpoint: PATHS.minimosBajos, permission: PERM.reportesInventarioLeer, warehouse: true, product: true, paged: true, description: 'Solo incluye pares producto/almacén con mínimo explícito; no copia el umbral global.' },
  { id: 'inventory/inactive-products', title: 'Sin movimiento registrado', endpoint: PATHS.productosSinMovimiento, permission: PERM.reportesInventarioLeer, asOf: true, days: true, warehouse: true, product: true, paged: true, description: 'Distingue NUNCA_REGISTRADO de SIN_MOVIMIENTO. No clasifica productos como obsoletos.' },
  { id: 'inventory/abc', title: 'Clasificación ABC', endpoint: PATHS.abcInventario, permission: PERM.reportesInventarioLeer, dateRange: true, warehouse: true, paged: true, description: 'Clasificación basada en costo de despachos confirmados, con cortes acumulados aprobados.' },
  { id: 'inventory/turnover', title: 'Rotación de inventario', permission: PERM.reportesInventarioLeer, description: 'Abre el informe con filtros, paginación y exportación de página.' },
  { id: 'sales/customer-profitability', title: 'Rentabilidad bruta por cliente', endpoint: PATHS.rentabilidadClientes, permission: PERM.facturasLeer, dateRange: true, customer: true, paged: true, description: 'Requiere además CONTABILIDAD_REPORTES_LEER; compara facturación sin impuestos con costos de despachos conciliados.' },
  { id: 'finance/operating-margin', title: 'Margen operativo', endpoint: PATHS.margenOperativo, permission: PERM.reportesContablesLeer, period: true, description: 'Usa clasificaciones explícitas; no infiere la clasificación de periodos históricos.' },
]

type Filters = { from: string; to: string; asOf: string; days: string; warehouseId: string; productId: string; customerId: string; periodId: string }
type Request = { definition: Definition; params: ReportParams }

export default function ReportesPage() {
  const { can } = useAuth()
  const available = definitions.filter((definition) => can(definition.permission)
    && (definition.id !== 'sales/customer-profitability' || can(PERM.reportesContablesLeer)))
  const [selectedId, setSelectedId] = useState('')
  const selected = available.find((definition) => definition.id === selectedId) ?? available[0]
  const currentDay = today()
  const [filters, setFilters] = useState<Filters>({ from: `${currentDay.slice(0, 7)}-01`, to: currentDay, asOf: currentDay, days: '', warehouseId: '', productId: '', customerId: '', periodId: '' })
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(20)
  const [applied, setApplied] = useState<Request | null>(null)
  const periods = useQuery({
    queryKey: [PATHS.periodosContables, 'reporting'],
    enabled: !!selected?.period && can(PERM.periodosLeer),
    queryFn: async () => normalizePage(await getReport<unknown>(PATHS.periodosContables, { page: 0, size: 100, sort: 'fechaInicio', direction: 'desc' })).rows,
  })
  const result = useQuery({
    queryKey: ['reporting', applied?.definition.id, applied?.params],
    enabled: !!applied?.definition.endpoint,
    queryFn: () => getReport<unknown>(applied?.definition.endpoint ?? '', applied?.params ?? {}),
  })

  if (!selected) return <Alert severity="info">Tu cuenta no tiene permiso para consultar los informes disponibles.</Alert>

  const update = (key: keyof Filters, value: string) => {
    setFilters((current) => ({ ...current, [key]: value }))
    setPage(0)
    setApplied(null)
  }
  const validDateRange = !!filters.from && !!filters.to && filters.from <= filters.to && filters.to <= today()
  const dateSpan = validDateRange ? (Date.parse(`${filters.to}T00:00:00Z`) - Date.parse(`${filters.from}T00:00:00Z`)) / 86_400_000 : Infinity
  const validCutoff = !!filters.asOf && filters.asOf <= today() && /^\d+$/.test(filters.days) && Number(filters.days) >= 1 && Number(filters.days) <= 36500
  const validPeriod = !!filters.periodId && /^[1-9]\d*$/.test(filters.periodId)
  const validOptionalId = (value: string) => !value || /^[1-9]\d*$/.test(value)
  const canRun = !!selected && (!selected.dateRange || validDateRange)
    && (!selected.dateRange || !selected.paged || dateSpan <= 3660)
    && (!selected.asOf || validCutoff) && (!selected.period || validPeriod)
    && (!selected.customer || validOptionalId(filters.customerId))
    && (!selected.product || validOptionalId(filters.productId))
    && (!selected.warehouse || validOptionalId(filters.warehouseId))

  const buildParams = (): ReportParams => ({
    ...(selected.dateRange ? { from: filters.from, to: filters.to } : {}),
    ...(selected.asOf ? { asOf: filters.asOf, days: Number(filters.days) } : {}),
    ...(selected.warehouse && filters.warehouseId ? { warehouseId: filters.warehouseId } : {}),
    ...(selected.product && filters.productId ? { productId: filters.productId } : {}),
    ...(selected.customer && filters.customerId ? { customerId: filters.customerId } : {}),
    ...(selected.period ? { periodId: filters.periodId } : {}),
    ...(selected.paged ? { page, size } : {}),
  })
  const run = (event: FormEvent) => {
    event.preventDefault()
    if (selected && canRun && selected.endpoint) setApplied({ definition: selected, params: buildParams() })
  }
  const changeReport = (id: string) => { setSelectedId(id); setPage(0); setApplied(null) }
  const selectedPeriod = selected.period && can(PERM.periodosLeer)

  return <Box>
    <Typography variant="h5" sx={{ mb: 2 }}>Reportes</Typography>
    <Paper component="form" onSubmit={run} elevation={0} sx={{ p: 2, mb: 2, display: 'grid', gap: 2, gridTemplateColumns: { sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(3, minmax(0, 1fr))' } }}>
      <TextField select label="Informe" value={selected.id} onChange={(event) => changeReport(event.target.value)}>
        {available.map((definition) => <MenuItem key={definition.id} value={definition.id}>{definition.title}</MenuItem>)}
      </TextField>
      {selected.dateRange && <>
        <TextField label="Desde" type="date" value={filters.from} onChange={(event) => update('from', event.target.value)} slotProps={{ inputLabel: { shrink: true } }} error={!filters.from || (!!filters.to && filters.from > filters.to)} />
        <TextField label="Hasta" type="date" value={filters.to} onChange={(event) => update('to', event.target.value)} slotProps={{ inputLabel: { shrink: true } }} error={!filters.to || filters.to > today() || (!!filters.from && filters.from > filters.to)} />
      </>}
      {selected.asOf && <>
        <TextField label="Fecha de corte" type="date" value={filters.asOf} onChange={(event) => update('asOf', event.target.value)} slotProps={{ inputLabel: { shrink: true } }} error={!filters.asOf || filters.asOf > today()} />
        <TextField label="Días sin movimiento" type="number" value={filters.days} onChange={(event) => update('days', event.target.value)} slotProps={{ htmlInput: { min: 1, max: 36500, step: 1 } }} error={!!filters.days && (!/^\d+$/.test(filters.days) || Number(filters.days) < 1 || Number(filters.days) > 36500)} />
      </>}
      {selected.warehouse && <TextField label="ID de almacén (opcional)" value={filters.warehouseId} onChange={(event) => update('warehouseId', event.target.value)} slotProps={{ htmlInput: { inputMode: 'numeric' } }} error={!validOptionalId(filters.warehouseId)} />}
      {selected.product && <TextField label="ID de producto (opcional)" value={filters.productId} onChange={(event) => update('productId', event.target.value)} slotProps={{ htmlInput: { inputMode: 'numeric' } }} error={!validOptionalId(filters.productId)} />}
      {selected.customer && <TextField label="ID de cliente (opcional)" value={filters.customerId} onChange={(event) => update('customerId', event.target.value)} slotProps={{ htmlInput: { inputMode: 'numeric' } }} error={!validOptionalId(filters.customerId)} />}
      {selected.period && (selectedPeriod
        ? <TextField select label="Periodo contable" value={filters.periodId} onChange={(event) => update('periodId', event.target.value)} error={!!periods.error} helperText={periods.error ? errorMessage(periods.error) : undefined}>
          <MenuItem value="">Selecciona un periodo</MenuItem>{(periods.data as Row[] | undefined)?.map((period) => <MenuItem key={period.id} value={String(period.id)}>{period.fechaInicio} — {period.fechaFin} · {period.estado}</MenuItem>)}
        </TextField>
        : <TextField label="ID de periodo contable" value={filters.periodId} onChange={(event) => update('periodId', event.target.value)} slotProps={{ htmlInput: { inputMode: 'numeric' } }} error={!!filters.periodId && !/^[1-9]\d*$/.test(filters.periodId)} helperText="No tienes permiso para consultar el catálogo de periodos." />)}
      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center' }}>
        {selected.endpoint && <Button type="submit" variant="contained" disabled={!canRun} startIcon={<PlayArrowIcon/>}>Consultar</Button>}
        {applied && <ExportReportButton report={applied.definition.id} params={applied.params} filename="reporte" disabled={result.isLoading || !!result.error} />}
      </Box>
    </Paper>
    <Alert severity="info" sx={{ mb: 2 }}>{selected.description} {selected.dateRange && selected.paged && dateSpan > 3660 && 'El rango no puede superar 3661 días.'} {selected.id === 'sales/customer-profitability' && 'Requiere permisos de facturas de ventas y reportes contables.'}</Alert>
    {selected.id === 'inventory/turnover' && <Paper elevation={0} sx={{ p: 2 }}><Typography>Este informe tiene filtros específicos y exporta la página consultada.</Typography><Button component={RouterLink} to="/inventario/rotacion" sx={{ mt: 1 }}>Abrir rotación de inventario</Button></Paper>}
    {result.isLoading && <Typography>Consultando…</Typography>}
    {result.error && <Alert severity="error">{errorMessage(result.error)}</Alert>}
    {applied && !result.isLoading && !result.error && result.data != null && <ReportResult data={result.data} paged={applied.definition.paged ?? false} page={Number(applied.params.page ?? 0)} size={Number(applied.params.size ?? 20)} onPageChange={(nextPage) => setApplied((current) => current ? { ...current, params: { ...current.params, page: nextPage } } : current)} onSizeChange={(nextSize) => { setPage(0); setSize(nextSize); setApplied((current) => current ? { ...current, params: { ...current.params, page: 0, size: nextSize } } : current) }} />}
  </Box>
}

function ReportResult({ data, paged, page, size, onPageChange, onSizeChange }: { data: unknown; paged: boolean; page: number; size: number; onPageChange: (page: number) => void; onSizeChange: (size: number) => void }) {
  const normalized = pageRows(data)
  if (normalized) {
    if (!normalized.rows.length) return <Alert severity="info">La consulta no devolvió filas.</Alert>
    const columns = Object.keys(normalized.rows[0])
    return <Paper elevation={0}>
      <Typography sx={{ p: 2 }} color="text.secondary">{normalized.total} resultados; se muestra la página solicitada.</Typography>
      <TableContainer sx={{ maxHeight: '65vh' }}><Table size="small" stickyHeader><TableHead><TableRow>{columns.map((column) => <TableCell key={column}>{reportLabel(column)}</TableCell>)}</TableRow></TableHead><TableBody>
        {normalized.rows.map((row, index) => <TableRow key={String(row.id ?? row.productId ?? row.customerId ?? index)} hover>{columns.map((column) => <TableCell key={column}>{formatCell(row[column])}</TableCell>)}</TableRow>)}
      </TableBody></Table></TableContainer>
      {paged && <TablePagination component="div" count={normalized.total} page={page} rowsPerPage={size} rowsPerPageOptions={[10, 20, 50, 100]} onPageChange={(_event, nextPage) => onPageChange(nextPage)} onRowsPerPageChange={(event) => onSizeChange(Number(event.target.value))} labelRowsPerPage="Filas por página" labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />}
    </Paper>
  }
  if (typeof data !== 'object' || data === null) return <Alert severity="info">{formatCell(data)}</Alert>
  const values = Object.entries(data as Record<string, unknown>)
  return <Paper elevation={0} sx={{ p: 2, display: 'grid', gap: 1, gridTemplateColumns: { sm: 'repeat(2, minmax(0, 1fr))' } }}>
    {values.map(([key, value]) => <Box key={key}><Typography variant="caption" color="text.secondary">{reportLabel(key)}</Typography><Typography>{formatCell(value)}</Typography></Box>)}
  </Paper>
}

function pageRows(data: unknown): { rows: Row[]; total: number } | null {
  if (!data || typeof data !== 'object' || !Array.isArray((data as { content?: unknown }).content)) return null
  return normalizePage(data)
}

function formatCell(value: unknown): string {
  if (value == null || value === '') return '—'
  if (typeof value === 'object') return JSON.stringify(value)
  if (typeof value === 'string' && /^-?\d+(?:\.\d+)?$/.test(value)) return exactDecimal(value)
  return String(value)
}
