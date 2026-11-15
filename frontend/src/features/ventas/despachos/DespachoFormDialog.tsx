import { useEffect } from 'react'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Autocomplete, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead,
  TableRow, TextField, Typography,
} from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { idValue, useLookup } from '../../../shared/api'
import { num, productoLabel, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'

interface Line { detalleId: string; productoId: string; nombre: string; pendiente: number; cantidad: string }
interface Values { pedidoId: string; observaciones: string; lineas: Line[] }

export default function DespachoFormDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  // Solo pedidos con reserva vigente y saldo por despachar.
  const confirmados = useLookup(PATHS.pedidos, { activo: undefined, estado: 'CONFIRMADO' }).data ?? []
  const parciales = useLookup(PATHS.pedidos, { activo: undefined, estado: 'DESPACHO_PARCIAL' }).data ?? []
  const pedidos = [...confirmados, ...parciales]

  const { control, register, handleSubmit, reset } = useForm<Values>({ defaultValues: { pedidoId: '', observaciones: '', lineas: [] } })
  const { fields } = useFieldArray({ control, name: 'lineas' })
  const pedidoId = useWatch({ control, name: 'pedidoId' })
  const lineas = useWatch({ control, name: 'lineas' })

  const pedido = useQuery({
    queryKey: ['pedido', pedidoId],
    enabled: !!pedidoId,
    queryFn: async () => (await http.get(`${PATHS.pedidos}/${pedidoId}`)).data,
  })

  useEffect(() => {
    const detalles: any[] = pedido.data?.detalles
    if (!detalles) return
    reset({
      pedidoId,
      observaciones: '',
      lineas: detalles
        .map((d) => ({ d, pend: Number(d.cantidad) - Number(d.cantidadDespachada ?? 0) }))
        .filter((x) => x.pend > 0)
        .map(({ d, pend }) => ({
          detalleId: String(d.id),
          productoId: String(d.producto?.id ?? d.productoId),
          nombre: productoLabel(d.producto),
          pendiente: pend,
          cantidad: String(pend),
        })),
    })
  }, [pedido.data, pedidoId, reset])

  const problems = (lineas ?? []).map((l) => (Number(l.cantidad) > l.pendiente ? `No puede superar el pendiente (${num(l.pendiente)}).` : Number(l.cantidad) < 0 ? 'No puede ser negativa.' : ''))
  const valid = !!pedidoId && (lineas ?? []).some((l) => Number(l.cantidad) > 0) && problems.every((p) => !p)

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => http.post(PATHS.despachos, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [PATHS.despachos] })
      notify('Despacho guardado en borrador. Confírmalo para descontar el stock y facturar.')
      onClose()
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  const submit = (v: Values) =>
    save.mutate({
      pedidoVentaId: idValue(v.pedidoId),
      observaciones: v.observaciones.trim() || null,
      detalles: v.lineas.filter((l) => Number(l.cantidad) > 0).map((l) => ({
        pedidoDetalleId: idValue(l.detalleId), productoId: idValue(l.productoId), cantidad: Number(l.cantidad),
      })),
    })

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <DialogTitle>Nuevo despacho</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1, display: 'grid', gap: 2 }}>
            <Controller name="pedidoId" control={control} render={({ field }) => (
              <Autocomplete
                size="small" options={pedidos} noOptionsText="No hay pedidos confirmados por despachar"
                getOptionLabel={(o) => `${o.numero ?? o.id} — ${txt(o.cliente ?? o.clienteNombre)}`}
                isOptionEqualToValue={(a, b) => a.id === b.id}
                value={pedidos.find((o) => String(o.id) === field.value) ?? null}
                onChange={(_e, v) => field.onChange(v ? String(v.id) : '')}
                renderInput={(p) => <TextField {...p} label="Orden de venta" required />}
              />
            )} />
            {pedidoId && pedido.isLoading && <Typography>Cargando pedido…</Typography>}
            {pedido.error && <Typography color="error">{errorMessage(pedido.error)}</Typography>}
            {pedidoId && !pedido.isLoading && !pedido.error && fields.length === 0 && (
              <Typography color="text.secondary">Este pedido no tiene productos pendientes de despachar.</Typography>
            )}
            {fields.length > 0 && (
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Producto</TableCell>
                      <TableCell align="right">Pendiente</TableCell>
                      <TableCell>A despachar</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {fields.map((f, i) => (
                      <TableRow key={f.id}>
                        <TableCell>
                          {f.nombre}
                          {problems[i] && <Typography variant="caption" color="error" sx={{ display: 'block' }}>{problems[i]}</Typography>}
                        </TableCell>
                        <TableCell align="right">{num(f.pendiente)}</TableCell>
                        <TableCell>
                          <TextField size="small" type="number" sx={{ width: 110 }} slotProps={{ htmlInput: { min: 0, step: 'any', 'aria-label': 'Cantidad a despachar' } }}
                            {...register(`lineas.${i}.cantidad`)} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}
            {fields.length > 0 && <TextField label="Observaciones" size="small" multiline minRows={2} {...register('observaciones')} />}
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={!valid || save.isPending}>{save.isPending ? 'Guardando…' : 'Guardar borrador'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
