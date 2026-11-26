import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Box, Button, IconButton, MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead,
  TablePagination, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import SendIcon from '@mui/icons-material/SendOutlined'
import CheckIcon from '@mui/icons-material/CheckCircleOutlined'
import LocalShippingIcon from '@mui/icons-material/LocalShippingOutlined'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, useLookup, type Row } from '../../../shared/api'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { ESTADOS_OC, EstadoChip, estadoLabel } from '../../../shared/EstadoChip'
import { fechaHora, money, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'
import OrdenDetalleDialog from './OrdenDetalleDialog'
import OrdenFormDialog from './OrdenFormDialog'

type ActionKind = 'submit' | 'approve' | 'send'
interface PurchaseOrder extends Row { folio?: string; proveedorId?: number; estado?: string; total?: number }
interface Action { kind: ActionKind; row: PurchaseOrder }

const actionLabel: Record<ActionKind, string> = {
  submit: 'Enviar a aprobación', approve: 'Aprobar orden', send: 'Marcar como enviada',
}

export default function OrdenesPage() {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [proveedorId, setProveedorId] = useState('')
  const [estado, setEstado] = useState('')
  const [creating, setCreating] = useState(false)
  const [viewing, setViewing] = useState<Row['id'] | null>(null)
  const [action, setAction] = useState<Action | null>(null)
  const proveedores = useLookup(PATHS.proveedores, {}, can(PERM.proveedoresLeer), null).data ?? []
  const proveedorById = new Map(proveedores.map((item) => [String(item.id), item]))

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [PATHS.ordenes, page, size, proveedorId, estado],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size, sort: 'fecha', direction: 'desc' }
      if (proveedorId) params.proveedorId = Number(proveedorId)
      if (estado) params.estado = estado
      return normalizePage((await http.get(PATHS.ordenes, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  const run = useMutation({
    mutationFn: (selected: Action) => http.post(`${PATHS.ordenes}/${selected.row.id}/${selected.kind}`),
    onSuccess: (_response, selected) => {
      qc.invalidateQueries({ queryKey: [PATHS.ordenes] })
      qc.invalidateQueries({ queryKey: ['orden', selected.row.id] })
      notify(selected.kind === 'approve' ? 'Orden aprobada.' : selected.kind === 'send' ? 'Orden marcada como enviada.' : 'Orden enviada a aprobación.')
      setAction(null)
    },
    onError: (e) => { notify(errorMessage(e), 'error'); setAction(null) },
  })

  const canWrite = can(PERM.ocWrite)
  const canApprove = can(PERM.ocApprove)

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Órdenes de compra</Typography>
        {canWrite && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Nueva orden</Button>}
      </Box>
      {canApprove && <Alert severity="info" sx={{ mb: 2 }}>La aprobación debe realizarla una persona distinta a quien creó la orden. El servidor valida esta regla.</Alert>}

      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField select size="small" label="Proveedor" value={proveedorId} sx={{ minWidth: 250 }}
          onChange={(event) => { setProveedorId(event.target.value); setPage(0) }}>
          <MenuItem value="">Todos los proveedores</MenuItem>
          {proveedores.map((supplier) => <MenuItem key={supplier.id} value={String(supplier.id)}>
            {supplier.codigo} — {supplier.nombreRazonSocial}
          </MenuItem>)}
        </TextField>
        <TextField select size="small" label="Estado" value={estado} sx={{ minWidth: 220 }} onChange={(event) => { setEstado(event.target.value); setPage(0) }}>
          <MenuItem value="">Todos</MenuItem>
          {ESTADOS_OC.map((value) => <MenuItem key={value} value={value}>{estadoLabel(value)}</MenuItem>)}
        </TextField>
      </Paper>

      <Paper elevation={0}>
        <TableContainer>
          <Table size="small">
            <TableHead><TableRow>
              {['Folio', 'Proveedor', 'Fecha', 'Estado'].map((heading) => <TableCell key={heading} sx={{ fontWeight: 600 }}>{heading}</TableCell>)}
              <TableCell align="right" sx={{ fontWeight: 600 }}>Total</TableCell>
              <TableCell align="right" sx={{ fontWeight: 600 }}>Acciones</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {isLoading && <TableRow><TableCell colSpan={6}>Cargando…</TableCell></TableRow>}
              {isError && <TableRow><TableCell colSpan={6} sx={{ color: 'error.main' }}>{errorMessage(error)}</TableCell></TableRow>}
              {!isLoading && !isError && data?.rows.length === 0 && <TableRow><TableCell colSpan={6} sx={{ py: 4, textAlign: 'center' }}>
                {proveedorId || estado ? 'Ningún resultado con esos filtros.' : 'Aún no hay órdenes de compra.'}
              </TableCell></TableRow>}
              {data?.rows.map((row) => {
                const order = row as PurchaseOrder
                const supplier = proveedorById.get(String(order.proveedorId))
                return <TableRow key={row.id} hover>
                  <TableCell>{txt(order.folio)}</TableCell>
                  <TableCell>{supplier ? `${supplier.codigo} — ${supplier.nombreRazonSocial}` : `Proveedor #${order.proveedorId ?? '—'}`}</TableCell>
                  <TableCell>{fechaHora(order.fecha)}</TableCell>
                  <TableCell><EstadoChip estado={order.estado} /></TableCell>
                  <TableCell align="right">{money(order.total)}</TableCell>
                  <TableCell align="right">
                    <Tooltip title="Ver detalle"><IconButton size="small" aria-label="Ver detalle" onClick={() => setViewing(row.id)}><VisibilityIcon fontSize="small" /></IconButton></Tooltip>
                    {canWrite && order.estado === 'BORRADOR' && <Tooltip title="Enviar a aprobación"><IconButton size="small" aria-label="Enviar a aprobación" onClick={() => setAction({ kind: 'submit', row: order })}><SendIcon fontSize="small" /></IconButton></Tooltip>}
                    {canApprove && order.estado === 'PENDIENTE_APROBACION' && <Tooltip title="Aprobar"><IconButton size="small" color="success" aria-label="Aprobar" onClick={() => setAction({ kind: 'approve', row: order })}><CheckIcon fontSize="small" /></IconButton></Tooltip>}
                    {canWrite && order.estado === 'APROBADA' && <Tooltip title="Marcar como enviada"><IconButton size="small" aria-label="Marcar como enviada" onClick={() => setAction({ kind: 'send', row: order })}><LocalShippingIcon fontSize="small" /></IconButton></Tooltip>}
                  </TableCell>
                </TableRow>
              })}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={data?.total ?? 0} page={page} rowsPerPage={size} rowsPerPageOptions={[10, 25, 50]}
          onPageChange={(_event, next) => setPage(next)} onRowsPerPageChange={(event) => { setSize(Number(event.target.value)); setPage(0) }}
          labelRowsPerPage="Filas por página" labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      </Paper>

      {creating && <OrdenFormDialog onClose={() => setCreating(false)} />}
      {viewing !== null && <OrdenDetalleDialog id={viewing} onClose={() => setViewing(null)} />}
      <ConfirmDialog open={!!action} title={`${action ? actionLabel[action.kind] : ''}${action ? ` ${txt(action.row.folio)}` : ''}`}
        text={action?.kind === 'approve'
          ? 'Confirma que revisaste la orden. El backend rechazará la aprobación si la creó tu mismo usuario.'
          : action?.kind === 'send'
            ? 'Registra que esta orden aprobada fue enviada al proveedor. No se envía correo desde esta acción.'
            : 'La orden pasará a pendiente de aprobación y ya no podrá modificarse desde esta interfaz.'}
        confirmLabel={action ? actionLabel[action.kind] : 'Confirmar'}
        onConfirm={() => action && run.mutate(action)} onClose={() => setAction(null)} />
    </Box>
  )
}
