import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Alert, Box, Button, MenuItem, Paper, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../api/http'
import { PATHS } from '../../config/contract'
import { normalizePage } from '../../shared/api'
import { useAuth } from '../auth/AuthContext'

interface Period {
  id: number
  fechaInicio: string
  fechaFin: string
  estado: string
}

interface FinancialReport {
  status: string
  totalsByType: Record<string, number>
}

function useTotal(endpoint: string, params: Record<string, unknown>) {
  return useQuery({
    queryKey: ['total', endpoint, params],
    queryFn: async () => normalizePage((await http.get(endpoint, { params: { page: 0, size: 1, ...params } })).data).total,
  })
}

function Card({ to, label, value, loading, error }: {
  to: string
  label: string
  value?: number
  loading: boolean
  error?: boolean
}) {
  return (
    <Paper component={RouterLink} to={to} elevation={0}
      sx={{ p: 3, textDecoration: 'none', color: 'inherit', display: 'block', '&:hover': { outline: '2px solid', outlineColor: 'primary.main' } }}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography sx={{ fontWeight: 700 }} variant="h3" color={error ? 'error' : 'primary'}>
        {loading ? '…' : error ? 'Error' : (value ?? '—')}
      </Typography>
    </Paper>
  )
}

function UnavailableCard({ label, reason }: { label: string; reason: string }) {
  return (
    <Paper elevation={0} sx={{ p: 3 }}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography sx={{ fontWeight: 700 }} variant="h6">No disponible</Typography>
      <Typography variant="body2" color="text.secondary">{reason}</Typography>
    </Paper>
  )
}

