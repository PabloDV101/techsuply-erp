import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Box, Button, IconButton, Paper, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import SendIcon from '@mui/icons-material/SendOutlined'
import CheckIcon from '@mui/icons-material/CheckCircleOutlined'
import CancelIcon from '@mui/icons-material/CancelOutlined'
import SwapIcon from '@mui/icons-material/ShoppingCartCheckout'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, type Row, useLookup } from '../../../shared/api'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import DataTable from '../../../shared/DataTable'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, money, today } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { ClienteSelect } from '../../../shared/selects'
import { useAuth } from '../../auth/AuthContext'
import DocDetalleDialog from './DocDetalleDialog'
import DocFormDialog from './DocFormDialog'
import PedidoConversionDialog from './PedidoConversionDialog'

type Transition = 'issue' | 'accept' | 'cancel'
const TRANSITIONS: Record<Transition, { title: string; text: string; label: string }> = {
  issue: { title: 'Emitir cotización', text: 'Cambiará de borrador a vigente y ya no podrá editarse.', label: 'Emitir' },
  accept: { title: 'Registrar aceptación', text: 'Se registrará internamente que el cliente aceptó la cotización.', label: 'Marcar aceptada' },
  cancel: { title: 'Cancelar cotización', text: 'La cotización se conservará como documento cancelado.', label: 'Cancelar cotización' },
}

export default function CotizacionesPage() {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [clienteId, setClienteId] = useState('')
  const [creating, setCreating] = useState(false)
  const [viewing, setViewing] = useState<Row['id'] | null>(null)
  const [transition, setTransition] = useState<{ kind: Transition; row: Row } | null>(null)
  const [converting, setConverting] = useState<Row | null>(null)
  const clients = useLookup(PATHS.clientes)
  const clientMap = new Map((clients.data ?? []).map((client) => [String(client.id), client]))

  const quotes = useQuery({
    queryKey: [PATHS.cotizaciones, page, size, clienteId],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (clienteId) params.clienteId = clienteId
      return normalizePage((await http.get(PATHS.cotizaciones, { params })).data)
    },
    placeholderData: keepPreviousData,
  })
  const runTransition = useMutation({
    mutationFn: ({ row, kind }: { row: Row; kind: Transition }) => http.post(`${PATHS.cotizaciones}/${row.id}/${kind}`),
    onSuccess: (_response, action) => {
      void qc.invalidateQueries({ queryKey: [PATHS.cotizaciones] })
      void qc.invalidateQueries({ queryKey: ['cotizacion', action.row.id] })
      setTransition(null)
      notify(action.kind === 'issue' ? 'Cotización emitida y vigente.' : action.kind === 'accept' ? 'Aceptación registrada.' : 'Cotización cancelada.')
    },
    onError: (error) => { notify(errorMessage(error), 'error'); setTransition(null) },
  })
  const canAdmin = can(PERM.cotizacionAdmin)

  const actionButton = (label: string, icon: React.ReactNode, onClick: () => void, color?: 'success' | 'error' | 'primary') => (
    <Tooltip title={label}><IconButton size="small" color={color} aria-label={label} onClick={onClick}>{icon}</IconButton></Tooltip>
  )

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Cotizaciones</Typography>
        {can(PERM.cotizacionWrite) && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Nueva cotización</Button>}
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2 }}>
        <ClienteSelect value={clienteId} onChange={(value) => { setClienteId(value); setPage(0) }} width={320} />
      </Paper>
      <DataTable data={quotes.data} loading={quotes.isLoading} error={quotes.error} page={page} size={size}
        onPage={setPage} onSize={(value) => { setSize(value); setPage(0) }}
        emptyText={clienteId ? 'El cliente no tiene cotizaciones.' : 'Aún no hay cotizaciones.'}
        columns={[
          { header: 'Folio', render: (row) => String(row.folio ?? '—') },
          { header: 'Cliente', render: (row) => clientMap.get(String(row.clienteId))?.nombreRazonSocial ?? `Cliente ${row.clienteId}` },
          { header: 'Fecha', render: (row) => fecha(row.fecha) },
          { header: 'Vigente hasta', render: (row) => fecha(row.vigenciaHasta) },
          { header: 'Estado', render: (row) => <EstadoChip estado={row.estado} /> },
          { header: 'Total', align: 'right', render: (row) => money(row.total) },
        ]}
        actions={(row) => <>
          <Tooltip title="Ver detalle"><IconButton size="small" aria-label={`Ver ${row.folio}`} onClick={() => setViewing(row.id)}><VisibilityIcon fontSize="small" /></IconButton></Tooltip>
          {canAdmin && row.estado === 'BORRADOR' && actionButton(`Emitir ${row.folio}`, <SendIcon fontSize="small" />, () => setTransition({ kind: 'issue', row }), 'primary')}
          {canAdmin && row.estado === 'VIGENTE' && actionButton(`Marcar aceptada ${row.folio}`, <CheckIcon fontSize="small" />, () => setTransition({ kind: 'accept', row }), 'success')}
          {canAdmin && ['BORRADOR', 'VIGENTE'].includes(String(row.estado)) && actionButton(`Cancelar ${row.folio}`, <CancelIcon fontSize="small" />, () => setTransition({ kind: 'cancel', row }), 'error')}
          {can(PERM.pedidoWrite) && row.estado === 'ACEPTADA' && String(row.vigenciaHasta) >= today() && actionButton(`Convertir ${row.folio} en pedido`, <SwapIcon fontSize="small" />, () => setConverting(row), 'primary')}
        </>}
      />
      {creating && <DocFormDialog onClose={() => setCreating(false)} />}
      {viewing !== null && <DocDetalleDialog kind="cotizacion" id={viewing} onClose={() => setViewing(null)} />}
      {converting && <PedidoConversionDialog quote={{ id: converting.id, folio: String(converting.folio) }} onClose={() => setConverting(null)} />}
      <ConfirmDialog open={!!transition} title={transition ? TRANSITIONS[transition.kind].title : ''}
        text={transition ? `${TRANSITIONS[transition.kind].text} ${transition.row.folio}` : ''}
        confirmLabel={transition ? TRANSITIONS[transition.kind].label : ''}
        onConfirm={() => transition && runTransition.mutate(transition)} onClose={() => setTransition(null)} />
    </Box>
  )
}
