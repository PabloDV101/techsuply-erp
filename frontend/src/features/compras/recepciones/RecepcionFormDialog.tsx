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
import { num, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'

interface Line {
  detalleId: string
  productoId: string
  nombre: string
  pendiente: number
  recibida: string
  aceptada: string
  rechazada: string
}
interface Values { ordenId: string; observaciones: string; lineas: Line[] }

const n = (v: string) => Number(v) || 0

export default function RecepcionFormDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  // Solo las órdenes que aún pueden recibir mercadería.
  const aprobadas = useLookup(PATHS.ordenes, { activo: undefined, estado: 'APROBADA' }).data ?? []
  const parciales = useLookup(PATHS.ordenes, { activo: undefined, estado: 'PARCIAL' }).data ?? []
  const ordenes = [...aprobadas, ...parciales]

  const { control, register, handleSubmit, reset } = useForm<Values>({ defaultValues: { ordenId: '', observaciones: '', lineas: [] } })
  const { fields } = useFieldArray({ control, name: 'lineas' })
  const ordenId = useWatch({ control, name: 'ordenId' })
  const lineas = useWatch({ control, name: 'lineas' })

  const orden = useQuery({
    queryKey: ['orden', idValue(ordenId || '0')],
    enabled: !!ordenId,
    queryFn: async () => (await http.get(`${PATHS.ordenes}/${ordenId}`)).data,
  })

  // Al elegir la orden, se arma una línea por cada producto con saldo pendiente.
  useEffect(() => {
    const detalles: any[] = orden.data?.detalles
    if (!detalles) return
    reset({
      ordenId,
      observaciones: '',
      lineas: detalles
        .map((d) => ({ d, pend: Number(d.cantidad) - Number(d.cantidadRecibida ?? 0) }))
        .filter((x) => x.pend > 0)
        .map(({ d, pend }) => ({
          detalleId: String(d.id),
          productoId: String(d.producto?.id ?? d.productoId),
          nombre: d.producto ? `${txt(d.producto.codigo)} — ${txt(d.producto.descripcion)}` : txt(d.productoNombre),
          pendiente: pend,
          recibida: String(pend),
          aceptada: String(pend),
          rechazada: '0',
        })),
    })
  }, [orden.data, ordenId, reset])

  const problems = (lineas ?? []).map((l) => {
    if (n(l.recibida) > l.pendiente) return `No puede superar el pendiente (${num(l.pendiente)}).`
    if (n(l.aceptada) + n(l.rechazada) !== n(l.recibida)) return 'Aceptado + rechazado debe ser igual a lo recibido.'
    return ''
  })
  const hasQty = (lineas ?? []).some((l) => n(l.recibida) > 0)
  const valid = !!ordenId && hasQty && problems.every((p) => !p)

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => http.post(PATHS.recepciones, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [PATHS.recepciones] })
      notify('Recepción guardada en borrador. Confírmala para actualizar el inventario.')
      onClose()
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  const submit = (v: Values) =>
    save.mutate({
      ordenCompraId: idValue(v.ordenId),
      observaciones: v.observaciones.trim() || null,
      detalles: v.lineas
        .filter((l) => n(l.recibida) > 0)
        .map((l) => ({
          ordenCompraDetalleId: idValue(l.detalleId),
          productoId: idValue(l.productoId),
          cantidadRecibida: n(l.recibida),
          cantidadAceptada: n(l.aceptada),
          cantidadRechazada: n(l.rechazada),
        })),
    })

  const qty = (i: number, key: 'recibida' | 'aceptada' | 'rechazada', label: string) => (
    <TextField size="small" type="number" aria-label={label} sx={{ width: 100 }} slotProps={{ htmlInput: { min: 0, step: 'any' } }} {...register(`lineas.${i}.${key}`)} />
  )

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <DialogTitle>Nueva recepción de bienes</DialogTitle>
        <DialogContent>
          <Box sx={{ pt: 1, display: 'grid', gap: 2 }}>
            <Controller
              name="ordenId" control={control}
              render={({ field }) => (
                <Autocomplete
                  size="small" options={ordenes} noOptionsText="No hay órdenes aprobadas o parciales"
                  getOptionLabel={(o) => `${o.numero ?? o.id} — ${txt(o.proveedor ?? o.proveedorNombre)}`}
                  isOptionEqualToValue={(a, b) => a.id === b.id}
                  value={ordenes.find((o) => String(o.id) === field.value) ?? null}
                  onChange={(_e, v) => field.onChange(v ? String(v.id) : '')}
                  renderInput={(p) => <TextField {...p} label="Orden de compra" required />}
                />
              )}
            />
            {ordenId && orden.isLoading && <Typography>Cargando orden…</Typography>}
            {orden.error && <Typography color="error">{errorMessage(orden.error)}</Typography>}
            {ordenId && !orden.isLoading && fields.length === 0 && !orden.error && (
              <Typography color="text.secondary">Esta orden no tiene productos pendientes de recibir.</Typography>
            )}
            {fields.length > 0 && (
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Producto</TableCell>
                      <TableCell align="right">Pendiente</TableCell>
                      <TableCell>Recibido</TableCell>
                      <TableCell>Aceptado</TableCell>
                      <TableCell>Rechazado</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {fields.map((f, i) => (
                      <TableRow key={f.id}>
                        <TableCell>
                          {f.nombre}
                          {problems[i] && <Typography sx={{ display: 'block' }} variant="caption" color="error" >{problems[i]}</Typography>}
                        </TableCell>
                        <TableCell align="right">{num(f.pendiente)}</TableCell>
                        <TableCell>{qty(i, 'recibida', 'Cantidad recibida')}</TableCell>
                        <TableCell>{qty(i, 'aceptada', 'Cantidad aceptada')}</TableCell>
                        <TableCell>{qty(i, 'rechazada', 'Cantidad rechazada')}</TableCell>
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
