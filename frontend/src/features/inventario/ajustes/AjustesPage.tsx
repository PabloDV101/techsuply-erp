import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Box, Button, Chip, Paper, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import { http } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { fechaHora, num, productoLabel, txt } from '../../../shared/format'
import { AlmacenSelect, ProductoSelect } from '../../../shared/selects'
import { useAuth } from '../../auth/AuthContext'
import AjusteFormDialog, { MOTIVOS_AJUSTE } from './AjusteFormDialog'

export default function AjustesPage() {
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [productoId, setProductoId] = useState('')
  const [almacenId, setAlmacenId] = useState('')
  const [creating, setCreating] = useState(false)

  const q = useQuery({
    queryKey: [PATHS.ajustes, page, size, productoId, almacenId],
    queryFn: async () => {
      const p: Record<string, unknown> = { page, size }
      if (productoId) p.productoId = productoId
      if (almacenId) p.almacenId = almacenId
      return normalizePage((await http.get(PATHS.ajustes, { params: p })).data)
    },
    placeholderData: keepPreviousData,
  })

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Ajustes de inventario</Typography>
        {can(PERM.inventarioAjustar) && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Nuevo ajuste</Button>}
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <ProductoSelect value={productoId} onChange={(v) => { setProductoId(v); setPage(0) }} width={280} />
        <AlmacenSelect value={almacenId} onChange={(v) => { setAlmacenId(v); setPage(0) }} width={200} />
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={productoId || almacenId ? 'Ningún ajuste con esos filtros.' : 'Aún no hay ajustes. Sirven para corregir diferencias entre el sistema y lo contado.'}
        columns={[
          { header: 'Fecha', render: (r) => fechaHora(r.fecha) },
          { header: 'Producto', render: (r) => productoLabel(r.producto) },
          { header: 'Almacén', render: (r) => txt(r.almacen) },
          {
            header: 'Ajuste', align: 'right',
            render: (r) => <Chip size="small" variant="outlined" color={r.tipo === 'POSITIVO' ? 'success' : 'error'}
              label={`${r.tipo === 'POSITIVO' ? '+' : '−'}${num(r.cantidad)}`} />,
          },
          { header: 'Motivo', render: (r) => MOTIVOS_AJUSTE.find((m) => m.value === r.motivo)?.label ?? txt(r.motivo) },
          { header: 'Observaciones', render: (r) => txt(r.observaciones) },
          { header: 'Usuario', render: (r) => txt(r.usuario) },
        ]}
      />
      {creating && <AjusteFormDialog onClose={() => setCreating(false)} />}
    </Box>
  )
}
