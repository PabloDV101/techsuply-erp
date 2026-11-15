import { Controller, useForm, useWatch } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { idValue } from '../../../shared/api'
import { money, today } from '../../../shared/format'
import LineasForm, { emptyLine, lineTotal, type DocValues } from '../../../shared/LineasForm'
import { useNotify } from '../../../shared/Notify'
import { AlmacenSelect, ClienteSelect } from '../../../shared/selects'
import { useCredito } from '../../../shared/useCredito'
import { DOC, type DocKind } from './docConfig'

/** Carga el documento (si se edita) y muestra el formulario cuando está listo. */
export default function DocFormDialog({ kind, id, onClose }: { kind: DocKind; id?: number | string; onClose: () => void }) {
  const cfg = DOC[kind]
  const { data, isLoading, error } = useQuery({
    queryKey: [kind, id],
    enabled: id !== undefined,
    queryFn: async () => (await http.get(`${cfg.path}/${id}`)).data,
  })
  if (id !== undefined && (isLoading || error)) {
    return (
      <Dialog open onClose={onClose}>
        <DialogContent>{error ? <Typography color="error">{errorMessage(error)}</Typography> : 'Cargando…'}</DialogContent>
        <DialogActions><Button onClick={onClose}>Cerrar</Button></DialogActions>
      </Dialog>
    )
  }
  return <Form kind={kind} id={id} existing={data} onClose={onClose} />
}

function Form({ kind, id, existing, onClose }: { kind: DocKind; id?: number | string; existing?: any; onClose: () => void }) {
  const cfg = DOC[kind]
  const qc = useQueryClient()
  const notify = useNotify()

  const { control, register, handleSubmit, setValue, formState: { errors } } = useForm<DocValues>({
    defaultValues: {
      clienteId: String(existing?.cliente?.id ?? existing?.clienteId ?? ''),
      fecha: String(existing?.fecha ?? today()).slice(0, 10),
      vence: String(existing?.[cfg.venceKey] ?? '').slice(0, 10),
      almacenId: String(existing?.almacen?.id ?? existing?.almacenId ?? ''),
      observaciones: existing?.observaciones ?? '',
      detalles: existing?.detalles?.length
        ? existing.detalles.map((d: any) => ({
            productoId: String(d.producto?.id ?? d.productoId ?? ''),
            cantidad: String(d.cantidad ?? ''),
            precioUnitario: String(d.precioUnitario ?? ''),
            descuento: String(d.descuento ?? 0),
          }))
        : [emptyLine()],
    },
  })
  const clienteId = useWatch({ control, name: 'clienteId' })
  const lines = useWatch({ control, name: 'detalles' }) ?? []
  const total = lines.reduce((s, l) => s + lineTotal(l), 0)
  const credito = useCredito(kind === 'pedido' ? clienteId : '').data

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => (id === undefined ? http.post(cfg.path, body) : http.put(`${cfg.path}/${id}`, body)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [cfg.path] })
      qc.invalidateQueries({ queryKey: [kind, id] })
      notify(id === undefined ? `Se creó el ${cfg.singular} en borrador.` : `Se actualizó el ${cfg.singular}.`)
      onClose()
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  const submit = (v: DocValues) =>
    save.mutate({
      clienteId: idValue(v.clienteId),
      fecha: v.fecha,
      [cfg.venceKey]: v.vence || null,
      ...(kind === 'pedido' ? { almacenId: idValue(v.almacenId) } : {}),
      observaciones: v.observaciones.trim() || null,
      detalles: v.detalles.map((d) => ({
        productoId: idValue(d.productoId),
        cantidad: Number(d.cantidad),
        precioUnitario: Number(d.precioUnitario),
        descuento: Number(d.descuento) || 0,
      })),
    })

  const disponible = credito ? Number(credito.creditoDisponible ?? 0) : undefined

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <DialogTitle>{id === undefined ? `Nueva ${kind === 'pedido' ? 'orden de venta' : 'cotización'}` : `Editar ${existing?.numero ?? cfg.singular}`}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gridTemplateColumns: { sm: '2fr 1fr 1fr' }, gap: 2, pt: 1 }}>
            <Controller name="clienteId" control={control} rules={{ required: 'Elige un cliente' }} render={({ field, fieldState }) => (
              <ClienteSelect required value={field.value} onChange={field.onChange} error={!!fieldState.error} helperText={fieldState.error?.message} />
            )} />
            <TextField label="Fecha" type="date" size="small" required slotProps={{ inputLabel: { shrink: true } }}
              error={!!errors.fecha} helperText={errors.fecha?.message} {...register('fecha', { required: 'Indica la fecha' })} />
            <TextField label={cfg.venceLabel} type="date" size="small" slotProps={{ inputLabel: { shrink: true } }} {...register('vence')} />
            {kind === 'pedido' && (
              <Box sx={{ gridColumn: { sm: '1 / -1' } }}>
                <Controller name="almacenId" control={control} rules={{ required: 'Elige el almacén que despachará' }} render={({ field, fieldState }) => (
                  <AlmacenSelect required label="Almacén de despacho" value={field.value} onChange={field.onChange}
                    error={!!fieldState.error} helperText={fieldState.error?.message} />
                )} />
              </Box>
            )}
            {credito?.estado === 'BLOQUEADO' && (
              <Alert severity="error" sx={{ gridColumn: { sm: '1 / -1' } }}>Este cliente tiene el crédito bloqueado. Puedes guardar el borrador, pero no se podrá confirmar.</Alert>
            )}
            {credito?.estado !== 'BLOQUEADO' && disponible !== undefined && (
              <Alert severity={total > disponible ? 'warning' : 'info'} icon={false} sx={{ gridColumn: { sm: '1 / -1' } }}>
                Crédito disponible: <b>{money(disponible)}</b>
                {total > disponible && ' · El pedido excede el crédito: al confirmarlo quedará retenido hasta que Crédito lo libere.'}
              </Alert>
            )}
            <TextField label="Observaciones" size="small" multiline minRows={2} sx={{ gridColumn: { sm: '1 / -1' } }} {...register('observaciones')} />
          </Box>
          <LineasForm control={control} register={register} setValue={setValue} errors={errors} />
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={save.isPending}>{save.isPending ? 'Guardando…' : 'Guardar borrador'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
