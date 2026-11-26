import { useQuery } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { useLookup } from '../../../shared/api'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, money, num } from '../../../shared/format'
import { DOC, type DocKind } from './docConfig'

function Info({ label, value }: { label: string; value: string }) {
  return <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value}</Typography></Box>
}

export default function DocDetalleDialog({ kind, id, onClose }: { kind: DocKind; id: number | string; onClose: () => void }) {
  const config = DOC[kind]
  const { data, isLoading, error } = useQuery({
    queryKey: [kind, id],
    queryFn: async () => (await http.get(`${config.path}/${id}`)).data,
  })
  const products = useLookup(PATHS.productos)
  const clients = useLookup(PATHS.clientes)
  const productMap = new Map((products.data ?? []).map((product) => [String(product.id), product]))
  const client = (clients.data ?? []).find((item) => String(item.id) === String(data?.clienteId))
  const lines: any[] = data?.detalles ?? []
  const subtotal = (line: any) => Number(line.cantidad) * Number(line.precioUnitario) * (1 - Number(line.descuentoPct ?? 0) / 100)

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{kind === 'pedido' ? 'Pedido de venta' : 'Cotización'} {data?.folio ?? ''}</DialogTitle>
      <DialogContent>
        {isLoading && <Typography>Cargando…</Typography>}
        {error && <Typography color="error">{errorMessage(error)}</Typography>}
        {data && <>
          <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(4, 1fr)' }, gap: 2, mb: 2 }}>
            <Info label="Cliente" value={client?.nombreRazonSocial ?? `Cliente ${data.clienteId}`} />
            <Info label="Fecha" value={fecha(data.fecha)} />
            {kind === 'cotizacion' && <Info label={config.dateLabel} value={fecha(data[config.dateKey])} />}
            {kind === 'pedido' && <Info label="Almacén ID" value={String(data.almacenId)} />}
            {kind === 'pedido' && <Info label="Modalidad" value={`${data.modalidad} (no acredita pago)`} />}
            <Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={data.estado} /></Box></Box>
          </Box>
          <Box sx={{ overflowX: 'auto', mt: 2 }}><Table size="small">
            <TableHead><TableRow>
              <TableCell>Producto</TableCell><TableCell align="right">Cantidad</TableCell>
              {kind === 'pedido' && <><TableCell align="right">Reservado</TableCell><TableCell align="right">Despachado</TableCell></>}
              <TableCell align="right">Precio</TableCell><TableCell align="right">Desc. %</TableCell><TableCell align="right">Impuesto %</TableCell><TableCell align="right">Subtotal</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {lines.map((line) => <TableRow key={line.id}>
                <TableCell>{productMap.get(String(line.productoId)) ? `${productMap.get(String(line.productoId))?.sku} — ${productMap.get(String(line.productoId))?.nombre}` : `Producto ${line.productoId}`}</TableCell>
                <TableCell align="right">{num(line.cantidad)}</TableCell>
                {kind === 'pedido' && <><TableCell align="right">{num(line.cantidadReservada ?? 0)}</TableCell><TableCell align="right">{num(line.cantidadDespachada ?? 0)}</TableCell></>}
                <TableCell align="right">{money(line.precioUnitario)}</TableCell>
                <TableCell align="right">{num(line.descuentoPct ?? 0)}</TableCell>
                <TableCell align="right">{num(line.tasaImpuesto ?? 0)}</TableCell>
                <TableCell align="right">{money(subtotal(line))}</TableCell>
              </TableRow>)}
              {[['Subtotal', data.subtotal], ['Impuestos', data.impuestos], ['Total', data.total]].map(([label, value]) => (
                <TableRow key={String(label)}><TableCell colSpan={kind === 'pedido' ? 7 : 5} align="right" sx={{ fontWeight: label === 'Total' ? 700 : 500 }}>{label}</TableCell>
                  <TableCell align="right" sx={{ fontWeight: label === 'Total' ? 700 : 500 }}>{money(value)}</TableCell></TableRow>
              ))}
            </TableBody>
          </Table></Box>
        </>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}><Button onClick={onClose}>Cerrar</Button></DialogActions>
    </Dialog>
  )
}
