import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { Box, Button, IconButton, Paper, TextField, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import { http } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, type Row } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha } from '../../../shared/format'
import { useAuth } from '../../auth/AuthContext'
import DespachoDetalleDialog from './DespachoDetalleDialog'
import DespachoFormDialog from './DespachoFormDialog'

export default function DespachosPage() {
  const { can } = useAuth()
  const [searchParams, setSearchParams] = useSearchParams()
  const pedidoId = searchParams.get('pedidoId') ?? ''
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [creating, setCreating] = useState(false)
  const [viewing, setViewing] = useState<Row['id'] | null>(null)
  const q = useQuery({
    queryKey: [PATHS.despachos, page, size, pedidoId],
    queryFn: async () => { const params: Record<string, unknown> = { page, size }; if (pedidoId) params.pedidoId = pedidoId; return normalizePage((await http.get(PATHS.despachos, { params })).data) },
    placeholderData: keepPreviousData,
  })
  return <Box>
    <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}><Typography variant="h5" sx={{ flexGrow: 1 }}>Despachos</Typography>{can(PERM.despachoWrite) && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Nuevo despacho</Button>}</Box>
    {pedidoId && <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, alignItems: 'center' }}><TextField size="small" label="Filtrado por pedido ID" value={pedidoId} slotProps={{ input: { readOnly: true } }} /><Button onClick={() => { setSearchParams({}); setPage(0) }}>Quitar filtro</Button></Paper>}
    <DataTable data={q.data} loading={q.isLoading} error={q.error} page={page} size={size} onPage={setPage} onSize={(value) => { setSize(value); setPage(0) }} emptyText={pedidoId ? 'Este pedido aún no tiene despachos.' : 'Aún no hay despachos.'} columns={[
      { header: 'Folio', render: (row) => String(row.folio ?? '—') },
      { header: 'Pedido ID', render: (row) => String(row.pedidoId) },
      { header: 'Almacén ID', render: (row) => String(row.almacenId) },
      { header: 'Fecha', render: (row) => fecha(row.fecha) },
      { header: 'Registrado por', render: (row) => String(row.registradoPorUsuarioId) },
      { header: 'Estado', render: (row) => <EstadoChip estado={row.estado} /> },
    ]} actions={(row) => <Tooltip title="Ver detalle"><IconButton size="small" aria-label={`Ver ${row.folio}`} onClick={() => setViewing(row.id)}><VisibilityIcon fontSize="small" /></IconButton></Tooltip>} />
    {creating && <DespachoFormDialog onClose={() => setCreating(false)} />}{viewing !== null && <DespachoDetalleDialog id={viewing} onClose={() => setViewing(null)} />}
  </Box>
}
