import { useQuery } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { useLookup } from '../../../shared/api'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, money, num, productoLabel } from '../../../shared/format'

function Info({ label, value }: { label: string; value: string }) {
  return <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value}</Typography></Box>
}

export default function FacturaDetalleDialog({ id, onClose }: { id: number | string; onClose: () => void }) {
  const { data: invoice, isLoading, error } = useQuery({ queryKey: [PATHS.facturas, 'detail', id], queryFn: async () => (await http.get(`${PATHS.facturas}/${id}`)).data })
  const order = useQuery({ queryKey: [PATHS.pedidos, 'detail', invoice?.pedidoId], enabled: !!invoice?.pedidoId, queryFn: async () => (await http.get(`${PATHS.pedidos}/${invoice.pedidoId}`)).data })
  const clients = useLookup(PATHS.clientes)
  const products = useLookup(PATHS.productos)
  const client = (clients.data ?? []).find((item) => String(item.id) === String(invoice?.clienteId))
  const productMap = new Map((products.data ?? []).map((product) => [String(product.id), product]))
  const orderLines = new Map((order.data?.detalles ?? []).map((line: any) => [String(line.id), line]))
  const lines: any[] = invoice?.detalles ?? []

  return <Dialog open onClose={onClose} fullWidth maxWidth="md"><DialogTitle>Factura {invoice?.folio ?? ''}</DialogTitle><DialogContent>
    {isLoading && <Typography>Cargando…</Typography>}{error && <Typography color="error">{errorMessage(error)}</Typography>}
    {invoice && <>
      <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(4, 1fr)' }, gap: 2, mb: 2 }}><Info label="Cliente" value={client?.nombreRazonSocial ?? `Cliente ${invoice.clienteId}`} /><Info label="Pedido ID" value={String(invoice.pedidoId)} /><Info label="Emisión" value={fecha(invoice.fechaEmision)} /><Info label="Vencimiento" value={fecha(invoice.fechaVencimiento)} /><Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={invoice.estado} /></Box></Box></Box>
      <Box sx={{ overflowX: 'auto', mt: 2 }}><Table size="small"><TableHead><TableRow><TableCell>Producto</TableCell><TableCell align="right">Cantidad</TableCell><TableCell align="right">Precio</TableCell><TableCell align="right">Descuento</TableCell><TableCell align="right">Impuesto</TableCell><TableCell align="right">Total línea</TableCell></TableRow></TableHead><TableBody>
        {lines.map((line, index) => { const orderLine: any = orderLines.get(String(line.detallePedidoId)); return <TableRow key={line.id ?? index}><TableCell>{productoLabel(productMap.get(String(line.productoId ?? orderLine?.productoId)))}</TableCell><TableCell align="right">{num(line.cantidad)}</TableCell><TableCell align="right">{money(line.precioUnitario)}</TableCell><TableCell align="right">{num(line.descuentoPct)}%</TableCell><TableCell align="right">{num(line.tasaImpuesto)}%</TableCell><TableCell align="right">{money(line.total)}</TableCell></TableRow> })}
        {([['Subtotal', invoice.subtotal], ['Impuestos', invoice.impuestos], ['Total', invoice.total]] as const).map(([label, value]) => <TableRow key={label}><TableCell colSpan={5} align="right" sx={{ fontWeight: label === 'Total' ? 700 : 500 }}>{label}</TableCell><TableCell align="right" sx={{ fontWeight: label === 'Total' ? 700 : 500 }}>{money(value)}</TableCell></TableRow>)}
      </TableBody></Table></Box>
      <Typography variant="body2" color="text.secondary" sx={{ mt: 2 }}>Documento comercial interno. No es CFDI y no registra pago, cuenta por cobrar ni saldo.</Typography>
      {order.error && <Typography color="error" variant="caption">No se pudieron cargar nombres de productos: {errorMessage(order.error)}</Typography>}
    </>}
  </DialogContent><DialogActions sx={{ px: 3, pb: 2 }}><Button onClick={onClose}>Cerrar</Button></DialogActions></Dialog>
}
