import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Autocomplete, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { normalizePage } from '../../../shared/api'
import { money } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'

export default function FacturaEmitirDialog({ onClose, onEmitted }: { onClose: () => void; onEmitted: (id: number | string) => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const [page, setPage] = useState(0)
  const [pedidoId, setPedidoId] = useState('')
  const idempotency = useRef<{ pedidoId: string; key: string } | null>(null)
  const orders = useQuery({ queryKey: [PATHS.pedidos, 'invoice-select', page], queryFn: async () => normalizePage((await http.get(PATHS.pedidos, { params: { page, size: 100 } })).data) })
  const candidates = (orders.data?.rows ?? []).filter((order) => order.estado === 'DESPACHADO')
  const selected = candidates.find((order) => String(order.id) === pedidoId) ?? null
  const existingInvoices = useQuery({
    queryKey: [PATHS.facturas, 'order-check', selected?.clienteId],
    enabled: !!selected,
    queryFn: async () => {
      const params = { page: 0, size: 100, clienteId: selected?.clienteId }
      const firstResponse = (await http.get(PATHS.facturas, { params })).data
      const first = normalizePage(firstResponse)
      const totalPages = typeof firstResponse.totalPages === 'number' ? firstResponse.totalPages : 1
      const pages = await Promise.all(Array.from({ length: Math.max(0, totalPages - 1) }, async (_, index) => normalizePage((await http.get(PATHS.facturas, { params: { ...params, page: index + 1 } })).data).rows))
      return [...first.rows, ...pages.flat()]
    },
  })
  const alreadyInvoiced = existingInvoices.data?.find((invoice) => String(invoice.pedidoId) === pedidoId)
  const emit = useMutation({
    mutationFn: (body: Record<string, unknown>) => http.post(PATHS.facturas, body),
    onSuccess: (response) => {
      void qc.invalidateQueries({ queryKey: [PATHS.facturas] })
      notify(`Factura ${response.data?.folio ?? ''} emitida. Es un documento comercial interno; no genera CFDI ni registra cobro.`)
      onEmitted(response.data.id)
    },
    onError: (error) => notify(errorMessage(error), 'error'),
  })
  const submit = () => {
    if (!pedidoId) return
    if (!idempotency.current || idempotency.current.pedidoId !== pedidoId) idempotency.current = { pedidoId, key: crypto.randomUUID() }
    emit.mutate({ pedidoId: /^\d+$/.test(pedidoId) ? Number(pedidoId) : pedidoId, claveIdempotencia: idempotency.current.key })
  }
  return <Dialog open onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>Emitir factura comercial interna</DialogTitle><DialogContent><Box sx={{ display: 'grid', gap: 2, pt: 1 }}>
    <Alert severity="info">Solo se puede emitir cuando el pedido está completamente despachado. La factura no genera CFDI, cuenta por cobrar ni cobro.</Alert>
    <Autocomplete options={candidates} loading={orders.isLoading} value={selected} onChange={(_event, value) => { setPedidoId(value ? String(value.id) : ''); idempotency.current = null }} getOptionLabel={(order) => `${order.folio ?? order.id} — cliente ${order.clienteId} — ${money(order.total)}`} isOptionEqualToValue={(a, b) => String(a.id) === String(b.id)} noOptionsText="No hay pedidos despachados en esta página" renderInput={(params) => <TextField {...params} label="Pedido completamente despachado" required />} />
    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}><Typography variant="caption">Página {page + 1} · {orders.data?.total ?? 0} pedidos</Typography><Box><Button disabled={page === 0} onClick={() => setPage((value) => Math.max(0, value - 1))}>Anterior</Button><Button disabled={!orders.data || (page + 1) * 100 >= orders.data.total} onClick={() => setPage((value) => value + 1)}>Siguiente</Button></Box></Box>
    {orders.error && <Alert severity="error">{errorMessage(orders.error)}</Alert>}
    {selected && existingInvoices.isLoading && <Typography variant="caption">Comprobando si el pedido ya tiene factura…</Typography>}
    {existingInvoices.error && <Alert severity="error">No se pudo comprobar si el pedido ya está facturado: {errorMessage(existingInvoices.error)}</Alert>}
    {alreadyInvoiced && <Alert severity="warning">El pedido ya tiene la factura {alreadyInvoiced.folio}; no se puede emitir otra.</Alert>}
  </Box></DialogContent><DialogActions sx={{ px: 3, pb: 2 }}><Button onClick={onClose}>Cerrar</Button><Button variant="contained" disabled={!selected || !!alreadyInvoiced || existingInvoices.isLoading || !!existingInvoices.error || emit.isPending} onClick={submit}>{emit.isPending ? 'Emitiendo…' : 'Emitir factura'}</Button></DialogActions></Dialog>
}
