import { Alert, Box, Button } from '@mui/material'
import { Link, Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from './AuthContext'

/** Restringe una ruta por los permisos que el backend asignó en /auth/me. */
export default function PermissionGate({ permissions, requireAll = false, children }: {
  permissions: string[]
  requireAll?: boolean
  children: ReactNode
}) {
  const { user } = useAuth()
  const location = useLocation()
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />

  const allowed = requireAll
    ? permissions.every((permission) => user.permisos.includes(permission))
    : permissions.some((permission) => user.permisos.includes(permission))
  if (allowed) return children

  return (
    <Box sx={{ maxWidth: 640, mx: 'auto', mt: 4 }}>
      <Alert severity="warning" action={<Button component={Link} to="/" color="inherit" size="small">Volver al inicio</Button>}>
        Tu cuenta no tiene permiso para consultar esta sección.
      </Alert>
    </Box>
  )
}
