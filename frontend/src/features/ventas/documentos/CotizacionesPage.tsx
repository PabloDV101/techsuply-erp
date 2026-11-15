import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Box, Button, IconButton, InputAdornment, MenuItem, Paper, TextField, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import EditIcon from '@mui/icons-material/Edit'
import SendIcon from '@mui/icons-material/SendOutlined'
import CheckIcon from '@mui/icons-material/CheckCircleOutlined'
import CancelIcon from '@mui/icons-material/CancelOutlined'
import SwapIcon from '@mui/icons-material/ShoppingCartCheckout'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM, SEARCH_PARAM } from '../../../config/contract'
import { normalizePage, type Row } from '../../../shared/api'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import DataTable from '../../../shared/DataTable'
import { ESTADOS_COTIZACION, EstadoChip, estadoLabel } from '../../../shared/EstadoChip'
import { fecha, money, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'
import DocDetalleDialog from './DocDetalleDialog'
import DocFormDialog from './DocFormDialog'

type Kind = 'enviar' | 'aceptar' | 'rechazar' | 'convertir'
const TEXT: Record<Kind, { title: string; text: string; label: string }> = {
  enviar: { title: 'Enviar cotización', text: 'Se marcará como enviada al cliente y ya no podrá editarse.', label: 'Enviar' },
  aceptar: { title: 'Registrar aceptación', text: 'El cliente aceptó la cotización. Podrás convertirla en orden de venta.', label: 'Marcar aceptada' },
  rechazar: { title: 'Registrar rechazo', text: 'El cliente rechazó la cotización. Esta acción no se puede deshacer.', label: 'Marcar rechazada' },
  convertir: { title: 'Convertir en orden de venta', text: 'Se creará un pedido en borrador con los mismos productos y precios.', label: 'Crear orden de venta' },
}

export default function CotizacionesPage() {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const notify = useNotify()
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [search, setSearch] = useState('')
  const [estado, setEstado] = useState('')
  const [form, setForm] = useState<{ id?: Row['id'] } | null>(null)
  const [viewing, setViewing] = useState<Row['id'] | null>(null)
  const [action, setAction] = useState<{ kind: Kind; row: Row } | null>(null)

  const q = useQuery({
    queryKey: [PATHS.cotizaciones, page, size, search, estado],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (search) params[SEARCH_PARAM] = search
      if (estado) params.estado = estado
      return normalizePage((await http.get(PATHS.cotizaciones, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  const run = useMutation({
    mutationFn: async (a: { kind: Kind; row: Row }) => (await http.post(`${PATHS.cotizaciones}/${a.row.id}/${a.kind}`)).data,
    onSuccess: (data, a) => {
      for (const k of [[PATHS.cotizaciones], ['cotizacion', a.row.id], [PATHS.pedidos]]) qc.invalidateQueries({ queryKey: k })
      setAction(null)
      if (a.kind === 'convertir') {
        notify(`Orden de venta ${data?.numero ?? ''} creada en borrador.`.replace('  ', ' '))
        navigate('/ventas/pedidos')
      } else notify('Cotización actualizada.')
    },
    onError: (e) => { notify(errorMessage(e), 'error'); setAction(null) },
  })

  const canWrite = can(PERM.cotizacionWrite)
  const btn = (title: string, icon: React.ReactNode, onClick: () => void, color?: 'success' | 'error' | 'primary') => (
    <Tooltip title={title}><IconButton size="small" color={color} aria-label={title} onClick={onClick}>{icon}</IconButton></Tooltip>
  )

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Cotizaciones</Typography>
        {canWrite && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setForm({})}>Nueva cotización</Button>}
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField size="small" placeholder="Buscar por número o cliente" value={search} sx={{ flex: '1 1 260px' }}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <TextField select size="small" label="Estado" value={estado} sx={{ width: 200 }} onChange={(e) => { setEstado(e.target.value); setPage(0) }}>
          <MenuItem value="">Todos</MenuItem>
          {ESTADOS_COTIZACION.map((e) => <MenuItem key={e} value={e}>{estadoLabel(e)}</MenuItem>)}
        </TextField>
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={search || estado ? 'Ningún resultado con esos filtros.' : 'Aún no hay cotizaciones. Crea la primera para un cliente.'}
        columns={[
          { header: 'Número', render: (r) => txt(r.numero) },
          { header: 'Cliente', render: (r) => txt(r.cliente ?? r.clienteNombre) },
          { header: 'Fecha', render: (r) => fecha(r.fecha) },
          { header: 'Válida hasta', render: (r) => fecha(r.validaHasta) },
          { header: 'Estado', render: (r) => <EstadoChip estado={r.estado} /> },
          { header: 'Total', align: 'right', render: (r) => money(r.total) },
        ]}
        actions={(r) => (
          <>
            {btn('Ver detalle', <VisibilityIcon fontSize="small" />, () => setViewing(r.id))}
            {canWrite && r.estado === 'BORRADOR' && btn('Editar', <EditIcon fontSize="small" />, () => setForm({ id: r.id }))}
            {canWrite && r.estado === 'BORRADOR' && btn('Enviar', <SendIcon fontSize="small" />, () => setAction({ kind: 'enviar', row: r }), 'primary')}
            {canWrite && r.estado === 'ENVIADA' && btn('Marcar aceptada', <CheckIcon fontSize="small" />, () => setAction({ kind: 'aceptar', row: r }), 'success')}
            {canWrite && r.estado === 'ENVIADA' && btn('Marcar rechazada', <CancelIcon fontSize="small" />, () => setAction({ kind: 'rechazar', row: r }), 'error')}
            {canWrite && r.estado === 'ACEPTADA' && btn('Convertir en orden de venta', <SwapIcon fontSize="small" />, () => setAction({ kind: 'convertir', row: r }), 'primary')}
          </>
        )}
      />
      {form && <DocFormDialog kind="cotizacion" id={form.id} onClose={() => setForm(null)} />}
      {viewing !== null && <DocDetalleDialog kind="cotizacion" id={viewing} onClose={() => setViewing(null)} />}
      <ConfirmDialog open={!!action} title={action ? TEXT[action.kind].title : ''} text={action ? TEXT[action.kind].text : ''}
        confirmLabel={action ? TEXT[action.kind].label : ''} onConfirm={() => action && run.mutate(action)} onClose={() => setAction(null)} />
    </Box>
  )
}
