import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, num, productoLabel, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'

export default function DespachoDetalleDialog({ id, onClose }: { id: number | string; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [confirming, setConfirming] = useState(false)
  // La misma clave se reutiliza al reintentar: el backend no descuenta el stock dos veces.
  const idempotencyKey = useRef(crypto.randomUUID())

  const { data: d, isLoading, error } = useQuery({
    queryKey: ['despacho', id],
    queryFn: async () => (await http.get(`${PATHS.despachos}/${id}`)).data,
  })

  const confirm = useMutation({
    mutationFn: async () =>
      (await http.post(`${PATHS.despachos}/${id}/confirmar`, null, { headers: { 'Idempotency-Key': idempotencyKey.current } })).data,
    onSuccess: (data) => {
      for (const k of [[PATHS.despachos], ['despacho', id], [PATHS.pedidos], [PATHS.facturas], [PATHS.existencias], [PATHS.movimientos], [PATHS.credito]]) {
        qc.invalidateQueries({ queryKey: k })
      }
      const factura = data?.factura?.numero ?? data?.facturaNumero
      notify(factura ? `Despacho confirmado. Se generó la factura ${factura}.` : 'Despacho confirmado. La factura se generará automáticamente.')
      setConfirming(false)
    },
    onError: (e) => { notify(errorMessage(e), 'error'); setConfirming(false) },
  })

  const lines: any[] = d?.detalles ?? []
  const Info = ({ label, value }: { label: string; value: string }) => (
    <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value}</Typography></Box>
  )

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Despacho {d?.numero ?? ''}</DialogTitle>
      <DialogContent>
        {isLoading && <Typography>Cargando…</Typography>}
        {error && <Typography color="error">{errorMessage(error)}</Typography>}
        {d && (
          <>
            <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(4, 1fr)' }, gap: 2, mb: 2 }}>
              <Info label="Orden de venta" value={txt(d.pedido?.numero ?? d.pedidoNumero)} />
              <Info label="Cliente" value={txt(d.cliente ?? d.pedido?.cliente ?? d.clienteNombre)} />
              <Info label="Fecha" value={fecha(d.fecha)} />
              <Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={d.estado} /></Box></Box>
            </Box>
            {(d.factura?.numero ?? d.facturaNumero) && <Info label="Factura generada" value={String(d.factura?.numero ?? d.facturaNumero)} />}
            <Box sx={{ overflowX: 'auto', mt: 2 }}>
              <Table size="small">
                <TableHead><TableRow><TableCell>Producto</TableCell><TableCell align="right">Cantidad</TableCell></TableRow></TableHead>
                <TableBody>
                  {lines.map((l, i) => (
                    <TableRow key={l.id ?? i}><TableCell>{productoLabel(l.producto)}</TableCell><TableCell align="right">{num(l.cantidad)}</TableCell></TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cerrar</Button>
        {d?.estado === 'BORRADOR' && can(PERM.despachoConfirm) && (
          <Button variant="contained" disabled={confirm.isPending} onClick={() => setConfirming(true)}>Confirmar despacho</Button>
        )}
      </DialogActions>
      <ConfirmDialog open={confirming} title="Confirmar despacho"
        text="Se descontará el stock reservado, se actualizará el pedido y se emitirá la factura automáticamente. No se puede deshacer."
        confirmLabel="Confirmar despacho" onConfirm={() => confirm.mutate()} onClose={() => setConfirming(false)} />
    </Dialog>
  )
}
