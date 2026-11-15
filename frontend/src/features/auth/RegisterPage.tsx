import { useState } from 'react'
import { Link as RouterLink, useNavigate } from 'react-router-dom'
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
  username: z.string().trim().min(1, 'Ingresa un usuario').max(60, 'Máximo 60 caracteres'),
  correo: z.string().trim().email('Ingresa un correo válido').max(150, 'Máximo 150 caracteres'),
  password,
  confirmarPassword: z.string().min(1, 'Confirma la contraseña'),
}).refine((values) => values.password === values.confirmarPassword, {
  path: ['confirmarPassword'],
  message: 'Las contraseñas no coinciden',
})

type Values = z.infer<typeof schema>

export default function RegisterPage() {
  const navigate = useNavigate()
  const [error, setError] = useState('')
  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<Values>({ resolver: zodResolver(schema) })

  const onSubmit = async ({ username, correo, password: rawPassword }: Values) => {
    setError('')
    try {
      await http.post(PATHS.register, { username, correo, password: rawPassword })
      navigate('/login', {
        replace: true,
        state: { message: 'Cuenta creada con acceso básico. Ya puedes iniciar sesión.' },
      })
    } catch (e) {
      setError(errorMessage(e))
    }
  }

  return (
    <Box sx={{ maxWidth: 520, mx: 'auto', p: 2 }}>
      <Paper component="form" onSubmit={handleSubmit(onSubmit)} noValidate sx={{ p: { xs: 2, sm: 4 } }}>
        <Typography variant="h5" gutterBottom>Crear cuenta</Typography>
        <Typography color="text.secondary" sx={{ mb: 2 }}>
          La cuenta nueva tendrá únicamente el rol básico, sin permisos administrativos.
        </Typography>
        {error && <Alert severity="error" sx={{ mb: 2 }}>{error}</Alert>}
        <TextField
          label="Usuario"
          fullWidth
          margin="normal"
          autoComplete="username"
          error={!!errors.username}
          helperText={errors.username?.message}
          {...register('username')}
        />
        <TextField
          label="Correo electrónico"
          type="email"
          fullWidth
          margin="normal"
          autoComplete="email"
          error={!!errors.correo}
          helperText={errors.correo?.message}
          {...register('correo')}
        />
        <TextField
          label="Contraseña"
          type="password"
          fullWidth
          margin="normal"
          autoComplete="new-password"
          error={!!errors.password}
          helperText={errors.password?.message ?? 'Mínimo 12 caracteres; máximo 72 bytes UTF-8.'}
          {...register('password')}
        />
        <TextField
          label="Confirmar contraseña"
          type="password"
          fullWidth
          margin="normal"
          autoComplete="new-password"
          error={!!errors.confirmarPassword}
          helperText={errors.confirmarPassword?.message}
          {...register('confirmarPassword')}
        />
        <Button type="submit" variant="contained" fullWidth size="large" disabled={isSubmitting} sx={{ mt: 2 }}>
          {isSubmitting ? <CircularProgress size={24} color="inherit" /> : 'Crear cuenta'}
        </Button>
        <Link component={RouterLink} to="/login" underline="hover" sx={{ display: 'block', mt: 2, textAlign: 'center' }}>
          Ya tengo cuenta
        </Link>
      </Paper>
    </Box>
  )
}
