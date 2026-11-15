import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Box, Button, IconButton, MenuItem, Paper, TextField, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import { http } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, type Row } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { ESTADOS_RECEPCION, EstadoChip, estadoLabel } from '../../../shared/EstadoChip'
import { fecha, txt } from '../../../shared/format'
import { useAuth } from '../../auth/AuthContext'
import DespachoDetalleDialog from './DespachoDetalleDialog'
import DespachoFormDialog from './DespachoFormDialog'

export default function DespachosPage() {
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [estado, setEstado] = useState('')
  const [creating, setCreating] = useState(false)
  const [viewing, setViewing] = useState<Row['id'] | null>(null)

  const q = useQuery({
    queryKey: [PATHS.despachos, page, size, estado],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (estado) params.estado = estado
      return normalizePage((await http.get(PATHS.despachos, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Despachos</Typography>
        {can(PERM.despachoWrite) && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Nuevo despacho</Button>}
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2 }}>
        <TextField select size="small" label="Estado" value={estado} sx={{ width: 190 }} onChange={(e) => { setEstado(e.target.value); setPage(0) }}>
          <MenuItem value="">Todos</MenuItem>
          {ESTADOS_RECEPCION.map((e) => <MenuItem key={e} value={e}>{estadoLabel(e)}</MenuItem>)}
        </TextField>
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={estado ? 'Ningún despacho con ese estado.' : 'Aún no hay despachos. Registra el primero contra una orden de venta confirmada.'}
        columns={[
          { header: 'Número', render: (r) => txt(r.numero) },
          { header: 'Orden de venta', render: (r) => txt(r.pedido?.numero ?? r.pedidoNumero) },
          { header: 'Cliente', render: (r) => txt(r.cliente ?? r.pedido?.cliente ?? r.clienteNombre) },
          { header: 'Fecha', render: (r) => fecha(r.fecha) },
          { header: 'Estado', render: (r) => <EstadoChip estado={r.estado} /> },
          { header: 'Factura', render: (r) => txt(r.factura?.numero ?? r.facturaNumero) },
        ]}
        actions={(r) => (
          <Tooltip title="Ver detalle"><IconButton size="small" aria-label="Ver detalle" onClick={() => setViewing(r.id)}><VisibilityIcon fontSize="small" /></IconButton></Tooltip>
        )}
      />
      {creating && <DespachoFormDialog onClose={() => setCreating(false)} />}
      {viewing !== null && <DespachoDetalleDialog id={viewing} onClose={() => setViewing(null)} />}
    </Box>
  )
}
