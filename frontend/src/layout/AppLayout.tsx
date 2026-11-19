import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import {
  AppBar, Avatar, Box, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, ListSubheader,
  Menu, MenuItem, Toolbar, Typography, useMediaQuery, useTheme,
} from '@mui/material'
import MenuIcon from '@mui/icons-material/Menu'
import DashboardIcon from '@mui/icons-material/SpaceDashboard'
import LocalShippingIcon from '@mui/icons-material/LocalShipping'
import Inventory2Icon from '@mui/icons-material/Inventory2'
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
import CreditScoreIcon from '@mui/icons-material/CreditScoreOutlined'
import AccountBalanceIcon from '@mui/icons-material/AccountBalanceOutlined'
import { useAuth } from '../features/auth/AuthContext'

const WIDTH = 232
const groups = [
  { title: '', items: [{ to: '/', label: 'Panel', icon: <DashboardIcon /> }] },
  {
    title: 'Maestros',
    items: [
      { to: '/proveedores', label: 'Proveedores', icon: <LocalShippingIcon /> },
      { to: '/productos', label: 'Productos', icon: <Inventory2Icon /> },
    ],
  },
  {
    title: 'Compras',
    items: [
      { to: '/ordenes-compra', label: 'Órdenes de compra', icon: <ShoppingCartIcon /> },
      { to: '/recepciones', label: 'Recepción de bienes', icon: <MoveToInboxIcon /> },
    ],
  },
  {
    title: 'Inventario',
    items: [
      { to: '/inventario/existencias', label: 'Existencias', icon: <WarehouseIcon /> },
      { to: '/inventario/movimientos', label: 'Movimientos', icon: <SwapHorizIcon /> },
      { to: '/inventario/ajustes', label: 'Ajustes', icon: <TuneIcon /> },
      { to: '/inventario/conteos', label: 'Conteos físicos', icon: <FactCheckIcon /> },
      { to: '/inventario/alertas', label: 'Alertas de stock', icon: <NotificationsIcon /> },
      { to: '/inventario/rotacion', label: 'Rotación', icon: <TrendingUpIcon /> },
    ],
  },
  {
    title: 'Ventas',
    items: [
      { to: '/ventas/clientes', label: 'Clientes', icon: <PeopleIcon /> },
      { to: '/ventas/cotizaciones', label: 'Cotizaciones', icon: <RequestQuoteIcon /> },
      { to: '/ventas/pedidos', label: 'Órdenes de venta', icon: <ListAltIcon /> },
      { to: '/ventas/despachos', label: 'Despachos', icon: <LocalShippingOutlinedIcon /> },
      { to: '/ventas/facturas', label: 'Facturas', icon: <ReceiptIcon /> },
      { to: '/ventas/credito', label: 'Gestión de crédito', icon: <CreditScoreIcon /> },
    ],
  },
  {
    title: 'Contabilidad',
    items: [
      { to: '/contabilidad', label: 'Plan, asientos y reportes', icon: <AccountBalanceIcon /> },
    ],
  },
]

export default function AppLayout() {
  const theme = useTheme()
  const desktop = useMediaQuery(theme.breakpoints.up('md'))
  const [open, setOpen] = useState(false)
  const [anchor, setAnchor] = useState<HTMLElement | null>(null)
  const { user, logout } = useAuth()
  const { pathname } = useLocation()

  const nav = (
    <Box sx={{ width: WIDTH, pb: 2 }}>
      <Toolbar><Typography sx={{ fontWeight: 700 }} color="primary">TechSupply ERP</Typography></Toolbar>
      <Divider />
      {groups.map((g) => (
        <List key={g.title || 'main'} dense subheader={g.title ? <ListSubheader sx={{ lineHeight: '32px' }}>{g.title}</ListSubheader> : undefined}>
          {g.items.map((i) => (
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
      ))}
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
            <MenuItem onClick={logout}>Cerrar sesión</MenuItem>
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
        <Outlet />
      </Box>
    </Box>
  )
}
