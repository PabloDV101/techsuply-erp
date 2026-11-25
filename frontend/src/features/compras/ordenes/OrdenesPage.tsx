import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Box, Button, IconButton, InputAdornment, MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead,
  TablePagination, TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
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
import { ESTADOS_OC, EstadoChip, estadoLabel } from '../../../shared/EstadoChip'
import { fecha, money, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'
import OrdenDetalleDialog from './OrdenDetalleDialog'
import OrdenFormDialog from './OrdenFormDialog'

type Action = { kind: 'aprobar' | 'cancelar'; row: Row }

export default function OrdenesPage() {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [search, setSearch] = useState('')
  const [estado, setEstado] = useState('')
  const [form, setForm] = useState<{ id?: Row['id'] } | null>(null)
  const [viewing, setViewing] = useState<Row['id'] | null>(null)
  const [action, setAction] = useState<Action | null>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [PATHS.ordenes, page, size, search, estado],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (search) params[SEARCH_PARAM] = search
      if (estado) params.estado = estado
      return normalizePage((await http.get(PATHS.ordenes, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  const run = useMutation({
    mutationFn: (a: Action) => http.post(`${PATHS.ordenes}/${a.row.id}/${a.kind}`),
    onSuccess: (_r, a) => {
      qc.invalidateQueries({ queryKey: [PATHS.ordenes] })
      qc.invalidateQueries({ queryKey: ['orden', a.row.id] })
      notify(a.kind === 'aprobar' ? 'Orden aprobada.' : 'Orden cancelada.')
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
        {canWrite && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setForm({})}>Nueva orden</Button>}
      </Box>

      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField size="small" placeholder="Buscar por número o proveedor" value={search} sx={{ flex: '1 1 260px' }}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <TextField select size="small" label="Estado" value={estado} sx={{ width: 190 }} onChange={(e) => { setEstado(e.target.value); setPage(0) }}>
          <MenuItem value="">Todos</MenuItem>
          {ESTADOS_OC.map((e) => <MenuItem key={e} value={e}>{estadoLabel(e)}</MenuItem>)}
        </TextField>
      </Paper>

      <Paper elevation={0}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                {['Número', 'Proveedor', 'Fecha', 'Estado'].map((h) => <TableCell key={h} sx={{ fontWeight: 600 }}>{h}</TableCell>)}
                <TableCell align="right" sx={{ fontWeight: 600 }}>Total</TableCell>
                <TableCell align="right" sx={{ fontWeight: 600 }}>Acciones</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading && <TableRow><TableCell colSpan={6}>Cargando…</TableCell></TableRow>}
              {isError && <TableRow><TableCell colSpan={6} sx={{ color: 'error.main' }}>{errorMessage(error)}</TableCell></TableRow>}
              {!isLoading && !isError && data?.rows.length === 0 && (
                <TableRow><TableCell colSpan={6} sx={{ py: 4, textAlign: 'center' }}>
                  {search || estado ? 'Ningún resultado con esos filtros.' : 'Aún no hay órdenes. Crea la primera orden de compra.'}
                </TableCell></TableRow>
              )}
              {data?.rows.map((r) => (
                <TableRow key={r.id} hover>
                  <TableCell>{txt(r.numero)}</TableCell>
                  <TableCell>{txt(r.proveedor ?? r.proveedorNombre)}</TableCell>
                  <TableCell>{fecha(r.fecha)}</TableCell>
                  <TableCell><EstadoChip estado={r.estado} /></TableCell>
                  <TableCell align="right">{money(r.total)}</TableCell>
                  <TableCell align="right">
                    <Tooltip title="Ver detalle"><IconButton size="small" aria-label="Ver detalle" onClick={() => setViewing(r.id)}><VisibilityIcon fontSize="small" /></IconButton></Tooltip>
                    {canWrite && r.estado === 'BORRADOR' && (
                      <Tooltip title="Editar"><IconButton size="small" aria-label="Editar" onClick={() => setForm({ id: r.id })}><EditIcon fontSize="small" /></IconButton></Tooltip>
                    )}
                    {canApprove && r.estado === 'BORRADOR' && (
                      <Tooltip title="Aprobar"><IconButton size="small" color="success" aria-label="Aprobar" onClick={() => setAction({ kind: 'aprobar', row: r })}><CheckIcon fontSize="small" /></IconButton></Tooltip>
                    )}
                    {canWrite && (r.estado === 'BORRADOR' || r.estado === 'APROBADA') && (
                      <Tooltip title="Cancelar orden"><IconButton size="small" color="error" aria-label="Cancelar orden" onClick={() => setAction({ kind: 'cancelar', row: r })}><CancelIcon fontSize="small" /></IconButton></Tooltip>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={data?.total ?? 0} page={page} rowsPerPage={size} rowsPerPageOptions={[10, 25, 50]}
          onPageChange={(_e, p) => setPage(p)} onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}
          labelRowsPerPage="Filas por página" labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      </Paper>

      {form && <OrdenFormDialog id={form.id} onClose={() => setForm(null)} />}
      {viewing !== null && <OrdenDetalleDialog id={viewing} onClose={() => setViewing(null)} />}
      <ConfirmDialog
        open={!!action}
        title={action?.kind === 'aprobar' ? `Aprobar orden ${txt(action.row.numero)}` : `Cancelar orden ${txt(action?.row.numero)}`}
        text={action?.kind === 'aprobar'
          ? 'Una orden aprobada ya no se puede editar y queda lista para recibir mercadería.'
          : 'La orden dejará de aceptar recepciones. Esta acción no se puede deshacer.'}
        confirmLabel={action?.kind === 'aprobar' ? 'Aprobar orden' : 'Cancelar orden'}
        onConfirm={() => action && run.mutate(action)}
        onClose={() => setAction(null)}
      />
    </Box>
  )
}
