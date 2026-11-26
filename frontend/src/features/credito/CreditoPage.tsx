import { Alert, Box, Typography } from '@mui/material'

/** El backend no ofrece crédito operativo; esta pantalla evita simularlo. */
export default function CreditoPage() {
  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 2 }}>Gestión de crédito</Typography>
      <Alert severity="info">El crédito operativo todavía no está disponible en el backend.</Alert>
    </Box>
  )
}
