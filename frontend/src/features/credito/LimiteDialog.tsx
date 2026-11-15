import { useForm } from 'react-hook-form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../api/http'
import { PATHS } from '../../config/contract'
import type { Row } from '../../shared/api'
import { useNotify } from '../../shared/Notify'

interface Values { limiteCredito: string; plazoPagoDias: string; motivo: string }

export default function LimiteDialog({ row, onClose }: { row: Row; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { register, handleSubmit, formState: { errors } } = useForm<Values>({
    defaultValues: { limiteCredito: String(row.limiteCredito ?? ''), plazoPagoDias: String(row.plazoPagoDias ?? ''), motivo: '' },
  })
  const save = useMutation({
    mutationFn: (v: Values) =>
      http.patch(`${PATHS.credito}/clientes/${row.id}/limite`, {
        limiteCredito: Number(v.limiteCredito), plazoPagoDias: v.plazoPagoDias === '' ? null : Number(v.plazoPagoDias), motivo: v.motivo.trim(),
      }),
    onSuccess: () => {
      for (const k of [[PATHS.credito], ['credito'], [PATHS.clientes]]) qc.invalidateQueries({ queryKey: k })
      notify('Condiciones de crédito actualizadas.')
      onClose()
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
        <DialogTitle>Límite y plazo de crédito</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gap: 2, pt: 1 }}>
            <Typography variant="body2">{String(row.clienteNombre)}</Typography>
            <TextField label="Límite de crédito" type="number" size="small" required slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
              error={!!errors.limiteCredito} helperText={errors.limiteCredito?.message}
              {...register('limiteCredito', { validate: (v) => (v !== '' && Number(v) >= 0) || 'Indica un valor igual o mayor a 0' })} />
            <TextField label="Plazo de pago (días)" type="number" size="small" slotProps={{ htmlInput: { min: 0, step: 1 } }} {...register('plazoPagoDias')} />
            <TextField label="Motivo del cambio" size="small" required multiline minRows={2} error={!!errors.motivo} helperText={errors.motivo?.message}
              {...register('motivo', { validate: (v) => v.trim() !== '' || 'Indica el motivo' })} />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={save.isPending}>{save.isPending ? 'Guardando…' : 'Guardar'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
