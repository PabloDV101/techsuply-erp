import { useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, num, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'

export default function RecepcionDetalleDialog({ id, onClose }: { id: number | string; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [confirming, setConfirming] = useState(false)
  // La misma clave se reutiliza si el usuario reintenta: el backend no duplica la entrada de stock.
  const idempotencyKey = useRef(crypto.randomUUID())

  const { data: r, isLoading, error } = useQuery({
    queryKey: ['recepcion', id],
    queryFn: async () => (await http.get(`${PATHS.recepciones}/${id}`)).data,
  })

  const confirm = useMutation({
    mutationFn: () => http.post(`${PATHS.recepciones}/${id}/confirmar`, null, { headers: { 'Idempotency-Key': idempotencyKey.current } }),
    onSuccess: () => {
      for (const k of [[PATHS.recepciones], ['recepcion', id], [PATHS.ordenes], ['orden']]) qc.invalidateQueries({ queryKey: k })
      notify('Recepción confirmada. El stock se actualizó con lo aceptado.')
      setConfirming(false)
    },
    onError: (e) => { notify(errorMessage(e), 'error'); setConfirming(false) },
  })

  const lines: any[] = r?.detalles ?? []

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Recepción {r?.numero ?? ''}</DialogTitle>
      <DialogContent>
        {isLoading && <Typography>Cargando…</Typography>}
        {error && <Typography color="error">{errorMessage(error)}</Typography>}
        {r && (
          <>
            <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(4, 1fr)' }, gap: 2, mb: 2 }}>
              <Box><Typography variant="caption" color="text.secondary">Orden de compra</Typography><Typography>{txt(r.ordenCompra?.numero ?? r.ordenCompraNumero)}</Typography></Box>
              <Box><Typography variant="caption" color="text.secondary">Fecha</Typography><Typography>{fecha(r.fecha)}</Typography></Box>
              <Box><Typography variant="caption" color="text.secondary">Registrada por</Typography><Typography>{txt(r.usuario)}</Typography></Box>
              <Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={r.estado} /></Box></Box>
            </Box>
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Producto</TableCell>
                    <TableCell align="right">Recibido</TableCell>
                    <TableCell align="right">Aceptado</TableCell>
                    <TableCell align="right">Rechazado</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {lines.map((d, i) => (
                    <TableRow key={d.id ?? i}>
                      <TableCell>{d.producto ? `${txt(d.producto.codigo)} — ${txt(d.producto.descripcion)}` : txt(d.productoNombre)}</TableCell>
                      <TableCell align="right">{num(d.cantidadRecibida)}</TableCell>
                      <TableCell align="right">{num(d.cantidadAceptada)}</TableCell>
                      <TableCell align="right">{num(d.cantidadRechazada)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cerrar</Button>
        {r?.estado === 'BORRADOR' && can(PERM.recepcionConfirm) && (
          <Button variant="contained" onClick={() => setConfirming(true)} disabled={confirm.isPending}>Confirmar recepción</Button>
        )}
      </DialogActions>
      <ConfirmDialog
        open={confirming}
        title="Confirmar recepción"
        text="Se sumará al inventario solo la cantidad aceptada y se actualizará la orden de compra. No se puede deshacer."
        confirmLabel="Confirmar recepción"
        onConfirm={() => confirm.mutate()}
        onClose={() => setConfirming(false)}
      />
    </Dialog>
  )
}
