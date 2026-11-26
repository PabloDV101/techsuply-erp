import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, Typography,
} from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { useLookup } from '../../../shared/api'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fechaHora, money, num } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'

export default function RecepcionDetalleDialog({ id, onClose }: { id: number | string; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [confirming, setConfirming] = useState(false)
  const receiptQuery = useQuery({
    queryKey: ['recepcion', id],
    queryFn: async () => (await http.get(`${PATHS.recepciones}/${id}`)).data,
  })
  const receipt = receiptQuery.data
  const orderQuery = useQuery({
    queryKey: ['orden', receipt?.ordenCompraId],
    enabled: receipt?.ordenCompraId != null && can(PERM.ordenesLeer),
    queryFn: async () => (await http.get(`${PATHS.ordenes}/${receipt?.ordenCompraId}`)).data,
  })
  const products = useLookup(PATHS.productos, {}, can(PERM.productosLeer)).data ?? []
  const warehouses = useLookup(PATHS.almacenes, {}, can(PERM.almacenesLeer)).data ?? []
  const productById = new Map(products.map((product) => [String(product.id), product]))
  const warehouse = warehouses.find((item) => String(item.id) === String(receipt?.almacenId))
  const orderLineById = new Map<string, { productoId: number | string }>(
    (orderQuery.data?.detalles ?? []).map((line: { id: number | string; productoId: number | string }) => [String(line.id), line]),
  )

  const confirm = useMutation({
    mutationFn: () => http.post(`${PATHS.recepciones}/${id}/confirm`),
    onSuccess: () => {
      for (const key of [[PATHS.recepciones], ['recepcion', id], [PATHS.ordenes], ['orden']]) {
        qc.invalidateQueries({ queryKey: key })
      }
      notify('Recepción confirmada. Se actualizó el stock con las cantidades aceptadas.')
      setConfirming(false)
    },
    onError: (error) => { notify(errorMessage(error), 'error'); setConfirming(false) },
  })

  const lines = receipt?.detalles ?? []

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Recepción {receipt?.folio ?? ''}</DialogTitle>
      <DialogContent>
        {receiptQuery.isLoading && <Typography>Cargando…</Typography>}
        {receiptQuery.error && <Typography color="error">{errorMessage(receiptQuery.error)}</Typography>}
        {receipt && <>
          <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(4, 1fr)' }, gap: 2, mb: 2 }}>
            <Info label="Orden de compra (ID)" value={String(receipt.ordenCompraId)} />
            <Info label="Almacén" value={warehouse ? `${warehouse.codigo} — ${warehouse.nombre}` : `#${receipt.almacenId}`} />
            <Info label="Registrada" value={fechaHora(receipt.fecha)} />
            <Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={receipt.estado} /></Box></Box>
            <Info label="Registrada por (usuario ID)" value={String(receipt.registradaPorUsuarioId)} />
            {receipt.referenciaExterna && <Info label="Referencia externa" value={receipt.referenciaExterna} />}
            {receipt.confirmadaEn && <Info label="Confirmada" value={fechaHora(receipt.confirmadaEn)} />}
          </Box>
          {orderQuery.error && <Alert severity="warning" sx={{ mb: 2 }}>No se pudo cargar el detalle de la orden relacionada. Se muestran los identificadores de línea.</Alert>}
          <Table size="small">
            <TableHead><TableRow>
              {['Producto', 'Recibido', 'Aceptado', 'Rechazado', 'Precio recibido', 'Motivo'].map((heading) => <TableCell key={heading}>{heading}</TableCell>)}
            </TableRow></TableHead>
            <TableBody>{lines.map((line: Record<string, unknown> & { id: number | string; detalleOrdenCompraId: number | string }) => {
              const orderLine = orderLineById.get(String(line.detalleOrdenCompraId))
              const product = orderLine ? productById.get(String(orderLine.productoId)) : undefined
              return <TableRow key={line.id}>
                <TableCell>{product ? `${product.sku} — ${product.nombre}` : `Detalle de orden #${line.detalleOrdenCompraId}`}</TableCell>
                <TableCell>{num(line.cantidadRecibida)}</TableCell>
                <TableCell>{num(line.cantidadAceptada)}</TableCell>
                <TableCell>{num(line.cantidadRechazada)}</TableCell>
                <TableCell>{money(line.precioUnitarioRecibido)}</TableCell>
                <TableCell>{String(line.motivoRechazo ?? '—')}</TableCell>
              </TableRow>
            })}</TableBody>
          </Table>
          {orderQuery.isLoading && <Typography variant="caption">Cargando referencias de productos…</Typography>}
        </>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cerrar</Button>
        {receipt?.estado === 'PENDIENTE' && can(PERM.recepcionConfirm) &&
          <Button variant="contained" onClick={() => setConfirming(true)} disabled={confirm.isPending}>Confirmar recepción</Button>}
      </DialogActions>
      <ConfirmDialog open={confirming} title="Confirmar recepción"
        text="La confirmación actualizará inventario, kardex y el estado de la orden en una sola operación. Solo las cantidades aceptadas entran al stock."
        confirmLabel={confirm.isPending ? 'Confirmando…' : 'Confirmar recepción'} onConfirm={() => confirm.mutate()} onClose={() => setConfirming(false)} />
    </Dialog>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value}</Typography></Box>
}
