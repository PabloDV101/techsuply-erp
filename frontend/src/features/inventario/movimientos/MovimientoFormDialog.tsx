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

interface Values {
  tipo: 'ENTRADA' | 'SALIDA' | 'TRASLADO'
  productoId: string
  almacenId: string
  almacenDestinoId: string
  cantidad: string
  motivo: string
  referencia: string
}

export default function MovimientoFormDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { control, register, handleSubmit, setError, formState: { errors } } = useForm<Values>({
    defaultValues: { tipo: 'ENTRADA', productoId: '', almacenId: '', almacenDestinoId: '', cantidad: '', motivo: '', referencia: '' },
  })
  const [tipo, productoId, almacenId, cantidad] = useWatch({ control, name: ['tipo', 'productoId', 'almacenId', 'cantidad'] })
  const stock = useStock(productoId, almacenId)
  const sale = tipo !== 'ENTRADA'

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => http.post(PATHS.movimientos, body),
    onSuccess: () => {
      for (const k of [[PATHS.movimientos], [PATHS.existencias], [PATHS.alertas], ['stock']]) qc.invalidateQueries({ queryKey: k })
      notify('Movimiento registrado.')
      onClose()
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  const submit = (v: Values) => {
    const qty = Number(v.cantidad)
    if (v.tipo === 'TRASLADO' && v.almacenDestinoId === v.almacenId) {
      setError('almacenDestinoId', { message: 'El destino debe ser distinto al origen' })
      return
    }
    if (sale && stock.data && qty > stock.data.disponible) {
      setError('cantidad', { message: `Solo hay ${num(stock.data.disponible)} disponibles` })
      return
    }
    save.mutate({
      tipo: v.tipo,
      productoId: idValue(v.productoId),
      almacenId: idValue(v.almacenId),
      almacenDestinoId: v.tipo === 'TRASLADO' ? idValue(v.almacenDestinoId) : null,
      cantidad: qty,
      motivo: v.motivo.trim(),
      referencia: v.referencia.trim() || null,
    })
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <DialogTitle>Registrar movimiento</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gap: 2, pt: 1 }}>
            <Controller name="tipo" control={control} render={({ field }) => (
              <TextField select size="small" label="Tipo de movimiento" {...field}>
                <MenuItem value="ENTRADA">Entrada</MenuItem>
                <MenuItem value="SALIDA">Salida</MenuItem>
                <MenuItem value="TRASLADO">Traslado entre almacenes</MenuItem>
              </TextField>
            )} />
            <Controller name="productoId" control={control} rules={{ required: 'Elige un producto' }} render={({ field, fieldState }) => (
              <ProductoSelect required value={field.value} onChange={field.onChange} error={!!fieldState.error} helperText={fieldState.error?.message} />
            )} />
            <Controller name="almacenId" control={control} rules={{ required: 'Elige un almacén' }} render={({ field, fieldState }) => (
              <AlmacenSelect required label={tipo === 'TRASLADO' ? 'Almacén de origen' : 'Almacén'} value={field.value} onChange={field.onChange}
                error={!!fieldState.error} helperText={fieldState.error?.message} />
            )} />
            {tipo === 'TRASLADO' && (
              <Controller name="almacenDestinoId" control={control} rules={{ required: 'Elige el destino' }} render={({ field, fieldState }) => (
                <AlmacenSelect required label="Almacén de destino" value={field.value} onChange={field.onChange}
                  error={!!fieldState.error} helperText={fieldState.error?.message} />
              )} />
            )}
            {stock.data && (
              <Alert severity="info" icon={false}>
                Disponible en el almacén: <b>{num(stock.data.disponible)}</b>
                {sale && Number(cantidad) > 0 && ` · Quedaría en ${num(stock.data.disponible - Number(cantidad))}`}
              </Alert>
            )}
            <TextField label="Cantidad" type="number" size="small" required slotProps={{ htmlInput: { min: 0, step: 'any' } }}
              error={!!errors.cantidad} helperText={errors.cantidad?.message}
              {...register('cantidad', { validate: (v) => Number(v) > 0 || 'Debe ser mayor que 0' })} />
            <TextField label="Motivo" size="small" required error={!!errors.motivo} helperText={errors.motivo?.message}
              {...register('motivo', { required: 'Indica el motivo' })} />
            <TextField label="Referencia (documento, opcional)" size="small" {...register('referencia')} />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={save.isPending}>{save.isPending ? 'Guardando…' : 'Registrar'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
