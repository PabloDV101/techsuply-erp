import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import {
  Alert, AppBar, Avatar, Box, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, ListSubheader,
  Menu, MenuItem, Toolbar, Typography, useMediaQuery, useTheme,
} from '@mui/material'
import MenuIcon from '@mui/icons-material/Menu'
import DashboardIcon from '@mui/icons-material/SpaceDashboard'
import LocalShippingIcon from '@mui/icons-material/LocalShipping'
import Inventory2Icon from '@mui/icons-material/Inventory2'
import AccountTreeIcon from '@mui/icons-material/AccountTreeOutlined'
import BusinessIcon from '@mui/icons-material/BusinessOutlined'
import ShoppingCartIcon from '@mui/icons-material/ShoppingCartOutlined'
import MoveToInboxIcon from '@mui/icons-material/MoveToInbox'
import WarehouseIcon from '@mui/icons-material/WarehouseOutlined'
import SwapHorizIcon from '@mui/icons-material/SwapHoriz'
import TuneIcon from '@mui/icons-material/Tune'
import FactCheckIcon from '@mui/icons-material/FactCheckOutlined'
import NotificationsIcon from '@mui/icons-material/NotificationsActiveOutlined'
import TrendingUpIcon from '@mui/icons-material/TrendingUp'
import PeopleIcon from '@mui/icons-material/PeopleAltOutlined'
import RequestQuoteIcon from '@mui/icons-material/RequestQuoteOutlined'
import ReceiptIcon from '@mui/icons-material/ReceiptLongOutlined'
import LocalShippingOutlinedIcon from '@mui/icons-material/LocalShippingOutlined'
import ListAltIcon from '@mui/icons-material/ListAlt'
import AccountBalanceIcon from '@mui/icons-material/AccountBalanceOutlined'
import AdminPanelSettingsIcon from '@mui/icons-material/AdminPanelSettingsOutlined'
import FactHistoryIcon from '@mui/icons-material/ManageHistoryOutlined'
import type { ReactNode } from 'react'
import { useAuth } from '../features/auth/AuthContext'
import { PERM } from '../config/contract'

const WIDTH = 232
interface NavigationItem {
  to: string
  label: string
  icon: ReactNode
  permissions?: string[]
  requireAny?: boolean
}
interface NavigationGroup { title: string; items: NavigationItem[] }
const groups: NavigationGroup[] = [
  { title: '', items: [{ to: '/', label: 'Panel', icon: <DashboardIcon /> }] },
  {
    title: 'Maestros',
    items: [
      { to: '/proveedores', label: 'Proveedores', icon: <LocalShippingIcon />, permissions: [PERM.proveedoresLeer] },
      { to: '/productos', label: 'Productos', icon: <Inventory2Icon />, permissions: [PERM.productosLeer] },
      { to: '/categorias', label: 'Categorías', icon: <AccountTreeIcon />, permissions: [PERM.categoriasLeer] },
      { to: '/departamentos', label: 'Departamentos', icon: <BusinessIcon />, permissions: [PERM.departamentosLeer] },
      { to: '/almacenes', label: 'Almacenes', icon: <WarehouseIcon />, permissions: [PERM.almacenesLeer] },
    ],
  },
  {
    title: 'Compras',
    items: [
      { to: '/ordenes-compra', label: 'Órdenes de compra', icon: <ShoppingCartIcon />, permissions: [PERM.ordenesLeer] },
      { to: '/recepciones', label: 'Recepción de bienes', icon: <MoveToInboxIcon />, permissions: [PERM.recepcionesLeer] },
    ],
  },
  {
    title: 'Inventario',
    items: [
      { to: '/inventario/existencias', label: 'Existencias', icon: <WarehouseIcon />, permissions: [PERM.existenciasLeer] },
      { to: '/inventario/movimientos', label: 'Movimientos', icon: <SwapHorizIcon />, permissions: [PERM.movimientosLeer] },
      { to: '/inventario/ajustes', label: 'Ajustes', icon: <TuneIcon />, permissions: [PERM.ajustesLeer] },
      { to: '/inventario/conteos', label: 'Conteos físicos', icon: <FactCheckIcon />, permissions: [PERM.conteosLeer] },
      { to: '/inventario/alertas', label: 'Alertas de stock', icon: <NotificationsIcon />, permissions: [PERM.alertasLeer] },
      { to: '/inventario/rotacion', label: 'Rotación', icon: <TrendingUpIcon />, permissions: [PERM.reportesInventarioLeer] },
    ],
  },
  {
    title: 'Reportería',
    items: [{ to: '/reportes', label: 'Informes', icon: <TrendingUpIcon />, permissions: [PERM.facturaProveedorLeer, PERM.facturasLeer, PERM.alertasLeer, PERM.reportesInventarioLeer, PERM.reportesContablesLeer], requireAny: true }],
  },
  {
    title: 'Ventas',
    items: [
      { to: '/ventas/clientes', label: 'Clientes', icon: <PeopleIcon />, permissions: [PERM.clientesLeer] },
      { to: '/ventas/cotizaciones', label: 'Cotizaciones', icon: <RequestQuoteIcon />, permissions: [PERM.cotizacionesLeer] },
      { to: '/ventas/pedidos', label: 'Órdenes de venta', icon: <ListAltIcon />, permissions: [PERM.pedidosLeer] },
      { to: '/ventas/despachos', label: 'Despachos', icon: <LocalShippingOutlinedIcon />, permissions: [PERM.despachoLeer] },
      { to: '/ventas/facturas', label: 'Facturas', icon: <ReceiptIcon />, permissions: [PERM.facturasLeer] },
    ],
  },
  {
    title: 'Contabilidad',
    items: [
      { to: '/contabilidad', label: 'Contabilidad', icon: <AccountBalanceIcon />, permissions: Object.values(PERM).filter((permission) => permission.startsWith('CONTABILIDAD_')), requireAny: true },
    ],
  },
  {
    title: 'Sistema',
    items: [
      { to: '/notificaciones', label: 'Notificaciones', icon: <NotificationsIcon /> },
      { to: '/auditoria', label: 'Auditoría', icon: <FactHistoryIcon />, permissions: [PERM.seguridadAuditoriaLeer] },
      { to: '/seguridad', label: 'Seguridad', icon: <AdminPanelSettingsIcon />, permissions: [PERM.seguridadUsuariosAdmin, PERM.seguridadRolesAdmin, PERM.seguridadPermisosAdmin], requireAny: true },
    ],
  },
]

