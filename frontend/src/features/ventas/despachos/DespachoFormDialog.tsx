import { useEffect, useMemo, useState } from 'react'
import { useFieldArray, useForm, useWatch } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Autocomplete, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { normalizePage, idValue, type Row, useLookup } from '../../../shared/api'
import { num, productoLabel } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'

interface Line { detallePedidoId: string; productoId: string; nombre: string; pendiente: number; cantidad: string }
interface Values { pedidoId: string; lineas: Line[] }

export default function DespachoFormDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const [orderPage, setOrderPage] = useState(0)
  const [idempotency, setIdempotency] = useState<{ fingerprint: string; key: string } | null>(null)
  const orders = useQuery({
    queryKey: [PATHS.pedidos, 'dispatch-select', orderPage],
    queryFn: async () => normalizePage((await http.get(PATHS.pedidos, { params: { page: orderPage, size: 100 } })).data),
  })
  const candidates = (orders.data?.rows ?? []).filter((row) => ['CONFIRMADO', 'PARCIALMENTE_DESPACHADO'].includes(String(row.estado)))
  const products = useLookup(PATHS.productos)
  const productMap = useMemo(() => new Map((products.data ?? []).map((product) => [String(product.id), product])), [products.data])
  const { control, register, handleSubmit, reset } = useForm<Values>({ defaultValues: { pedidoId: '', lineas: [] } })
  const { fields } = useFieldArray({ control, name: 'lineas' })
  const pedidoId = useWatch({ control, name: 'pedidoId' })
  const lineas = useWatch({ control, name: 'lineas' })
  const pedido = useQuery({ queryKey: [PATHS.pedidos, 'detail', pedidoId], enabled: !!pedidoId, queryFn: async () => (await http.get(`${PATHS.pedidos}/${pedidoId}`)).data })
  const dispatches = useQuery({ queryKey: [PATHS.despachos, 'order', pedidoId], enabled: !!pedidoId, queryFn: async () => {
    const first = normalizePage((await http.get(PATHS.despachos, { params: { page: 0, size: 100, pedidoId } })).data)
    const pages = await Promise.all(Array.from({ length: Math.max(0, Math.ceil(first.total / 100) - 1) }, async (_, index) => normalizePage((await http.get(PATHS.despachos, { params: { page: index + 1, size: 100, pedidoId } })).data).rows))
    return [...first.rows, ...pages.flat()]
  } })

  useEffect(() => {
    const details: Row[] | undefined = pedido.data?.detalles
    if (!details) return
    if (dispatches.isLoading) return
    const assigned = new Map<string, number>()
    for (const dispatch of dispatches.data ?? []) if (dispatch.estado === 'BORRADOR') for (const line of dispatch.detalles ?? []) assigned.set(String(line.detallePedidoId), (assigned.get(String(line.detallePedidoId)) ?? 0) + Number(line.cantidad))
    reset({ pedidoId, lineas: details
      .map((detail) => ({ detail, pending: Number(detail.cantidadReservada) - Number(detail.cantidadDespachada ?? 0) - (assigned.get(String(detail.id)) ?? 0) }))
      .filter(({ pending }) => pending > 0)
      .map(({ detail, pending }) => ({ detallePedidoId: String(detail.id), productoId: String(detail.productoId), nombre: productoLabel(productMap.get(String(detail.productoId))), pendiente: pending, cantidad: String(pending) })) })
  }, [pedido.data, pedidoId, dispatches.data, dispatches.isLoading, productMap, reset])

  const problems = (lineas ?? []).map((line) => {
    const value = Number(line.cantidad)
    if (!Number.isFinite(value) || value <= 0) return 'Captura una cantidad mayor que cero.'
    if (value > line.pendiente) return `No puede superar el pendiente (${num(line.pendiente)}).`
    return ''
  })
  const valid = !!pedidoId && !dispatches.isError && fields.length > 0 && problems.every((problem) => !problem)
  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => http.post(PATHS.despachos, body),
    onSuccess: (response) => {
      void qc.invalidateQueries({ queryKey: [PATHS.despachos] })
      void qc.invalidateQueries({ queryKey: [PATHS.pedidos] })
      notify(`Despacho ${response.data?.folio ?? ''} guardado en borrador. Confírmalo para aplicar stock, kardex y contabilidad; la factura se emite por separado.`)
      onClose()
    },
    onError: (error) => notify(errorMessage(error), 'error'),
  })
  const submit = (values: Values) => {
    const payload = { pedidoId: idValue(values.pedidoId), detalles: values.lineas.map((line) => ({ detallePedidoId: idValue(line.detallePedidoId), cantidad: Number(line.cantidad) })) }
    const fingerprint = JSON.stringify(payload)
    const key = idempotency?.fingerprint === fingerprint ? idempotency.key : crypto.randomUUID()
    setIdempotency({ fingerprint, key })
    save.mutate({ ...payload, claveIdempotencia: key })
  }

  return <Dialog open onClose={onClose} fullWidth maxWidth="md">
    <form onSubmit={handleSubmit(submit)} noValidate>
      <DialogTitle>Nuevo despacho</DialogTitle>
      <DialogContent>
        <Box sx={{ pt: 1, display: 'grid', gap: 2 }}>
          <Autocomplete options={candidates} loading={orders.isLoading} noOptionsText="No hay pedidos confirmados en esta página" getOptionLabel={(row) => `${row.folio ?? row.id} — almacén ${row.almacenId}`} isOptionEqualToValue={(a, b) => String(a.id) === String(b.id)} value={candidates.find((row) => String(row.id) === pedidoId) ?? null} onChange={(_event, value) => { reset({ pedidoId: value ? String(value.id) : '', lineas: [] }); setIdempotency(null) }} renderInput={(params) => <TextField {...params} label="Pedido confirmado o parcialmente despachado" required />} />
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <Typography variant="caption">Página {orderPage + 1} · {orders.data?.total ?? 0} pedidos</Typography>
            <Box><Button disabled={orderPage === 0} onClick={() => setOrderPage((page) => Math.max(0, page - 1))}>Anterior</Button><Button disabled={!orders.data || (orderPage + 1) * 100 >= orders.data.total} onClick={() => setOrderPage((page) => page + 1)}>Siguiente</Button></Box>
          </Box>
          {orders.error && <Alert severity="error">{errorMessage(orders.error)}</Alert>}
          {dispatches.error && <Alert severity="error">No se pudieron consultar los borradores que ya asignan cantidades: {errorMessage(dispatches.error)}</Alert>}
          {(pedido.isLoading || dispatches.isLoading) && <Typography>Cargando pedido y asignaciones…</Typography>}
          {pedido.error && <Alert severity="error">{errorMessage(pedido.error)}</Alert>}
          {pedidoId && !pedido.isLoading && !pedido.error && fields.length === 0 && <Typography color="text.secondary">Este pedido no tiene cantidades pendientes.</Typography>}
          {fields.length > 0 && <Box sx={{ overflowX: 'auto' }}><Table size="small"><TableHead><TableRow><TableCell>Producto</TableCell><TableCell align="right">Pendiente</TableCell><TableCell>A despachar</TableCell></TableRow></TableHead><TableBody>{fields.map((field, index) => <TableRow key={field.id}><TableCell>{field.nombre}{problems[index] && <Typography variant="caption" color="error" sx={{ display: 'block' }}>{problems[index]}</Typography>}</TableCell><TableCell align="right">{num(field.pendiente)}</TableCell><TableCell><TextField size="small" type="number" sx={{ width: 120 }} slotProps={{ htmlInput: { min: 0, step: 'any', 'aria-label': `Cantidad a despachar ${field.nombre}` } }} {...register(`lineas.${index}.cantidad`)} /></TableCell></TableRow>)}</TableBody></Table></Box>}
          <Alert severity="info">El borrador no cambia existencias. Al confirmarlo se registran salida, kardex y contabilidad. La factura requiere emisión separada.</Alert>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}><Button onClick={onClose}>Cerrar</Button><Button type="submit" variant="contained" disabled={!valid || save.isPending}>{save.isPending ? 'Guardando…' : 'Guardar borrador'}</Button></DialogActions>
    </form>
  </Dialog>
}
