import { useState, type FormEvent } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, MenuItem, Paper, TextField, Typography } from '@mui/material'
import RefreshIcon from '@mui/icons-material/Refresh'
import DownloadIcon from '@mui/icons-material/FileDownloadOutlined'
import { errorMessage } from '../../api/http'
import { PATHS, PERM } from '../../config/contract'
import { normalizePage, type Row } from '../../shared/api'
import { today } from '../../shared/format'
import { useAuth } from '../auth/AuthContext'
import { downloadDashboardCsv, exactDecimal, getReport, type ReportParams } from '../reporteria/reportingApi'
import { useNotify } from '../../shared/Notify'

type DashboardIndicator = {
  code: string
  source: string
  from: string | null
  to: string | null
  asOf: string | null
  availability: 'DISPONIBLE' | 'NO_DISPONIBLE'
  value: number | string | null
  unit: 'IMPORTE' | 'PRODUCTOS' | string
  unavailabilityReason: string | null
}
type DashboardResponse = { from: string; to: string; indicators: DashboardIndicator[] }
type DashboardFilter = { from: string; to: string; periodId: string }

const currentDay = today()
const startOfMonth = `${currentDay.slice(0, 7)}-01`

export default function DashboardPage() {
  const { user, can } = useAuth()
  const notify = useNotify()
  const [filter, setFilter] = useState<DashboardFilter>({ from: startOfMonth, to: currentDay, periodId: '' })
  const [applied, setApplied] = useState(filter)
  const [exporting, setExporting] = useState(false)
  const canFinance = can(PERM.reportesContablesLeer)
  const hasSources = can(PERM.facturaProveedorLeer) || can(PERM.facturasLeer)
    || can(PERM.alertasLeer) || canFinance
  const validRange = !!filter.from && !!filter.to && filter.from <= filter.to && filter.to <= today()
  const periods = useQuery({
    queryKey: [PATHS.periodosContables, 'dashboard'],
    enabled: canFinance && can(PERM.periodosLeer),
    queryFn: async () => normalizePage(await getReport<unknown>(PATHS.periodosContables, { page: 0, size: 100, sort: 'fechaInicio', direction: 'desc' })).rows,
  })
  const params: ReportParams = {
    from: applied.from,
    to: applied.to,
    ...(canFinance && applied.periodId ? { periodId: applied.periodId } : {}),
  }
  const dashboard = useQuery({
    queryKey: [PATHS.dashboard, params],
    enabled: hasSources && validRange,
    queryFn: () => getReport<DashboardResponse>(PATHS.dashboard, params),
  })
  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (validRange && (!filter.periodId || /^[1-9]\d*$/.test(filter.periodId))) setApplied(filter)
  }
  const exportCsv = async () => {
    setExporting(true)
    try {
      await downloadDashboardCsv(params)
    } catch (error) {
      notify(errorMessage(error), 'error')
    } finally {
      setExporting(false)
    }
  }
  const noFuture = filter.to <= today()
  const periodInvalid = !!filter.periodId && !/^[1-9]\d*$/.test(filter.periodId)
  const pendingChanges = filter.from !== applied.from || filter.to !== applied.to || filter.periodId !== applied.periodId

  return <Box>
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, flexWrap: 'wrap', mb: 2 }}>
      <Box sx={{ flexGrow: 1 }}><Typography variant="h5">Hola, {user?.nombre}</Typography><Typography color="text.secondary">Indicadores que tu cuenta tiene permiso de consultar.</Typography></Box>
      <Button startIcon={<DownloadIcon/>} disabled={!hasSources || !validRange || pendingChanges || exporting} onClick={() => void exportCsv()}>{exporting ? 'Generando…' : 'Exportar CSV'}</Button>
    </Box>
    <Paper component="form" onSubmit={submit} elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap' }}>
      <TextField size="small" label="Desde" type="date" value={filter.from} onChange={(event) => setFilter({ ...filter, from: event.target.value })} slotProps={{ inputLabel: { shrink: true } }} error={!filter.from || (!!filter.to && filter.from > filter.to)} />
      <TextField size="small" label="Hasta" type="date" value={filter.to} onChange={(event) => setFilter({ ...filter, to: event.target.value })} slotProps={{ inputLabel: { shrink: true } }} error={!noFuture || (!!filter.from && filter.to < filter.from)} helperText={!noFuture ? 'La fecha final no puede ser futura.' : undefined} />
      {canFinance && can(PERM.periodosLeer) && <TextField select size="small" label="Periodo financiero" value={filter.periodId} onChange={(event) => setFilter({ ...filter, periodId: event.target.value })} sx={{ minWidth: 260 }} error={!!periods.error} helperText={periods.error ? errorMessage(periods.error) : undefined}>
        <MenuItem value="">Sin periodo: resultado no disponible</MenuItem>
        {(periods.data as Row[] | undefined)?.map((period) => <MenuItem key={period.id} value={String(period.id)}>{period.fechaInicio} — {period.fechaFin} · {period.estado}</MenuItem>)}
      </TextField>}
      {canFinance && !can(PERM.periodosLeer) && <TextField size="small" label="ID de periodo financiero" value={filter.periodId} onChange={(event) => setFilter({ ...filter, periodId: event.target.value })} slotProps={{ htmlInput: { inputMode: 'numeric' } }} error={periodInvalid} helperText="Se requiere un periodo para calcular el resultado." />}
      <Button type="submit" variant="contained" startIcon={<RefreshIcon/>} disabled={!validRange || periodInvalid}>Actualizar</Button>
    </Paper>
    {!hasSources && <Alert severity="info">Tu cuenta no tiene permiso para ninguno de los indicadores del dashboard.</Alert>}
    {hasSources && !validRange && <Alert severity="warning">Indica un rango inclusivo válido y no futuro.</Alert>}
    {pendingChanges && <Alert severity="info" sx={{ mb: 2 }}>Hay filtros sin consultar; actualiza para cargar sus resultados.</Alert>}
    {!pendingChanges && dashboard.error && <Alert severity="error" sx={{ mb: 2 }}>{errorMessage(dashboard.error)}</Alert>}
    {!pendingChanges && dashboard.isLoading && <Typography sx={{ mb: 2 }}>Consultando indicadores…</Typography>}
    {!pendingChanges && hasSources && dashboard.data && dashboard.data.indicators.length === 0 && <Alert severity="info" sx={{ mb: 2 }}>No hay indicadores autorizados para mostrar.</Alert>}
    <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', xl: 'repeat(4, 1fr)' } }}>
      {!pendingChanges && dashboard.data?.indicators.map((indicator) => <IndicatorCard key={indicator.code} indicator={indicator}/>) }
    </Box>
    {!pendingChanges && dashboard.data && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 2 }}>
      Las facturas usan el rango seleccionado. Inventario es una instantánea actual; el resultado usa las fechas del periodo contable elegido. Los importes excluyen impuestos cuando así lo define su fuente, no representan pagos y no declaran una moneda.
    </Typography>}
  </Box>
}

