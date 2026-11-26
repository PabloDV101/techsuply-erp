import { useState } from 'react'
import { Box, Paper, Tab, Tabs, Typography } from '@mui/material'
import { PERM } from '../../config/contract'
import { useAuth } from '../auth/AuthContext'
import CuentasPeriodosTab from './CuentasPeriodosTab'
import AsientosTab from './AsientosTab'
import ReportesTab from './ReportesTab'
import CuentasPorCobrarTab from './CuentasPorCobrarTab'
import CuentasPorPagarTab from './CuentasPorPagarTab'
import ConfiguracionContableTab from './ConfiguracionContableTab'
import IntegracionesTab from './IntegracionesTab'

const sections = [
  { label: 'Cuentas y periodos', permissions: [PERM.contabilidadLeer, PERM.cuentaAdmin, PERM.periodosLeer, PERM.periodoAdmin, PERM.periodoCerrar], view: <CuentasPeriodosTab/> },
  { label: 'Asientos', permissions: [PERM.asientosLeer, PERM.asientosRegistrar, PERM.asientosContabilizar, PERM.asientosRevertir], view: <AsientosTab/> },
  { label: 'Reportes', permissions: [PERM.reportesContablesLeer], view: <ReportesTab/> },
  { label: 'CxC y cobros', permissions: [PERM.cxcLeer, PERM.cobrosLeer, PERM.cobrosRegistrar, PERM.cobrosConfirmar], view: <CuentasPorCobrarTab/> },
  { label: 'CxP y pagos', permissions: [PERM.cxpLeer, PERM.pagosProveedorLeer, PERM.pagosProveedorRegistrar, PERM.pagosProveedorConfirmar], view: <CuentasPorPagarTab/> },
  { label: 'Integraciones', permissions: [PERM.integracionesLeer, PERM.integracionesReintentar], view: <IntegracionesTab/> },
  { label: 'Configuración', permissions: [PERM.integracionesAdmin, PERM.inventarioCuentasLeer, PERM.inventarioCuentasConfigurar, PERM.cobrosConfigurar, PERM.cxpLeer, PERM.cxpConfigurar, PERM.pagosProveedorConfigurar], view: <ConfiguracionContableTab/> },
]

export default function ContabilidadPage() {
  const { can } = useAuth()
  const available = sections.filter((section) => section.permissions.some(can))
  const [tab, setTab] = useState(0)
  const selected = Math.min(tab, available.length - 1)
  if (!available.length) return <Typography>No tienes permisos para consultar las funciones de Contabilidad.</Typography>
  return <Box>
    <Typography variant="h5" sx={{ mb: 2 }}>Contabilidad</Typography>
    <Paper elevation={0} sx={{ mb: 2 }}><Tabs value={selected} onChange={(_, value) => setTab(value)} variant="scrollable" scrollButtons="auto">{available.map((section) => <Tab key={section.label} label={section.label}/>)}</Tabs></Paper>
    {available[selected]?.view}
  </Box>
}