export default function AppLayout() {
  const theme = useTheme()
  const desktop = useMediaQuery(theme.breakpoints.up('md'))
  const [open, setOpen] = useState(false)
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const [signingOut, setSigningOut] = useState(false)
  const { user, logout, can } = useAuth()
  const { pathname } = useLocation()
  const integratedPaths = ['/proveedores', '/productos', '/categorias', '/departamentos', '/almacenes', '/ventas/clientes', '/ordenes-compra', '/recepciones', '/seguridad', '/auditoria', '/notificaciones']
  const showIntegrationNotice = pathname !== '/' && !integratedPaths.some((path) => pathname.startsWith(path))

  const nav = (
    <Box sx={{ width: WIDTH, pb: 2 }}>
      <Toolbar><Typography sx={{ fontWeight: 700 }} color="primary">TechSupply ERP</Typography></Toolbar>
      <Divider />
      {groups.map((g) => {
        const visibleItems = g.items.filter((item) => !item.permissions || (item.requireAny ? item.permissions.some(can) : item.permissions.every(can)))
        if (!visibleItems.length) return null
        return (
        <List key={g.title || 'main'} dense subheader={g.title ? <ListSubheader sx={{ lineHeight: '32px' }}>{g.title}</ListSubheader> : undefined}>
          {visibleItems.map((i) => (
            <ListItemButton
              key={i.to}
              component={NavLink}
              to={i.to}
              selected={i.to === '/' ? pathname === '/' : pathname.startsWith(i.to)}
              onClick={() => setOpen(false)}
            >
              <ListItemIcon>
                {i.icon}
              </ListItemIcon>
              <ListItemText primary={i.label} />
            </ListItemButton>
          ))}
        </List>
      )})}
    </Box>
  )

  return (
    <Box sx={{ display: 'flex', minHeight: '100%' }}>
      <AppBar position="fixed" elevation={0} sx={{ ml: { md: `${WIDTH}px` }, width: { md: `calc(100% - ${WIDTH}px)` }, bgcolor: 'background.paper', color: 'text.primary', borderBottom: 1, borderColor: 'divider' }}>
        <Toolbar>
          {!desktop && <IconButton edge="start" onClick={() => setOpen(true)} aria-label="Abrir menú"><MenuIcon /></IconButton>}
          <Box sx={{ flexGrow: 1 }} />
          <Typography variant="body2" sx={{ mr: 1.5, display: { xs: 'none', sm: 'block' } }}>{user?.nombre}</Typography>
          <IconButton onClick={(e) => setAnchor(e.currentTarget)} aria-label="Menú de usuario">
            <Avatar sx={{ width: 32, height: 32, bgcolor: 'primary.main' }}>{(user?.nombre || '?')[0].toUpperCase()}</Avatar>
          </IconButton>
          <Menu anchorEl={anchor} open={!!anchor} onClose={() => setAnchor(null)}>
            <MenuItem disabled>{user?.roles.join(", ") || "Sin rol"}</MenuItem>
            <MenuItem disabled={signingOut} onClick={async () => {
              setSigningOut(true)
              try { await logout() } finally { setSigningOut(false); setAnchor(null) }
            }}>Cerrar sesión</MenuItem>
          </Menu>
        </Toolbar>
      </AppBar>
      <Drawer
        variant={desktop ? 'permanent' : 'temporary'}
        open={desktop || open}
        onClose={() => setOpen(false)}
        sx={{ width: desktop ? WIDTH : 0, '& .MuiDrawer-paper': { width: WIDTH, boxSizing: 'border-box' } }}
      >
        {nav}
      </Drawer>
      <Box component="main" sx={{ flexGrow: 1, minWidth: 0, p: { xs: 2, md: 3 } }}>
        <Toolbar />
        {showIntegrationNotice && <Alert severity="info" sx={{ mb: 2 }}>
          Esta sección está en proceso de integración con el servidor.
        </Alert>}
        <Outlet />
      </Box>
    </Box>
  )
}
