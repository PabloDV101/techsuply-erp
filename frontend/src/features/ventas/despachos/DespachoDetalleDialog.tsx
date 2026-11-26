import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { useLookup } from '../../../shared/api'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, num, productoLabel } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'

function Info({ label, value }: { label: string; value: string }) {
  return <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value}</Typography></Box>
}

export default function DespachoDetalleDialog({ id, onClose }: { id: number | string; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [confirming, setConfirming] = useState(false)
  const [cancelling, setCancelling] = useState(false)
  const { data: dispatch, isLoading, error } = useQuery({ queryKey: [PATHS.despachos, 'detail', id], queryFn: async () => (await http.get(`${PATHS.despachos}/${id}`)).data })
  const order = useQuery({ queryKey: [PATHS.pedidos, 'detail', dispatch?.pedidoId], enabled: !!dispatch?.pedidoId, queryFn: async () => (await http.get(`${PATHS.pedidos}/${dispatch.pedidoId}`)).data })
  const products = useLookup(PATHS.productos)
  const productMap = new Map((products.data ?? []).map((product) => [String(product.id), product]))
  const invalidate = () => {
    for (const key of [PATHS.despachos, PATHS.pedidos, PATHS.existencias, PATHS.movimientos, PATHS.asientosContables]) void qc.invalidateQueries({ queryKey: [key] })
    void qc.invalidateQueries({ queryKey: [PATHS.despachos, 'detail', id] })
  }
  const confirm = useMutation({ mutationFn: () => http.post(`${PATHS.despachos}/${id}/confirm`), onSuccess: () => { invalidate(); notify('Despacho confirmado: existencias, kardex y contabilidad actualizados. Emite la factura por separado.'); setConfirming(false) }, onError: (cause) => { notify(errorMessage(cause), 'error'); setConfirming(false) } })
  const cancel = useMutation({ mutationFn: () => http.post(`${PATHS.despachos}/${id}/cancel`), onSuccess: () => { invalidate(); notify('Despacho borrador cancelado; la reserva del pedido se conserva.'); setCancelling(false) }, onError: (cause) => { notify(errorMessage(cause), 'error'); setCancelling(false) } })
  const orderLines = new Map((order.data?.detalles ?? []).map((line: any) => [String(line.id), line]))

  return <Dialog open onClose={onClose} fullWidth maxWidth="md">
    <DialogTitle>Despacho {dispatch?.folio ?? ''}</DialogTitle>
    <DialogContent>
      {isLoading && <Typography>Cargando…</Typography>}{error && <Typography color="error">{errorMessage(error)}</Typography>}
      {dispatch && <>
        <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(4, 1fr)' }, gap: 2, mb: 2 }}><Info label="Pedido ID" value={String(dispatch.pedidoId)} /><Info label="Almacén ID" value={String(dispatch.almacenId)} /><Info label="Registrado por" value={String(dispatch.registradoPorUsuarioId)} /><Info label="Fecha" value={fecha(dispatch.fecha)} /><Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={dispatch.estado} /></Box></Box></Box>
        <Box sx={{ overflowX: 'auto', mt: 2 }}><Table size="small"><TableHead><TableRow><TableCell>Producto</TableCell><TableCell align="right">Cantidad</TableCell><TableCell align="right">Costo unitario al confirmar</TableCell></TableRow></TableHead><TableBody>{(dispatch.detalles ?? []).map((line: any) => { const orderLine: any = orderLines.get(String(line.detallePedidoId)); return <TableRow key={line.id}><TableCell>{productoLabel(productMap.get(String(orderLine?.productoId)))}</TableCell><TableCell align="right">{num(line.cantidad)}</TableCell><TableCell align="right">{line.costoUnitario == null ? 'Pendiente' : num(line.costoUnitario)}</TableCell></TableRow> })}</TableBody></Table></Box>
        {order.error && <Typography color="error" variant="caption">No se pudo resolver la descripción de productos: {errorMessage(order.error)}</Typography>}
        <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>La factura comercial interna se emite por separado después de completar los despachos del pedido. Este flujo no genera CFDI.</Typography>
      </>}
    </DialogContent>
    <DialogActions sx={{ px: 3, pb: 2 }}><Button onClick={onClose}>Cerrar</Button>{dispatch?.estado === 'BORRADOR' && can(PERM.despachoWrite) && <Button color="error" disabled={cancel.isPending} onClick={() => setCancelling(true)}>Cancelar borrador</Button>}{dispatch?.estado === 'BORRADOR' && can(PERM.despachoConfirm) && <Button variant="contained" disabled={confirm.isPending} onClick={() => setConfirming(true)}>Confirmar despacho</Button>}</DialogActions>
    <ConfirmDialog open={confirming} title="Confirmar despacho" text="Se aplicarán las salidas reservadas al stock, kardex y contabilidad. La factura se emite después desde Facturas." confirmLabel="Confirmar despacho" onConfirm={() => confirm.mutate()} onClose={() => setConfirming(false)} />
    <ConfirmDialog open={cancelling} title="Cancelar despacho borrador" text="El borrador se cancelará y liberará su asignación de cantidades. La reserva del pedido permanecerá activa." confirmLabel="Cancelar borrador" onConfirm={() => cancel.mutate()} onClose={() => setCancelling(false)} />
  </Dialog>
}