function IndicatorCard({ indicator }: { indicator: DashboardIndicator }) {
  const available = indicator.availability === 'DISPONIBLE'
  const value = indicator.value == null ? '—' : indicator.unit === 'IMPORTE' ? exactDecimal(indicator.value, 2) : exactDecimal(indicator.value)
  return <Paper variant="outlined" sx={{ p: 2.5, minWidth: 0 }}>
    <Typography color="text.secondary" variant="body2">{indicatorTitle[indicator.code] ?? indicator.code}</Typography>
    {available ? <Typography variant="h4" color="primary" sx={{ fontWeight: 700, overflowWrap: 'anywhere', my: 1 }}>{value}</Typography>
      : <Alert severity="warning" sx={{ my: 1 }}>No disponible: {indicator.unavailabilityReason ?? 'La fuente no permitió calcular este indicador.'}</Alert>}
    {available && <Typography variant="caption">Unidad: {indicator.unit === 'IMPORTE' ? 'importe (sin moneda definida)' : indicator.unit}</Typography>}
    <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>{indicator.source}</Typography>
    <Typography variant="caption" color="text.secondary" sx={{ display: 'block' }}>
      {indicator.from && indicator.to ? `${indicator.from} a ${indicator.to}` : indicator.asOf ? `Consulta al ${new Date(indicator.asOf).toLocaleString('es-MX')}` : 'Periodo contable seleccionado'}
    </Typography>
  </Paper>
}

const indicatorTitle: Record<string, string> = {
  COMPRAS_FACTURADO_SIN_IMPUESTOS: 'Facturado por proveedores',
  VENTAS_FACTURADO_SIN_IMPUESTOS: 'Facturado en ventas',
  INVENTARIO_PRODUCTOS_ALERTA: 'Productos con alerta global',
  FINANZAS_RESULTADO: 'Resultado contable del periodo',
}
