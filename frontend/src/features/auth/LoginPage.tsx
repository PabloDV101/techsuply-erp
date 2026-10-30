import { useState } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Box, Button, CircularProgress, Paper, TextField, Typography } from '@mui/material'
import { useAuth } from './AuthContext'
import { errorMessage } from '../../api/http'

const schema = z.object({
  identificador: z.string().min(1, 'Ingresa tu usuario o correo'),
  password: z.string().min(1, 'Ingresa tu contraseña'),
})
type Values = z.infer<typeof schema>

export default function LoginPage() {
  const { user, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [error, setError] = useState('')
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) })

  if (user) return <Navigate to="/" replace />

  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const onSubmit = async (v: Values) => {
    setError('')
    try {
      await login(v.identificador, v.password)
      navigate(from, { replace: true })
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <Box sx={{ minHeight: '100%', display: 'grid', gridTemplateColumns: { md: '1fr 1fr' } }}>
      <Box
        sx={{
          display: { xs: 'none', md: 'flex' },
          flexDirection: 'column',
          justifyContent: 'flex-end',
          p: 6,
          bgcolor: 'primary.main',
          color: 'primary.contrastText',
        }}
      >
        <Typography sx={{ fontWeight: 700 }} variant="h3" >TechSupply ERP</Typography>
        <Typography sx={{ mt: 1, opacity: 0.85, maxWidth: 380 }}>
          Compras, inventario, ventas y contabilidad en un solo lugar.
        </Typography>
      </Box>
      <Box sx={{ display: 'grid', placeItems: 'center', p: 2 }}>
        <Paper component="form" onSubmit={handleSubmit(onSubmit)} noValidate elevation={0} sx={{ p: 4, width: '100%', maxWidth: 400 }}>
          <Typography variant="h5" gutterBottom>Iniciar sesión</Typography>
          {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
          <TextField
            label="Usuario o correo"
            fullWidth
            margin="normal"
            autoFocus
            autoComplete="username"
            error={!!errors.identificador}
            helperText={errors.identificador?.message}
            {...register('identificador')}
          />
          <TextField
            label="Contraseña"
            type="password"
            fullWidth
            margin="normal"
            autoComplete="current-password"
            error={!!errors.password}
            helperText={errors.password?.message}
            {...register('password')}
          />
          <Button type="submit" variant="contained" fullWidth size="large" disabled={isSubmitting} sx={{ mt: 2 }}>
            {isSubmitting ? <CircularProgress size={24} color="inherit" /> : 'Entrar'}
          </Button>
        </Paper>
      </Box>
    </Box>
  )
}
