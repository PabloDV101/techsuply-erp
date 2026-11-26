import { useState } from 'react'
import { Controller, useForm } from 'react-hook-form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { idValue } from '../../../shared/api'
import LineasForm, { emptyLine, type DocValues } from '../../../shared/LineasForm'
import { today } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { ClienteSelect } from '../../../shared/selects'

export default function DocFormDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const [idempotency, setIdempotency] = useState<{ fingerprint: string; key: string } | null>(null)
  const { control, register, handleSubmit, setValue, formState: { errors } } = useForm<DocValues>({
    defaultValues: { clienteId: '', vigenciaHasta: today(), detalles: [emptyLine()] },
  })

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => http.post(PATHS.cotizaciones, body),
    onSuccess: (response) => {
      void qc.invalidateQueries({ queryKey: [PATHS.cotizaciones] })
      notify(`Cotización ${response.data?.folio ?? ''} creada como borrador.`)
      onClose()
    },
    onError: (error) => notify(errorMessage(error), 'error'),
  })
  const submit = (values: DocValues) => {
    const payload = {
      clienteId: idValue(values.clienteId),
      vigenciaHasta: values.vigenciaHasta,
      detalles: values.detalles.map((line) => ({
        productoId: idValue(line.productoId),
        cantidad: Number(line.cantidad),
        precioUnitario: Number(line.precioUnitario),
        descuentoPct: Number(line.descuentoPct),
        tasaImpuesto: Number(line.tasaImpuesto),
      })),
    }
    const fingerprint = JSON.stringify(payload)
    const key = idempotency?.fingerprint === fingerprint ? idempotency.key : crypto.randomUUID()
    setIdempotency({ fingerprint, key })
    save.mutate({ ...payload, claveIdempotencia: key })
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="lg">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <DialogTitle>Nueva cotización</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gridTemplateColumns: { sm: '2fr 1fr' }, gap: 2, pt: 1 }}>
            <Controller name="clienteId" control={control} rules={{ required: 'Elige un cliente' }} render={({ field, fieldState }) => (
              <ClienteSelect required value={field.value} onChange={field.onChange} error={!!fieldState.error} helperText={fieldState.error?.message} />
            )} />
            <TextField label="Vigente hasta" type="date" size="small" required slotProps={{ inputLabel: { shrink: true } }}
              error={!!errors.vigenciaHasta} helperText={errors.vigenciaHasta?.message}
              {...register('vigenciaHasta', { required: 'Indica el último día de vigencia' })} />
          </Box>
          <LineasForm control={control} register={register} setValue={setValue} errors={errors} />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={save.isPending}>{save.isPending ? 'Guardando…' : 'Guardar cotización'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
