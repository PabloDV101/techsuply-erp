import { useState } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Box, Button, CircularProgress, Link, Paper, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../api/http'
import { PATHS } from '../../config/contract'

const password = z.string()
  .min(12, 'Usa al menos 12 caracteres')
  .refine((value) => new TextEncoder().encode(value).length <= 72, 'La contraseña no debe superar 72 bytes UTF-8')

const schema = z.object({
  identificador: z.string().min(1, 'Ingresa tu usuario o correo').max(150),
  claveRecuperacion: z.string().min(32, 'La clave de recuperación debe tener al menos 32 caracteres').max(128),
  contrasenaNueva: password,
  confirmarContrasena: z.string().min(1, 'Confirma la contraseña nueva'),
}).refine((values) => values.contrasenaNueva === values.confirmarContrasena, {
  path: ['confirmarContrasena'],
  message: 'Las contraseñas nuevas no coinciden',
})

type Values = z.infer<typeof schema>

export default function LocalAccountRecoveryPage() {
  const [error, setError] = useState('')
  const [complete, setComplete] = useState(false)
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) })

  const onSubmit = async ({ identificador, claveRecuperacion, contrasenaNueva }: Values) => {
    setError('')
    try {
      await http.post(PATHS.localAccountRecovery, { identificador, claveRecuperacion, contrasenaNueva })
      setComplete(true)
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  if (complete) {
    return (
      <Box sx={{ maxWidth: 520, mx: 'auto' }}>
        <Paper sx={{ p: { xs: 2, sm: 4 } }}>
          <Alert severity="success" sx={{ mb: 2 }}>
            Si la cuenta existe y la clave de recuperación es correcta, la contraseña se actualizó.
          </Alert>
          <Button component={RouterLink} to="/login" variant="contained" fullWidth>Volver a iniciar sesión</Button>
        </Paper>
      </Box>
    )
  }

  return (
    <Box sx={{ maxWidth: 520, mx: 'auto' }}>
      <Paper component="form" onSubmit={handleSubmit(onSubmit)} noValidate sx={{ p: { xs: 2, sm: 4 } }}>
        <Typography variant="h5" gutterBottom>Recuperar cuenta local</Typography>
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          Esta opción solo funciona en este equipo y requiere la clave de recuperación local.
          El cambio cierra las sesiones activas. La cuenta seguirá inactiva si fue deshabilitada.
        </Typography>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <TextField
          label="Usuario o correo"
          fullWidth
          margin="normal"
          autoComplete="username"
          error={!!errors.identificador}
          helperText={errors.identificador?.message}
          {...register('identificador')}
        />
        <TextField
          label="Clave de recuperación local"
          type="password"
          fullWidth
          margin="normal"
          autoComplete="off"
          error={!!errors.claveRecuperacion}
          helperText={errors.claveRecuperacion?.message}
          {...register('claveRecuperacion')}
        />
        <TextField
          label="Contraseña nueva"
          type="password"
          fullWidth
          margin="normal"
          autoComplete="new-password"
          error={!!errors.contrasenaNueva}
          helperText={errors.contrasenaNueva?.message ?? 'Mínimo 12 caracteres; máximo 72 bytes UTF-8.'}
          {...register('contrasenaNueva')}
        />
        <TextField
          label="Confirmar contraseña nueva"
          type="password"
          fullWidth
          margin="normal"
          autoComplete="new-password"
          error={!!errors.confirmarContrasena}
          helperText={errors.confirmarContrasena?.message}
          {...register('confirmarContrasena')}
        />
        <Button type="submit" variant="contained" fullWidth size="large" disabled={isSubmitting} sx={{ mt: 2 }}>
          {isSubmitting ? <CircularProgress size={24} color="inherit" /> : 'Restablecer contraseña'}
        </Button>
        <Link component={RouterLink} to="/login" underline="hover" sx={{ display: 'block', mt: 2, textAlign: 'center' }}>
          Volver al inicio de sesión
        </Link>
      </Paper>
    </Box>
  )
}
