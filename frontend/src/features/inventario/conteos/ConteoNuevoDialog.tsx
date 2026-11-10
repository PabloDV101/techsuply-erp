import { Controller, useForm } from 'react-hook-form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { idValue } from '../../../shared/api'
import { useNotify } from '../../../shared/Notify'
import { AlmacenSelect } from '../../../shared/selects'

interface Values { almacenId: string; observaciones: string }

export default function ConteoNuevoDialog({ onClose, onCreated }: { onClose: () => void; onCreated: (id: number | string) => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { control, register, handleSubmit } = useForm<Values>({ defaultValues: { almacenId: '', observaciones: '' } })

  const save = useMutation({
    mutationFn: async (v: Values) =>
      (await http.post(PATHS.conteos, { almacenId: idValue(v.almacenId), observaciones: v.observaciones.trim() || null })).data,
    onSuccess: (data) => {
      qc.invalidateQueries({ queryKey: [PATHS.conteos] })
      notify('Conteo iniciado. Registra las cantidades contadas.')
      onCreated(data?.id)
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
        <DialogTitle>Nuevo conteo físico</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gap: 2, pt: 1 }}>
            <Typography variant="body2" color="text.secondary">
              Se tomará una foto de las cantidades del sistema en el almacén elegido para compararlas con lo que cuentes.
            </Typography>
            <Controller name="almacenId" control={control} rules={{ required: 'Elige un almacén' }} render={({ field, fieldState }) => (
              <AlmacenSelect required value={field.value} onChange={field.onChange} error={!!fieldState.error} helperText={fieldState.error?.message} />
            )} />
            <TextField label="Observaciones (opcional)" size="small" multiline minRows={2} {...register('observaciones')} />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={save.isPending}>{save.isPending ? 'Creando…' : 'Iniciar conteo'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
