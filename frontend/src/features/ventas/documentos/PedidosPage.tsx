import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, Paper, TextField, Tooltip, Typography } from '@mui/material'
import CheckIcon from '@mui/icons-material/CheckCircleOutlined'
import CancelIcon from '@mui/icons-material/CancelOutlined'
import LocalShippingIcon from '@mui/icons-material/LocalShippingOutlined'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, type Row, useLookup } from '../../../shared/api'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import DataTable from '../../../shared/DataTable'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, money } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { ClienteSelect } from '../../../shared/selects'
import { useAuth } from '../../auth/AuthContext'
import DocDetalleDialog from './DocDetalleDialog'

export default function PedidosPage() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const notify = useNotify()
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [clienteId, setClienteId] = useState('')
  const [viewing, setViewing] = useState<Row['id'] | null>(null)
  const [confirming, setConfirming] = useState<Row | null>(null)
  const [cancelling, setCancelling] = useState<Row | null>(null)
  const [cancelReason, setCancelReason] = useState('')
  const clients = useLookup(PATHS.clientes)
  const clientMap = new Map((clients.data ?? []).map((client) => [String(client.id), client]))

  const orders = useQuery({
    queryKey: [PATHS.pedidos, page, size, clienteId],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (clienteId) params.clienteId = clienteId
      return normalizePage((await http.get(PATHS.pedidos, { params })).data)
    },
    placeholderData: keepPreviousData,
  })
  const invalidate = () => {
    void qc.invalidateQueries({ queryKey: [PATHS.pedidos] })
    void qc.invalidateQueries({ queryKey: [PATHS.existencias] })
    void qc.invalidateQueries({ queryKey: [PATHS.despachos] })
  }
  const confirmOrder = useMutation({
    mutationFn: (row: Row) => http.post(`${PATHS.pedidos}/${row.id}/confirm`),
    onSuccess: () => { invalidate(); notify('Pedido confirmado; las existencias quedaron reservadas.'); setConfirming(null) },
    onError: (error) => { notify(errorMessage(error), 'error'); setConfirming(null) },
  })
  const cancelOrder = useMutation({
    mutationFn: ({ row, reason }: { row: Row; reason: string }) => http.post(`${PATHS.pedidos}/${row.id}/cancel`, { motivo: reason }),
    onSuccess: () => { invalidate(); notify('Pedido cancelado; se liberó la reserva pendiente.'); setCancelling(null); setCancelReason('') },
    onError: (error) => { notify(errorMessage(error), 'error'); setCancelling(null) },
  })

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Pedidos de venta</Typography>
        <Typography color="text.secondary">Se crean al convertir una cotización aceptada y vigente.</Typography>
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2 }}><ClienteSelect value={clienteId} onChange={(value) => { setClienteId(value); setPage(0) }} width={320} /></Paper>
      <DataTable data={orders.data} loading={orders.isLoading} error={orders.error} page={page} size={size}
        onPage={setPage} onSize={(value) => { setSize(value); setPage(0) }}
        emptyText={clienteId ? 'El cliente no tiene pedidos.' : 'Aún no hay pedidos. Convierte una cotización aceptada y vigente.'}
        columns={[
          { header: 'Folio', render: (row) => String(row.folio ?? '—') },
          { header: 'Cliente', render: (row) => clientMap.get(String(row.clienteId))?.nombreRazonSocial ?? `Cliente ${row.clienteId}` },
          { header: 'Almacén ID', render: (row) => String(row.almacenId) },
          { header: 'Fecha', render: (row) => fecha(row.fecha) },
          { header: 'Modalidad', render: (row) => `${row.modalidad} (no acredita pago)` },
          { header: 'Estado', render: (row) => <EstadoChip estado={row.estado} /> },
          { header: 'Total', align: 'right', render: (row) => money(row.total) },
        ]}
        actions={(row) => <>
          <Tooltip title="Ver detalle"><IconButton size="small" aria-label={`Ver ${row.folio}`} onClick={() => setViewing(row.id)}><VisibilityIcon fontSize="small" /></IconButton></Tooltip>
          {can(PERM.pedidoConfirm) && row.estado === 'BORRADOR' && <Tooltip title="Confirmar y reservar existencias"><IconButton size="small" aria-label={`Confirmar ${row.folio}`} onClick={() => setConfirming(row)} color="success"><CheckIcon fontSize="small" /></IconButton></Tooltip>}
          {can(PERM.pedidoCancel) && ['BORRADOR', 'CONFIRMADO', 'PARCIALMENTE_DESPACHADO'].includes(String(row.estado)) && <Tooltip title="Cancelar pedido"><IconButton size="small" aria-label={`Cancelar ${row.folio}`} onClick={() => { setCancelling(row); setCancelReason('') }} color="error"><CancelIcon fontSize="small" /></IconButton></Tooltip>}
          {can(PERM.despachoLeer) && ['CONFIRMADO', 'PARCIALMENTE_DESPACHADO'].includes(String(row.estado)) && <Tooltip title="Ver o registrar despachos"><IconButton size="small" aria-label={`Despachos de ${row.folio}`} onClick={() => navigate(`/ventas/despachos?pedidoId=${row.id}`)}><LocalShippingIcon fontSize="small" /></IconButton></Tooltip>}
        </>}
      />
      {viewing !== null && <DocDetalleDialog kind="pedido" id={viewing} onClose={() => setViewing(null)} />}
      <ConfirmDialog open={!!confirming} title={`Confirmar pedido ${confirming?.folio ?? ''}`}
        text="Se validará la cotización y se reservará el stock de todas las líneas. Esto no registra pago y no crea un movimiento de kardex."
        confirmLabel="Confirmar y reservar" onConfirm={() => confirming && confirmOrder.mutate(confirming)} onClose={() => setConfirming(null)} />
      <Dialog open={!!cancelling} onClose={() => setCancelling(null)} fullWidth maxWidth="xs">
        <DialogTitle>Cancelar pedido {cancelling?.folio ?? ''}</DialogTitle>
        <DialogContent><TextField autoFocus fullWidth required multiline minRows={3} sx={{ mt: 1 }} label="Motivo" value={cancelReason} onChange={(event) => setCancelReason(event.target.value)} slotProps={{ htmlInput: { maxLength: 1000 } }} /></DialogContent>
        <DialogActions><Button onClick={() => setCancelling(null)}>Volver</Button><Button color="error" variant="contained" disabled={!cancelReason.trim() || cancelOrder.isPending} onClick={() => cancelling && cancelOrder.mutate({ row: cancelling, reason: cancelReason.trim() })}>Cancelar pedido</Button></DialogActions>
      </Dialog>
    </Box>
  )
}
