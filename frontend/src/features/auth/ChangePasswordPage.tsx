import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { useForm } from 'react-hook-form'
import { z } from 'zod'
import { zodResolver } from '@hookform/resolvers/zod'
import { Alert, Box, Button, CircularProgress, Paper, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../api/http'
import { PATHS } from '../../config/contract'
import { useAuth } from './AuthContext'

const password = z.string()
  .min(12, 'Usa al menos 12 caracteres')
  .refine((value) => new TextEncoder().encode(value).length <= 72, 'La contraseña no debe superar 72 bytes UTF-8')

const schema = z.object({
  contrasenaActual: z.string().min(1, 'Ingresa tu contraseña actual').max(72),
  contrasenaNueva: password,
  confirmarContrasena: z.string().min(1, 'Confirma la contraseña nueva'),
}).refine((values) => values.contrasenaNueva === values.confirmarContrasena, {
  path: ['confirmarContrasena'],
  message: 'Las contraseñas nuevas no coinciden',
})

type Values = z.infer<typeof schema>

export default function ChangePasswordPage() {
  const navigate = useNavigate()
  const { clearLocalSession } = useAuth()
  const [error, setError] = useState('')
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) })

  const onSubmit = async ({ contrasenaActual, contrasenaNueva }: Values) => {
    setError('')
    try {
      await http.post(PATHS.changePassword, { contrasenaActual, contrasenaNueva })
      clearLocalSession()
      navigate('/login', {
        replace: true,
        state: { message: 'Contraseña actualizada. Inicia sesión con tu nueva contraseña.' },
      })
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <Box sx={{ maxWidth: 520, mx: 'auto' }}>
      <Paper component="form" onSubmit={handleSubmit(onSubmit)} noValidate sx={{ p: { xs: 2, sm: 4 } }}>
        <Typography variant="h5" gutterBottom>Cambiar contraseña</Typography>
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          Usa al menos 12 caracteres. Al actualizarla, se cerrarán tus sesiones y tendrás que iniciar sesión de nuevo.
        </Typography>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <TextField
          label="Contraseña actual"
          type="password"
          fullWidth
          margin="normal"
          autoComplete="current-password"
          error={!!errors.contrasenaActual}
          helperText={errors.contrasenaActual?.message}
          {...register('contrasenaActual')}
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
          {isSubmitting ? <CircularProgress size={24} color="inherit" /> : 'Actualizar contraseña'}
        </Button>
      </Paper>
    </Box>
  )
}
