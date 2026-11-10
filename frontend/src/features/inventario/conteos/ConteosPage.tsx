import { useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Box, Button, IconButton, MenuItem, Paper, TextField, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import FactCheckIcon from '@mui/icons-material/FactCheckOutlined'
import CancelIcon from '@mui/icons-material/CancelOutlined'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, type Row } from '../../../shared/api'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import DataTable from '../../../shared/DataTable'
import { ESTADOS_CONTEO, EstadoChip, estadoLabel } from '../../../shared/EstadoChip'
import { fecha, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'
import ConteoDetalleDialog from './ConteoDetalleDialog'
import ConteoNuevoDialog from './ConteoNuevoDialog'

export default function ConteosPage() {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [estado, setEstado] = useState('')
  const [creating, setCreating] = useState(false)
  const [viewing, setViewing] = useState<Row['id'] | null>(null)
  const [toCancel, setToCancel] = useState<Row | null>(null)

  const q = useQuery({
    queryKey: [PATHS.conteos, page, size, estado],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (estado) params.estado = estado
      return normalizePage((await http.get(PATHS.conteos, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  const cancel = useMutation({
    mutationFn: (r: Row) => http.post(`${PATHS.conteos}/${r.id}/cancelar`),
    onSuccess: () => { qc.invalidateQueries({ queryKey: [PATHS.conteos] }); notify('Conteo cancelado.'); setToCancel(null) },
    onError: (e) => { notify(errorMessage(e), 'error'); setToCancel(null) },
  })

  const canCount = can(PERM.inventarioContar)

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Conteos físicos</Typography>
        {canCount && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Nuevo conteo</Button>}
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2 }}>
        <TextField select size="small" label="Estado" value={estado} sx={{ width: 190 }} onChange={(e) => { setEstado(e.target.value); setPage(0) }}>
          <MenuItem value="">Todos</MenuItem>
          {ESTADOS_CONTEO.map((e) => <MenuItem key={e} value={e}>{estadoLabel(e)}</MenuItem>)}
        </TextField>
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={estado ? 'Ningún conteo con ese estado.' : 'Aún no hay conteos. Inicia uno para comparar el sistema con lo que hay en el almacén.'}
        columns={[
          { header: 'Número', render: (r) => txt(r.numero) },
          { header: 'Almacén', render: (r) => txt(r.almacen) },
          { header: 'Fecha', render: (r) => fecha(r.fecha) },
          { header: 'Estado', render: (r) => <EstadoChip estado={r.estado} /> },
          { header: 'Con diferencia', align: 'right', render: (r) => txt(r.lineasConDiferencia) },
        ]}
        actions={(r) => (
          <>
            <Tooltip title={r.estado === 'EN_CONTEO' && canCount ? 'Contar' : 'Ver detalle'}>
              <IconButton size="small" aria-label="Abrir conteo" onClick={() => setViewing(r.id)}><FactCheckIcon fontSize="small" /></IconButton>
            </Tooltip>
            {canCount && r.estado === 'EN_CONTEO' && (
              <Tooltip title="Cancelar conteo">
                <IconButton size="small" color="error" aria-label="Cancelar conteo" onClick={() => setToCancel(r)}><CancelIcon fontSize="small" /></IconButton>
              </Tooltip>
            )}
          </>
        )}
      />
      {creating && <ConteoNuevoDialog onClose={() => setCreating(false)} onCreated={(id) => { setCreating(false); if (id !== undefined) setViewing(id) }} />}
      {viewing !== null && <ConteoDetalleDialog id={viewing} onClose={() => setViewing(null)} />}
      <ConfirmDialog open={!!toCancel} title={`Cancelar conteo ${txt(toCancel?.numero)}`}
        text="Se descartarán las cantidades registradas y no se generará ningún ajuste." confirmLabel="Cancelar conteo"
        onConfirm={() => toCancel && cancel.mutate(toCancel)} onClose={() => setToCancel(null)} />
    </Box>
  )
}
