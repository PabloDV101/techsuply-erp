import { Link as RouterLink } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { Box, Paper, Typography } from '@mui/material'
import { http } from '../../api/http'
import { PATHS } from '../../config/contract'
import { normalizePage } from '../../shared/api'
import { useAuth } from '../auth/AuthContext'

function useTotal(endpoint: string, params: Record<string, unknown>) {
  return useQuery({
    queryKey: ['total', endpoint, params],
    queryFn: async () => normalizePage((await http.get(endpoint, { params: { page: 0, size: 1, ...params } })).data).total,
  })
}

function Card({ to, label, value, loading }: { to: string; label: string; value?: number; loading: boolean }) {
  return (
    <Paper component={RouterLink} to={to} elevation={0}
      sx={{ p: 3, textDecoration: 'none', color: 'inherit', display: 'block', '&:hover': { outline: '2px solid', outlineColor: 'primary.main' } }}>
      <Typography color="text.secondary">{label}</Typography>
      <Typography sx={{ fontWeight: 700 }} variant="h3" color="primary">{loading ? '…' : (value ?? '—')}</Typography>
    </Paper>
  )
}

export default function DashboardPage() {
  const { user } = useAuth()
  const prov = useTotal(PATHS.proveedores, { activo: true })
  const prod = useTotal(PATHS.productos, { activo: true })
  const clientes = useTotal(PATHS.clientes, { activo: true })
  const cuentas = useQuery({
    queryKey: [PATHS.cuentasContables, 'dashboard-count'],
    queryFn: async () => (await http.get(PATHS.cuentasContables)).data.length,
  })
  const periodos = useQuery({
    queryKey: [PATHS.periodosContables, 'dashboard-count'],
    queryFn: async () => (await http.get(PATHS.periodosContables)).data.length,
  })
  return (
    <Box>
      <Typography variant="h5" gutterBottom>Hola, {user?.nombre}</Typography>
      <Typography color="text.secondary" sx={{ mb: 3 }}>Resumen de recursos disponibles. Los módulos operativos pendientes se identifican hasta su implementación.</Typography>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(2, 1fr)', lg: 'repeat(3, 1fr)' } }}>
        <Card to="/proveedores" label="Proveedores activos" value={prov.data} loading={prov.isLoading} />
        <Card to="/productos" label="Productos activos" value={prod.data} loading={prod.isLoading} />
        <Card to="/ventas/clientes" label="Clientes activos" value={clientes.data} loading={clientes.isLoading} />
        <Card to="/contabilidad" label="Cuentas contables" value={cuentas.data} loading={cuentas.isLoading} />
        <Card to="/contabilidad" label="Periodos contables" value={periodos.data} loading={periodos.isLoading} />
      </Box>
    </Box>
  )
}
