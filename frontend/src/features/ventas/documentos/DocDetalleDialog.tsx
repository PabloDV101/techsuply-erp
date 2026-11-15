import { useQuery } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, money, num, productoLabel, txt } from '../../../shared/format'
import { DOC, type DocKind } from './docConfig'

export default function DocDetalleDialog({ kind, id, onClose }: { kind: DocKind; id: number | string; onClose: () => void }) {
  const cfg = DOC[kind]
  const { data: d, isLoading, error } = useQuery({
    queryKey: [kind, id],
    queryFn: async () => (await http.get(`${cfg.path}/${id}`)).data,
  })
  const lines: any[] = d?.detalles ?? []
  const sub = (l: any) => Number(l.cantidad) * Number(l.precioUnitario) * (1 - Number(l.descuento ?? 0) / 100)

  const Info = ({ label, value }: { label: string; value: string }) => (
    <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value}</Typography></Box>
  )

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>{kind === 'pedido' ? 'Orden de venta' : 'Cotización'} {d?.numero ?? ''}</DialogTitle>
      <DialogContent>
        {isLoading && <Typography>Cargando…</Typography>}
        {error && <Typography color="error">{errorMessage(error)}</Typography>}
        {d && (
          <>
            <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(4, 1fr)' }, gap: 2, mb: 2 }}>
              <Info label="Cliente" value={txt(d.cliente ?? d.clienteNombre)} />
              <Info label="Fecha" value={fecha(d.fecha)} />
              <Info label={cfg.venceLabel} value={fecha(d[cfg.venceKey])} />
              <Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={d.estado} /></Box></Box>
            </Box>
            {d.observaciones && <Info label="Observaciones" value={d.observaciones} />}
            <Box sx={{ overflowX: 'auto', mt: 2 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Producto</TableCell>
                    <TableCell align="right">Cantidad</TableCell>
                    {kind === 'pedido' && <TableCell align="right">Despachado</TableCell>}
                    <TableCell align="right">Precio</TableCell>
                    <TableCell align="right">Desc. %</TableCell>
                    <TableCell align="right">Subtotal</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {lines.map((l, i) => (
                    <TableRow key={l.id ?? i}>
                      <TableCell>{productoLabel(l.producto)}</TableCell>
                      <TableCell align="right">{num(l.cantidad)}</TableCell>
                      {kind === 'pedido' && <TableCell align="right">{num(l.cantidadDespachada ?? 0)}</TableCell>}
                      <TableCell align="right">{money(l.precioUnitario)}</TableCell>
                      <TableCell align="right">{num(l.descuento ?? 0)}</TableCell>
                      <TableCell align="right">{money(sub(l))}</TableCell>
                    </TableRow>
                  ))}
                  {[['Subtotal', d.subtotal], ['Impuestos', d.impuestos], ['Total', d.total]].filter(([, v]) => v != null).map(([label, v]) => (
                    <TableRow key={String(label)}>
                      <TableCell colSpan={kind === 'pedido' ? 5 : 4} align="right" sx={{ fontWeight: label === 'Total' ? 700 : 500 }}>{label}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: label === 'Total' ? 700 : 500 }}>{money(v)}</TableCell>
                    </TableRow>
                  ))}
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
