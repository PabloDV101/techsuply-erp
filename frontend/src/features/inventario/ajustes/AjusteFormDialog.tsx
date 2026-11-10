import { Controller, useForm, useWatch } from 'react-hook-form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, TextField } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { idValue } from '../../../shared/api'
import { num } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { AlmacenSelect, ProductoSelect } from '../../../shared/selects'
import { useStock } from '../../../shared/useStock'

export const MOTIVOS_AJUSTE = [
  { value: 'MERMA', label: 'Merma' },
  { value: 'DANO', label: 'Daño o deterioro' },
  { value: 'ROBO', label: 'Robo o extravío' },
  { value: 'ERROR_CONTEO', label: 'Error de conteo' },
  { value: 'OTRO', label: 'Otro' },
]

interface Values { productoId: string; almacenId: string; cantidadReal: string; motivo: string; observaciones: string }

export default function AjusteFormDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { control, register, handleSubmit, formState: { errors } } = useForm<Values>({
    defaultValues: { productoId: '', almacenId: '', cantidadReal: '', motivo: 'ERROR_CONTEO', observaciones: '' },
  })
  const [productoId, almacenId, real, motivo] = useWatch({ control, name: ['productoId', 'almacenId', 'cantidadReal', 'motivo'] })
  const stock = useStock(productoId, almacenId)
  const sistema = stock.data?.fisica
  const diff = sistema === undefined || real === '' ? undefined : Number(real) - sistema

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => http.post(PATHS.ajustes, body),
    onSuccess: () => {
      for (const k of [[PATHS.ajustes], [PATHS.movimientos], [PATHS.existencias], [PATHS.alertas], ['stock']]) qc.invalidateQueries({ queryKey: k })
      notify('Ajuste registrado.')
      onClose()
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  const submit = (v: Values) => {
    if (!diff) return
    save.mutate({
      productoId: idValue(v.productoId),
      almacenId: idValue(v.almacenId),
      tipo: diff > 0 ? 'POSITIVO' : 'NEGATIVO',
      cantidad: Math.abs(diff),
      motivo: v.motivo,
      observaciones: v.observaciones.trim() || null,
    })
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <DialogTitle>Nuevo ajuste de inventario</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gap: 2, pt: 1 }}>
            <Controller name="productoId" control={control} rules={{ required: 'Elige un producto' }} render={({ field, fieldState }) => (
              <ProductoSelect required value={field.value} onChange={field.onChange} error={!!fieldState.error} helperText={fieldState.error?.message} />
            )} />
            <Controller name="almacenId" control={control} rules={{ required: 'Elige un almacén' }} render={({ field, fieldState }) => (
              <AlmacenSelect required value={field.value} onChange={field.onChange} error={!!fieldState.error} helperText={fieldState.error?.message} />
            )} />
            {sistema !== undefined && <Alert severity="info" icon={false}>Cantidad en sistema: <b>{num(sistema)}</b></Alert>}
            <TextField label="Cantidad real (contada)" type="number" size="small" required slotProps={{ htmlInput: { min: 0, step: 'any' } }}
              error={!!errors.cantidadReal} helperText={errors.cantidadReal?.message}
              {...register('cantidadReal', { validate: (v) => (v !== '' && Number(v) >= 0) || 'Indica la cantidad real' })} />
            {diff !== undefined && (
              <Alert severity={diff === 0 ? 'warning' : diff > 0 ? 'success' : 'error'} icon={false}>
                {diff === 0 ? 'La cantidad real coincide con el sistema: no hay nada que ajustar.'
                  : `Se registrará un ajuste de ${diff > 0 ? '+' : '−'}${num(Math.abs(diff))}.`}
              </Alert>
            )}
            <Controller name="motivo" control={control} render={({ field }) => (
              <TextField select size="small" label="Motivo" {...field}>
                {MOTIVOS_AJUSTE.map((m) => <MenuItem key={m.value} value={m.value}>{m.label}</MenuItem>)}
              </TextField>
            )} />
            <TextField label={motivo === 'OTRO' ? 'Explica el motivo' : 'Observaciones (opcional)'} size="small" multiline minRows={2}
              required={motivo === 'OTRO'} error={!!errors.observaciones} helperText={errors.observaciones?.message}
              {...register('observaciones', { validate: (v) => motivo !== 'OTRO' || v.trim() !== '' || 'Explica el motivo' })} />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={!diff || save.isPending}>{save.isPending ? 'Guardando…' : 'Registrar ajuste'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
