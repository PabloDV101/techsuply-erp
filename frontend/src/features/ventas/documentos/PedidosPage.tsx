import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Box, Button, IconButton, InputAdornment, MenuItem, Paper, TextField, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import EditIcon from '@mui/icons-material/Edit'
import CheckIcon from '@mui/icons-material/CheckCircleOutlined'
import CancelIcon from '@mui/icons-material/CancelOutlined'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM, SEARCH_PARAM } from '../../../config/contract'
import { normalizePage, type Row } from '../../../shared/api'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import DataTable from '../../../shared/DataTable'
import { ESTADOS_PEDIDO, EstadoChip, estadoLabel } from '../../../shared/EstadoChip'
import { fecha, money, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'
import DocDetalleDialog from './DocDetalleDialog'
import DocFormDialog from './DocFormDialog'

export default function PedidosPage() {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [search, setSearch] = useState('')
  const [estado, setEstado] = useState('')
  const [form, setForm] = useState<{ id?: Row['id'] } | null>(null)
  const [viewing, setViewing] = useState<Row['id'] | null>(null)
  const [action, setAction] = useState<{ kind: 'confirmar' | 'cancelar'; row: Row } | null>(null)

  const q = useQuery({
    queryKey: [PATHS.pedidos, page, size, search, estado],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (search) params[SEARCH_PARAM] = search
      if (estado) params.estado = estado
      return normalizePage((await http.get(PATHS.pedidos, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  const run = useMutation({
    mutationFn: async (a: { kind: 'confirmar' | 'cancelar'; row: Row }) => (await http.post(`${PATHS.pedidos}/${a.row.id}/${a.kind}`)).data,
    onSuccess: (data, a) => {
      for (const k of [[PATHS.pedidos], ['pedido', a.row.id], [PATHS.credito], [PATHS.existencias]]) qc.invalidateQueries({ queryKey: k })
      setAction(null)
      if (a.kind === 'cancelar') notify('Pedido cancelado.')
      else if (data?.estado === 'RETENIDO_CREDITO') notify('El pedido excede el crédito del cliente y quedó retenido para aprobación.', 'info')
      else notify('Pedido confirmado. El stock quedó reservado.')
    },
    onError: (e) => { notify(errorMessage(e), 'error'); setAction(null) },
  })

  const canWrite = can(PERM.pedidoWrite)
  const canConfirm = can(PERM.pedidoConfirm)
  const btn = (title: string, icon: React.ReactNode, onClick: () => void, color?: 'success' | 'error') => (
    <Tooltip title={title}><IconButton size="small" color={color} aria-label={title} onClick={onClick}>{icon}</IconButton></Tooltip>
  )

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Órdenes de venta</Typography>
        {canWrite && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setForm({})}>Nueva orden de venta</Button>}
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField size="small" placeholder="Buscar por número o cliente" value={search} sx={{ flex: '1 1 260px' }}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <TextField select size="small" label="Estado" value={estado} sx={{ width: 220 }} onChange={(e) => { setEstado(e.target.value); setPage(0) }}>
          <MenuItem value="">Todos</MenuItem>
          {ESTADOS_PEDIDO.map((e) => <MenuItem key={e} value={e}>{estadoLabel(e)}</MenuItem>)}
        </TextField>
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={search || estado ? 'Ningún resultado con esos filtros.' : 'Aún no hay órdenes de venta. Crea una o conviértela desde una cotización.'}
        columns={[
          { header: 'Número', render: (r) => txt(r.numero) },
          { header: 'Cliente', render: (r) => txt(r.cliente ?? r.clienteNombre) },
          { header: 'Fecha', render: (r) => fecha(r.fecha) },
          { header: 'Estado', render: (r) => <EstadoChip estado={r.estado} /> },
          { header: 'Total', align: 'right', render: (r) => money(r.total) },
        ]}
        actions={(r) => (
          <>
            {btn('Ver detalle', <VisibilityIcon fontSize="small" />, () => setViewing(r.id))}
            {canWrite && r.estado === 'BORRADOR' && btn('Editar', <EditIcon fontSize="small" />, () => setForm({ id: r.id }))}
            {canConfirm && r.estado === 'BORRADOR' && btn('Confirmar y reservar stock', <CheckIcon fontSize="small" />, () => setAction({ kind: 'confirmar', row: r }), 'success')}
            {canWrite && ['BORRADOR', 'CONFIRMADO', 'RETENIDO_CREDITO'].includes(String(r.estado)) &&
              btn('Cancelar pedido', <CancelIcon fontSize="small" />, () => setAction({ kind: 'cancelar', row: r }), 'error')}
          </>
        )}
      />
      {form && <DocFormDialog kind="pedido" id={form.id} onClose={() => setForm(null)} />}
      {viewing !== null && <DocDetalleDialog kind="pedido" id={viewing} onClose={() => setViewing(null)} />}
      <ConfirmDialog
        open={!!action}
        title={action?.kind === 'confirmar' ? `Confirmar pedido ${txt(action.row.numero)}` : `Cancelar pedido ${txt(action?.row.numero)}`}
        text={action?.kind === 'confirmar'
          ? 'Se validará el crédito del cliente y se reservará el stock. Ya no podrá editarse.'
          : 'Se liberará el stock reservado. Esta acción no se puede deshacer.'}
        confirmLabel={action?.kind === 'confirmar' ? 'Confirmar pedido' : 'Cancelar pedido'}
        onConfirm={() => action && run.mutate(action)} onClose={() => setAction(null)} />
    </Box>
  )
}
