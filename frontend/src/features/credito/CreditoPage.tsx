import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Box, IconButton, InputAdornment, LinearProgress, MenuItem, Paper, Tab, Tabs, TextField, Tooltip, Typography } from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import ReceiptIcon from '@mui/icons-material/ReceiptLongOutlined'
import EditIcon from '@mui/icons-material/Edit'
import LockIcon from '@mui/icons-material/LockOutlined'
import LockOpenIcon from '@mui/icons-material/LockOpenOutlined'
import CheckIcon from '@mui/icons-material/CheckCircleOutlined'
import CancelIcon from '@mui/icons-material/CancelOutlined'
import { http, errorMessage } from '../../api/http'
import { PATHS, PERM, SEARCH_PARAM } from '../../config/contract'
import { normalizePage, type Row } from '../../shared/api'
import DataTable from '../../shared/DataTable'
import { ESTADOS_CREDITO, EstadoChip, estadoLabel } from '../../shared/EstadoChip'
import { fecha, money, txt } from '../../shared/format'
import { MotivoDialog } from '../../shared/MotivoDialog'
import { useNotify } from '../../shared/Notify'
import { useAuth } from '../auth/AuthContext'
import EstadoCuentaDialog from './EstadoCuentaDialog'
import LimiteDialog from './LimiteDialog'

type Pending =
  | { kind: 'bloquear' | 'desbloquear'; row: Row }
  | { kind: 'liberar' | 'rechazar'; row: Row }

