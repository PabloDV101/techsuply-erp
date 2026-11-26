import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, MenuItem, Paper, TextField, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import CheckIcon from '@mui/icons-material/CheckOutlined'
import CloseIcon from '@mui/icons-material/CloseOutlined'
import PlayArrowIcon from '@mui/icons-material/PlayArrowOutlined'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, useLookup } from '../../../shared/api'
import type { Row } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { EstadoChip, estadoLabel } from '../../../shared/EstadoChip'
import { fechaHora, num, productoLabel, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { AlmacenSelect } from '../../../shared/selects'
import { useAuth } from '../../auth/AuthContext'
import AjusteFormDialog from './AjusteFormDialog'

const ESTADOS = ['PENDIENTE', 'APROBADO', 'APLICADO', 'RECHAZADO']

export default function AjustesPage() {
  const { can, user } = useAuth()
  const qc = useQueryClient()
  const notify = useNotify()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [almacenId, setAlmacenId] = useState('')
  const [estado, setEstado] = useState('')
  const [creating, setCreating] = useState(false)
  const [rejecting, setRejecting] = useState<Row | null>(null)
  const [rejectReason, setRejectReason] = useState('')
  const warehouses = useLookup(PATHS.almacenes)
  const products = useLookup(PATHS.productos)
  const warehouseMap = new Map((warehouses.data ?? []).map((w) => [String(w.id), w]))
  const productMap = new Map((products.data ?? []).map((p) => [String(p.id), p]))

  const q = useQuery({
    queryKey: [PATHS.ajustes, page, size, almacenId, estado],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (almacenId) params.almacenId = almacenId
      if (estado) params.estado = estado
      return normalizePage((await http.get(PATHS.ajustes, { params })).data)
    },
    placeholderData: keepPreviousData,
  })
  const refresh = () => {
    void qc.invalidateQueries({ queryKey: [PATHS.ajustes] })
    void qc.invalidateQueries({ queryKey: [PATHS.existencias] })
    void qc.invalidateQueries({ queryKey: [PATHS.movimientos] })
    void qc.invalidateQueries({ queryKey: [PATHS.alertas] })
    void qc.invalidateQueries({ queryKey: [PATHS.alertasResumen] })
  }
  const action = useMutation({
    mutationFn: ({ row, action: name }: { row: Row; action: 'approve' | 'apply' }) => http.post(`${PATHS.ajustes}/${row.id}/${name}`),
    onSuccess: (_data, variables) => { refresh(); notify(variables.action === 'approve' ? 'Ajuste aprobado.' : 'Ajuste aplicado; se actualizaron existencias y kardex.') },
    onError: (e) => notify(errorMessage(e), 'error'),
  })
  const reject = useMutation({
    mutationFn: ({ id, reason }: { id: Row['id']; reason: string }) => http.post(`${PATHS.ajustes}/${id}/reject`, { motivo: reason }),
    onSuccess: () => { refresh(); notify('Solicitud de ajuste rechazada.'); setRejecting(null); setRejectReason('') },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Ajustes de inventario</Typography>
        {can(PERM.inventarioAjustar) && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Solicitar ajuste</Button>}
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <AlmacenSelect value={almacenId} onChange={(v) => { setAlmacenId(v); setPage(0) }} width={240} />
        <TextField select size="small" label="Estado" value={estado} sx={{ width: 190 }} onChange={(e) => { setEstado(e.target.value); setPage(0) }}>
          <MenuItem value="">Todos</MenuItem>{ESTADOS.map((e) => <MenuItem key={e} value={e}>{estadoLabel(e)}</MenuItem>)}
        </TextField>
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={almacenId || estado ? 'Ninguna solicitud con esos filtros.' : 'Aún no hay solicitudes de ajuste.'}
        columns={[
          { header: 'Folio', render: (r) => txt(r.folio) },
          { header: 'Fecha', render: (r) => fechaHora(r.fecha) },
          { header: 'Almacén', render: (r) => txt(warehouseMap.get(String(r.almacenId)) ?? { id: r.almacenId }) },
          { header: 'Estado', render: (r) => <EstadoChip estado={r.estado} /> },
          { header: 'Motivo', render: (r) => txt(r.motivo) },
          { header: 'Líneas', render: (r) => (r.detalles ?? []).map((d: any) => `${productoLabel(productMap.get(String(d.productoId)) ?? { id: d.productoId })}: ${d.tipo === 'POSITIVO' ? '+' : '−'}${num(d.cantidad)}`).join('; ') },
          { header: 'Solicitó / aprobó', render: (r) => `${r.solicitadoPorUsuarioId ?? '—'} / ${r.autorizadoPorUsuarioId ?? '—'}` },
        ]}
        actions={(r) => <>
          {r.estado === 'PENDIENTE' && can(PERM.ajustesAprobar) && Number(r.solicitadoPorUsuarioId) !== user?.id && <>
            <Tooltip title="Aprobar"><IconButton size="small" aria-label={`Aprobar ${r.folio}`} onClick={() => action.mutate({ row: r, action: 'approve' })} disabled={action.isPending}><CheckIcon /></IconButton></Tooltip>
            <Tooltip title="Rechazar"><IconButton size="small" aria-label={`Rechazar ${r.folio}`} onClick={() => setRejecting(r)}><CloseIcon /></IconButton></Tooltip>
          </>}
          {r.estado === 'PENDIENTE' && can(PERM.ajustesAprobar) && Number(r.solicitadoPorUsuarioId) === user?.id && <Typography variant="caption" color="text.secondary">Requiere otro aprobador</Typography>}
          {r.estado === 'APROBADO' && can(PERM.ajustesAplicar) && <Tooltip title="Aplicar ajuste"><IconButton size="small" aria-label={`Aplicar ${r.folio}`} onClick={() => action.mutate({ row: r, action: 'apply' })} disabled={action.isPending}><PlayArrowIcon /></IconButton></Tooltip>}
        </>}
      />
      {creating && <AjusteFormDialog onClose={() => setCreating(false)} />}
      <Dialog open={!!rejecting} onClose={() => setRejecting(null)} fullWidth maxWidth="xs">
        <DialogTitle>Rechazar ajuste {txt(rejecting?.folio)}</DialogTitle>
        <DialogContent><TextField autoFocus fullWidth multiline minRows={3} sx={{ mt: 1 }} label="Motivo de rechazo" value={rejectReason} onChange={(e) => setRejectReason(e.target.value)} slotProps={{ htmlInput: { maxLength: 1000 } }} /></DialogContent>
        <DialogActions><Button onClick={() => setRejecting(null)}>Cancelar</Button><Button color="error" variant="contained" disabled={!rejectReason.trim() || reject.isPending} onClick={() => rejecting && reject.mutate({ id: rejecting.id, reason: rejectReason.trim() })}>Rechazar</Button></DialogActions>
      </Dialog>
    </Box>
  )
}