function amount(value: number | undefined) {
  return value === undefined
    ? '—'
    : new Intl.NumberFormat('es-MX', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(value)
}

export default function DashboardPage() {
  const { user } = useAuth()
  const [periodId, setPeriodId] = useState('')
  const prov = useTotal(PATHS.proveedores, { activo: true })
  const prod = useTotal(PATHS.productos, { activo: true })
  const clientes = useTotal(PATHS.clientes, { activo: true })
  const cuentas = useQuery({
    queryKey: [PATHS.cuentasContables, 'dashboard-count'],
    queryFn: async () => (await http.get(PATHS.cuentasContables)).data.length as number,
  })
  const periodos = useQuery({
    queryKey: [PATHS.periodosContables, 'dashboard-count'],
    queryFn: async () => (await http.get<Period[]>(PATHS.periodosContables)).data,
  })
  const selectedPeriod = periodos.data?.find((period) => period.id === Number(periodId)) ?? periodos.data?.[0]
  const income = useQuery({
    queryKey: [PATHS.estadoResultados, selectedPeriod?.id],
    enabled: Boolean(selectedPeriod),
    queryFn: async () => (await http.get<FinancialReport>(PATHS.estadoResultados, {
      params: { periodoId: selectedPeriod?.id },
    })).data,
  })
  const balance = useQuery({
    queryKey: [PATHS.balanceGeneral, selectedPeriod?.id],
    enabled: Boolean(selectedPeriod),
    queryFn: async () => (await http.get<FinancialReport>(PATHS.balanceGeneral, {
      params: { periodoId: selectedPeriod?.id },
    })).data,
  })
  const incomeTypes = income.data?.totalsByType
  const balanceTypes = balance.data?.totalsByType
  const financialMetrics = [
    { label: 'Ingresos contabilizados', value: incomeTypes?.INGRESO, loading: income.isLoading },
    { label: 'Costos contabilizados', value: incomeTypes?.COSTO, loading: income.isLoading },
    { label: 'Gastos contabilizados', value: incomeTypes?.GASTO, loading: income.isLoading },
    { label: 'Activos', value: balanceTypes?.ACTIVO, loading: balance.isLoading },
    { label: 'Pasivos', value: balanceTypes?.PASIVO, loading: balance.isLoading },
    { label: 'Capital', value: balanceTypes?.CAPITAL, loading: balance.isLoading },
  ]

  return (
    <Box>
      <Typography variant="h5" gutterBottom>Hola, {user?.nombre}</Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>
        Resumen ejecutivo de catálogos y saldos contables registrados. Las métricas sin fuente o fórmula aprobada se muestran como no disponibles.
      </Typography>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' } }}>
        <Card to="/proveedores" label="Proveedores activos" value={prov.data} loading={prov.isLoading} error={prov.isError} />
        <Card to="/productos" label="Productos activos" value={prod.data} loading={prod.isLoading} error={prod.isError} />
        <Card to="/ventas/clientes" label="Clientes activos" value={clientes.data} loading={clientes.isLoading} error={clientes.isError} />
        <Card to="/contabilidad" label="Cuentas contables" value={cuentas.data} loading={cuentas.isLoading} error={cuentas.isError} />
        <Card to="/contabilidad" label="Periodos contables" value={periodos.data?.length} loading={periodos.isLoading} error={periodos.isError} />
      </Box>

      <Paper elevation={0} sx={{ p: 2, mt: 3 }}>
        <Box sx={{ display: 'flex', justifyContent: 'space-between', gap: 2, alignItems: 'center', flexWrap: 'wrap', mb: 2 }}>
          <Box>
            <Typography variant="h6">Resumen financiero</Typography>
            <Typography variant="body2" color="text.secondary">
              Saldos por tipo de cuenta; no equivalen a margen operativo ni rentabilidad por cliente.
            </Typography>
          </Box>
          <TextField
            select
            size="small"
            label="Periodo contable"
            value={selectedPeriod?.id ?? ''}
            onChange={(event) => setPeriodId(event.target.value)}
            sx={{ minWidth: 250 }}
            disabled={!periodos.data?.length}
          >
            {periodos.data?.map((period) => (
              <MenuItem key={period.id} value={period.id}>
                {period.fechaInicio} – {period.fechaFin} ({period.estado})
              </MenuItem>
            ))}
          </TextField>
        </Box>

        {periodos.isError && <Alert severity="error">{errorMessage(periodos.error)}</Alert>}
        {!periodos.isLoading && !periodos.isError && !periodos.data?.length && (
          <Alert severity="info">No hay periodos contables para generar los informes financieros.</Alert>
        )}
        {selectedPeriod && (income.isError || balance.isError) && (
          <Alert severity="error" sx={{ mb: 2 }}>
            {income.isError ? errorMessage(income.error) : errorMessage(balance.error)}
          </Alert>
        )}
        {selectedPeriod && (
          <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' } }}>
            {financialMetrics.map((metric) => (
              <Paper key={metric.label} variant="outlined" sx={{ p: 2 }}>
                <Typography color="text.secondary">{metric.label}</Typography>
                <Typography variant="h5" sx={{ fontWeight: 700 }}>
                  {metric.loading ? '…' : amount(metric.value)}
                </Typography>
                <Typography variant="caption" color="text.secondary">Importe contable · periodo seleccionado</Typography>
              </Paper>
            ))}
          </Box>
        )}
        <Button component={RouterLink} to="/contabilidad" sx={{ mt: 2 }}>Abrir reportes contables</Button>
      </Paper>

      <Typography variant="h6" sx={{ mt: 3, mb: 1 }}>Indicadores pendientes de fuente o definición</Typography>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' } }}>
        <UnavailableCard label="Ventas y compras" reason="Los documentos transaccionales y KPI aprobados aún no están disponibles en el backend." />
        <UnavailableCard label="Inventario y rotación" reason="No hay existencias ni movimientos de inventario implementados como fuente de datos." />
        <UnavailableCard label="Rentabilidad y margen" reason="Faltan costos de despacho y una fórmula de negocio validada; no se calcula un porcentaje estimado." />
      </Box>
    </Box>
  )
}