export default function CreditoPage() {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const admin = can(PERM.creditoAdmin)
  const [tab, setTab] = useState(0)
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [search, setSearch] = useState('')
  const [estado, setEstado] = useState('')
  const [statement, setStatement] = useState<Row['id'] | null>(null)
  const [limit, setLimit] = useState<Row | null>(null)
  const [pending, setPending] = useState<Pending | null>(null)

  const clientes = useQuery({
    queryKey: [PATHS.credito, 'clientes', page, size, search, estado],
    enabled: tab === 0,
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (search) params[SEARCH_PARAM] = search
      if (estado) params.estado = estado
      const d = normalizePage((await http.get(`${PATHS.credito}/clientes`, { params })).data)
      // El id de la fila es siempre el del cliente.
      return { ...d, rows: d.rows.map((r) => ({ ...r, id: r.clienteId ?? r.cliente?.id ?? r.id, clienteNombre: txt(r.cliente ?? r.clienteNombre) })) }
    },
    placeholderData: keepPreviousData,
  })

  const retenidos = useQuery({
    queryKey: [PATHS.pedidos, 'retenidos', page, size],
    enabled: tab === 1,
    queryFn: async () => normalizePage((await http.get(PATHS.pedidos, { params: { page, size, estado: 'RETENIDO_CREDITO' } })).data),
    placeholderData: keepPreviousData,
  })

  const run = useMutation({
    mutationFn: (v: { p: Pending; motivo: string }) => {
      const { kind, row } = v.p
      const url = kind === 'bloquear' || kind === 'desbloquear' ? `${PATHS.credito}/clientes/${row.id}/${kind}`
        : kind === 'liberar' ? `${PATHS.pedidos}/${row.id}/liberar-credito` : `${PATHS.pedidos}/${row.id}/cancelar`
      return http.post(url, { motivo: v.motivo })
    },
    onSuccess: (_r, v) => {
      for (const k of [[PATHS.credito], ['credito'], [PATHS.pedidos]]) qc.invalidateQueries({ queryKey: k })
      const msg = { bloquear: 'Crédito bloqueado.', desbloquear: 'Crédito desbloqueado.', liberar: 'Pedido liberado: ya puede despacharse.', rechazar: 'Pedido cancelado.' }[v.p.kind]
      notify(msg)
      setPending(null)
    },
    onError: (e) => { notify(errorMessage(e), 'error'); setPending(null) },
  })

  const dialogText = {
    bloquear: ['Bloquear crédito', 'El cliente no podrá confirmar nuevos pedidos hasta que se desbloquee.', 'Bloquear'],
    desbloquear: ['Desbloquear crédito', 'El cliente podrá volver a confirmar pedidos dentro de su límite.', 'Desbloquear'],
    liberar: ['Liberar pedido retenido', 'Se aprueba la excepción de crédito y el pedido queda listo para despachar.', 'Liberar pedido'],
    rechazar: ['Cancelar pedido retenido', 'Se cancelará el pedido y se liberará el stock reservado.', 'Cancelar pedido'],
  } as const

  const btn = (title: string, icon: React.ReactNode, onClick: () => void, color?: 'success' | 'error') => (
    <Tooltip title={title}><IconButton size="small" color={color} aria-label={title} onClick={onClick}>{icon}</IconButton></Tooltip>
  )

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 0.5 }}>Gestión de crédito</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>Límites, saldos por cobrar y pedidos que excedieron el crédito.</Typography>
      <Tabs value={tab} onChange={(_e, v) => { setTab(v); setPage(0) }} sx={{ mb: 2 }}>
        <Tab label="Clientes" />
        <Tab label="Pedidos retenidos" />
      </Tabs>

      {tab === 0 && (
        <>
          <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
            <TextField size="small" placeholder="Buscar cliente" value={search} sx={{ flex: '1 1 260px' }}
              onChange={(e) => { setSearch(e.target.value); setPage(0) }}
              slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
            <TextField select size="small" label="Estado" value={estado} sx={{ width: 200 }} onChange={(e) => { setEstado(e.target.value); setPage(0) }}>
              <MenuItem value="">Todos</MenuItem>
              {ESTADOS_CREDITO.map((e) => <MenuItem key={e} value={e}>{estadoLabel(e)}</MenuItem>)}
            </TextField>
          </Paper>
          <DataTable
            data={clientes.data} loading={clientes.isLoading} error={clientes.error} page={page} size={size}
            onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
            emptyText={search || estado ? 'Ningún cliente con esos filtros.' : 'Aún no hay clientes con crédito configurado.'}
            columns={[
              { header: 'Cliente', render: (r) => String(r.clienteNombre) },
              { header: 'Límite', align: 'right', render: (r) => money(r.limiteCredito) },
              { header: 'Saldo pendiente', align: 'right', render: (r) => money(r.saldoPendiente) },
              { header: 'Vencido', align: 'right', render: (r) => <Typography variant="body2" color={Number(r.vencido) > 0 ? 'error' : undefined}>{money(r.vencido)}</Typography> },
              {
                header: 'Uso del crédito',
                render: (r) => {
                  const pct = Number(r.limiteCredito) > 0 ? Math.min(100, (Number(r.saldoPendiente) / Number(r.limiteCredito)) * 100) : 0
                  return (
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1, minWidth: 140 }}>
                      <LinearProgress variant="determinate" value={pct} color={pct >= 100 ? 'error' : pct >= 80 ? 'warning' : 'primary'} sx={{ flexGrow: 1, height: 6, borderRadius: 3 }} />
                      <Typography variant="caption">{Math.round(pct)}%</Typography>
                    </Box>
                  )
                },
              },
              { header: 'Disponible', align: 'right', render: (r) => money(r.creditoDisponible) },
              { header: 'Estado', render: (r) => <EstadoChip estado={r.estado} /> },
            ]}
            actions={(r) => (
              <>
                {btn('Estado de cuenta', <ReceiptIcon fontSize="small" />, () => setStatement(r.id))}
                {admin && btn('Cambiar límite y plazo', <EditIcon fontSize="small" />, () => setLimit(r))}
                {admin && r.estado !== 'BLOQUEADO' && btn('Bloquear crédito', <LockIcon fontSize="small" />, () => setPending({ kind: 'bloquear', row: r }), 'error')}
                {admin && r.estado === 'BLOQUEADO' && btn('Desbloquear crédito', <LockOpenIcon fontSize="small" />, () => setPending({ kind: 'desbloquear', row: r }), 'success')}
              </>
            )}
          />
        </>
      )}

      {tab === 1 && (
        <DataTable
          data={retenidos.data} loading={retenidos.isLoading} error={retenidos.error} page={page} size={size}
          onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
          emptyText="No hay pedidos retenidos por crédito."
          columns={[
            { header: 'Pedido', render: (r) => txt(r.numero) },
            { header: 'Cliente', render: (r) => txt(r.cliente ?? r.clienteNombre) },
            { header: 'Fecha', render: (r) => fecha(r.fecha) },
            { header: 'Total', align: 'right', render: (r) => money(r.total) },
            { header: 'Exceso', align: 'right', render: (r) => (r.excesoCredito == null ? '—' : money(r.excesoCredito)) },
          ]}
          actions={admin ? (r) => (
            <>
              {btn('Liberar pedido', <CheckIcon fontSize="small" />, () => setPending({ kind: 'liberar', row: r }), 'success')}
              {btn('Cancelar pedido', <CancelIcon fontSize="small" />, () => setPending({ kind: 'rechazar', row: r }), 'error')}
            </>
          ) : undefined}
        />
      )}

      {statement !== null && <EstadoCuentaDialog clienteId={statement} onClose={() => setStatement(null)} />}
      {limit && <LimiteDialog row={limit} onClose={() => setLimit(null)} />}
      {pending && (
        <MotivoDialog title={`${dialogText[pending.kind][0]} — ${txt(pending.row.numero ?? pending.row.clienteNombre)}`}
          text={dialogText[pending.kind][1]} confirmLabel={dialogText[pending.kind][2]} loading={run.isPending}
          onSubmit={(motivo) => run.mutate({ p: pending, motivo })} onClose={() => setPending(null)} />
      )}
    </Box>
  )
}
