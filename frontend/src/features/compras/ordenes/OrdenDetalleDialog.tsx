import { useQuery } from '@tanstack/react-query'
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { PERM } from '../../../config/contract'
import { useLookup } from '../../../shared/api'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fechaHora, money, num } from '../../../shared/format'
import { useAuth } from '../../auth/AuthContext'

export default function OrdenDetalleDialog({ id, onClose }: { id: number | string; onClose: () => void }) {
  const { can } = useAuth()
  const { data: order, isLoading, error } = useQuery({
    queryKey: ['orden', id],
    queryFn: async () => (await http.get(`${PATHS.ordenes}/${id}`)).data,
  })
  const suppliers = useLookup(PATHS.proveedores, {}, can(PERM.proveedoresLeer), null).data ?? []
  const departments = useLookup(PATHS.departamentos, {}, can(PERM.departamentosLeer), null).data ?? []
  const products = useLookup(PATHS.productos, {}, can(PERM.productosLeer), null).data ?? []
  const supplier = suppliers.find((item) => String(item.id) === String(order?.proveedorId))
  const department = departments.find((item) => String(item.id) === String(order?.departamentoId))
  const productById = new Map(products.map((item) => [String(item.id), item]))
  const lines = order?.detalles ?? []

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="lg">
      <DialogTitle>Orden de compra {order?.folio ?? ''}</DialogTitle>
      <DialogContent>
        {isLoading && <Typography>Cargando…</Typography>}
        {error && <Typography color="error">{errorMessage(error)}</Typography>}
        {order && <>
          <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(4, 1fr)' }, gap: 2, mb: 2 }}>
            <Info label="Proveedor" value={supplier ? `${supplier.codigo} — ${supplier.nombreRazonSocial}` : `#${order.proveedorId}`} />
            <Info label="Departamento" value={department ? `${department.codigo} — ${department.nombre}` : `#${order.departamentoId}`} />
            <Info label="Creada" value={fechaHora(order.fecha)} />
            <Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={order.estado} /></Box></Box>
            <Info label="Creada por (usuario ID)" value={String(order.creadaPorUsuarioId)} />
            {order.aprobadaPorUsuarioId != null && <Info label="Aprobada por (usuario ID)" value={String(order.aprobadaPorUsuarioId)} />}
            {order.aprobadaEn && <Info label="Aprobada en" value={fechaHora(order.aprobadaEn)} />}
          </Box>
          {order.observaciones && <Info label="Observaciones" value={order.observaciones} />}
          {order.motivoRechazo && <Info label="Motivo de rechazo" value={order.motivoRechazo} />}
          <Box sx={{ overflowX: 'auto', mt: 2 }}>
            <Table size="small">
              <TableHead><TableRow>
                {['Producto', 'Pedida', 'Aceptada', 'Pendiente', 'Precio', 'Descuento', 'Impuesto'].map((heading) =>
                  <TableCell key={heading} align={heading === 'Producto' ? 'left' : 'right'}>{heading}</TableCell>)}
              </TableRow></TableHead>
              <TableBody>
                {lines.map((line: Record<string, unknown> & { id: number | string; productoId: number | string }, index: number) => {
                  const product = productById.get(String(line.productoId))
                  return <TableRow key={line.id ?? index}>
                    <TableCell>{product ? `${product.sku} — ${product.nombre}` : `Producto #${line.productoId}`}</TableCell>
                    <TableCell align="right">{num(line.cantidad)}</TableCell>
                    <TableCell align="right">{num(line.cantidadAceptada)}</TableCell>
                    <TableCell align="right">{num(line.cantidadPendiente)}</TableCell>
                    <TableCell align="right">{money(line.precioUnitario)}</TableCell>
                    <TableCell align="right">{num(line.descuentoPct)}%</TableCell>
                    <TableCell align="right">{num(line.tasaImpuesto)}%</TableCell>
                  </TableRow>
                })}
                <TableRow>
                  <TableCell colSpan={4} align="right" sx={{ fontWeight: 600 }}>Subtotal</TableCell><TableCell colSpan={3} align="right">{money(order.subtotal)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell colSpan={4} align="right" sx={{ fontWeight: 600 }}>Impuestos</TableCell><TableCell colSpan={3} align="right">{money(order.impuestos)}</TableCell>
                </TableRow>
                <TableRow>
                  <TableCell colSpan={4} align="right" sx={{ fontWeight: 600 }}>Total</TableCell><TableCell colSpan={3} align="right" sx={{ fontWeight: 600 }}>{money(order.total)}</TableCell>
                </TableRow>
              </TableBody>
            </Table>
          </Box>
        </>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}><Button onClick={onClose}>Cerrar</Button></DialogActions>
    </Dialog>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value}</Typography></Box>
}
