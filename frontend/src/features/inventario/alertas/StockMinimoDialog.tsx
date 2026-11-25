import { useForm } from 'react-hook-form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import type { Row } from '../../../shared/api'
import { productoLabel } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'

export default function StockMinimoDialog({ row, onClose }: { row: Row; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const existenciaId = row.existenciaId ?? row.id
  const { register, handleSubmit, formState: { errors } } = useForm<{ stockMinimo: string }>({
    defaultValues: { stockMinimo: String(row.stockMinimo ?? '') },
  })

  const save = useMutation({
    mutationFn: (v: { stockMinimo: string }) => http.patch(`${PATHS.existencias}/${existenciaId}/stock-minimo`, { stockMinimo: Number(v.stockMinimo) }),
    onSuccess: () => {
      for (const k of [[PATHS.alertas], [PATHS.existencias]]) qc.invalidateQueries({ queryKey: k })
      notify('Stock mínimo actualizado.')
      onClose()
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
        <DialogTitle>Stock mínimo</DialogTitle>
        <DialogContent>
          <Typography variant="body2" sx={{ mb: 2 }}>{productoLabel(row.producto)}</Typography>
          <TextField label="Stock mínimo" type="number" size="small" fullWidth autoFocus slotProps={{ htmlInput: { min: 0, step: 'any' } }}
            error={!!errors.stockMinimo} helperText={errors.stockMinimo?.message ?? 'Se genera una alerta cuando lo disponible baja de este valor.'}
            {...register('stockMinimo', { validate: (v) => (v !== '' && Number(v) >= 0) || 'Indica un valor igual o mayor a 0' })} />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={save.isPending}>{save.isPending ? 'Guardando…' : 'Guardar'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
