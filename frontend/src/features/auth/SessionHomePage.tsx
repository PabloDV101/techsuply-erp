import { Alert, Box, Typography } from '@mui/material'
import { useAuth } from './AuthContext'

export default function SessionHomePage() {
  const { user } = useAuth()
  return (
    <Box>
      <Typography variant="h5" gutterBottom>Hola, {user?.nombre}</Typography>
      <Alert severity="info">Las secciones del menú están en proceso de integración.</Alert>
    </Box>
  )
}
