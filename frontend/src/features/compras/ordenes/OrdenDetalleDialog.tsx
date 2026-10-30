import { useQuery } from '@tanstack/react-query'
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, money, num, txt } from '../../../shared/format'

export default function OrdenDetalleDialog({ id, onClose }: { id: number | string; onClose: () => void }) {
  const { data: o, isLoading, error } = useQuery({
    queryKey: ['orden', id],
    queryFn: async () => (await http.get(`${PATHS.ordenes}/${id}`)).data,
  })
  const lines: any[] = o?.detalles ?? []

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Orden de compra {o?.numero ?? ''}</DialogTitle>
      <DialogContent>
        {isLoading && <Typography>Cargando…</Typography>}
        {error && <Typography color="error">{errorMessage(error)}</Typography>}
        {o && (
          <>
            <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(3, 1fr)' }, gap: 2, mb: 2 }}>
              <Info label="Proveedor" value={txt(o.proveedor ?? o.proveedorNombre)} />
              <Info label="Fecha" value={fecha(o.fecha)} />
              <Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={o.estado} /></Box></Box>
            </Box>
            {o.observaciones && <Info label="Observaciones" value={o.observaciones} />}
            <Box sx={{ overflowX: 'auto', mt: 2 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Producto</TableCell>
                    <TableCell align="right">Pedido</TableCell>
                    <TableCell align="right">Recibido</TableCell>
                    <TableCell align="right">Pendiente</TableCell>
                    <TableCell align="right">Precio</TableCell>
                    <TableCell align="right">Subtotal</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {lines.map((d, i) => {
                    const rec = Number(d.cantidadRecibida ?? 0)
                    return (
                      <TableRow key={d.id ?? i}>
                        <TableCell>{d.producto ? `${txt(d.producto.codigo)} — ${txt(d.producto.descripcion)}` : txt(d.productoNombre)}</TableCell>
                        <TableCell align="right">{num(d.cantidad)}</TableCell>
                        <TableCell align="right">{num(rec)}</TableCell>
                        <TableCell align="right">{num(Number(d.cantidad) - rec)}</TableCell>
                        <TableCell align="right">{money(d.precioUnitario)}</TableCell>
                        <TableCell align="right">{money(Number(d.cantidad) * Number(d.precioUnitario))}</TableCell>
                      </TableRow>
                    )
                  })}
                  <TableRow>
                    <TableCell colSpan={5} align="right" sx={{ fontWeight: 600 }}>Total</TableCell>
                    <TableCell align="right" sx={{ fontWeight: 600 }}>{money(o.total)}</TableCell>
                  </TableRow>
                </TableBody>
              </Table>
            </Box>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}><Button onClick={onClose}>Cerrar</Button></DialogActions>
    </Dialog>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value}</Typography></Box>
}
