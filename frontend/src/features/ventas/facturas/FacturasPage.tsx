import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Box, Button, IconButton, Paper, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import { http } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, type Row, useLookup } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, money } from '../../../shared/format'
import { useAuth } from '../../auth/AuthContext'
import { ClienteSelect } from '../../../shared/selects'
import FacturaDetalleDialog from './FacturaDetalleDialog'
import FacturaEmitirDialog from './FacturaEmitirDialog'

export default function FacturasPage() {
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [clienteId, setClienteId] = useState('')
  const [viewing, setViewing] = useState<Row['id'] | null>(null)
  const [emitting, setEmitting] = useState(false)
  const clients = useLookup(PATHS.clientes)
  const clientMap = new Map((clients.data ?? []).map((client) => [String(client.id), client]))
  const query = useQuery({ queryKey: [PATHS.facturas, page, size, clienteId], queryFn: async () => { const params: Record<string, unknown> = { page, size }; if (clienteId) params.clienteId = clienteId; return normalizePage((await http.get(PATHS.facturas, { params })).data) }, placeholderData: keepPreviousData })
  return <Box>
    <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', mb: 0.5, flexWrap: 'wrap' }}><Typography variant="h5" sx={{ flexGrow: 1 }}>Facturas</Typography>{can(PERM.facturaEmitir) && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEmitting(true)}>Emitir factura</Button>}</Box>
    <Typography color="text.secondary" sx={{ mb: 2 }}>Documentos comerciales internos de pedidos despachados. No son CFDI ni registran cobros.</Typography>
    <Paper elevation={0} sx={{ p: 2, mb: 2 }}><ClienteSelect value={clienteId} onChange={(value) => { setClienteId(value); setPage(0) }} width={320} /></Paper>
    <DataTable data={query.data} loading={query.isLoading} error={query.error} page={page} size={size} onPage={setPage} onSize={(value) => { setSize(value); setPage(0) }} emptyText={clienteId ? 'El cliente no tiene facturas.' : 'Aún no hay facturas emitidas.'} columns={[
      { header: 'Folio', render: (row) => String(row.folio ?? '—') },
      { header: 'Cliente', render: (row) => clientMap.get(String(row.clienteId))?.nombreRazonSocial ?? `Cliente ${row.clienteId}` },
      { header: 'Pedido ID', render: (row) => String(row.pedidoId) },
      { header: 'Emisión', render: (row) => fecha(row.fechaEmision) },
      { header: 'Estado', render: (row) => <EstadoChip estado={row.estado} /> },
      { header: 'Total', align: 'right', render: (row) => money(row.total) },
    ]} actions={(row) => <Tooltip title="Ver factura"><IconButton size="small" aria-label={`Ver ${row.folio}`} onClick={() => setViewing(row.id)}><VisibilityIcon fontSize="small" /></IconButton></Tooltip>} />
    {viewing !== null && <FacturaDetalleDialog id={viewing} onClose={() => setViewing(null)} />}{emitting && <FacturaEmitirDialog onClose={() => setEmitting(false)} onEmitted={(id) => { setEmitting(false); setViewing(id) }} />}
  </Box>
}
