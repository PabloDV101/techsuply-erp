import { useState } from 'react'
import { NavLink, Outlet, useLocation } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import {
  AppBar, Avatar, Badge, Box, Divider, Drawer, IconButton, List, ListItemButton, ListItemIcon, ListItemText, ListSubheader,
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
import PasswordIcon from '@mui/icons-material/Password'
import { useAuth } from '../features/auth/AuthContext'
import { http } from '../api/http'
import { PATHS } from '../config/contract'
import { normalizePage } from '../shared/api'

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
      { to: '/inventario/alertas', label: 'Alertas de stock', icon: <NotificationsIcon />, alert: true },
      { to: '/inventario/rotacion', label: 'Rotación', icon: <TrendingUpIcon /> },
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
  const alerts = useQuery({
    queryKey: ['total', PATHS.alertas, 'menu'],
    refetchInterval: 120_000,
    queryFn: async () => normalizePage((await http.get(PATHS.alertas, { params: { page: 0, size: 1 } })).data).total,
  })

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
                {'alert' in i && i.alert ? <Badge color="error" badgeContent={alerts.data ?? 0} max={99}>{i.icon}</Badge> : i.icon}
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
            <MenuItem component={NavLink} to="/cambiar-contrasena" onClick={() => setAnchor(null)}>
              <PasswordIcon fontSize="small" sx={{ mr: 1 }} />
              Cambiar contraseña
            </MenuItem>
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
