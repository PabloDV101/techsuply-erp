import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Box, IconButton, Paper, Tooltip, Typography } from '@mui/material'
import HistoryIcon from '@mui/icons-material/History'
import { http } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { normalizePage, useLookup } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { num, productoLabel, txt } from '../../../shared/format'
import { AlmacenSelect, ProductoSelect } from '../../../shared/selects'

export default function ExistenciasPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [productoId, setProductoId] = useState('')
  const [almacenId, setAlmacenId] = useState('')
  const products = useLookup(PATHS.productos)
  const warehouses = useLookup(PATHS.almacenes)
  const productMap = new Map((products.data ?? []).map((p) => [String(p.id), p]))
  const warehouseMap = new Map((warehouses.data ?? []).map((w) => [String(w.id), w]))

  const q = useQuery({
    queryKey: [PATHS.existencias, page, size, productoId, almacenId],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (productoId) params.productoId = productoId
      if (almacenId) params.almacenId = almacenId
      return normalizePage((await http.get(PATHS.existencias, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 2 }}>Existencias</Typography>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <ProductoSelect value={productoId} onChange={(v) => { setProductoId(v); setPage(0) }} width={300} />
        <AlmacenSelect value={almacenId} onChange={(v) => { setAlmacenId(v); setPage(0) }} width={220} />
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={productoId || almacenId ? 'Ningún resultado con esos filtros.' : 'Aún no hay existencias. Se generan al recibir mercadería o registrar entradas.'}
        columns={[
          { header: 'Producto', render: (r) => productoLabel(productMap.get(String(r.productoId)) ?? { id: r.productoId }) },
          { header: 'Almacén', render: (r) => txt(warehouseMap.get(String(r.almacenId)) ?? { id: r.almacenId }) },
          { header: 'Físico', align: 'right', render: (r) => num(r.cantidadFisica) },
          { header: 'Reservado', align: 'right', render: (r) => num(r.cantidadReservada) },
          { header: 'Disponible', align: 'right', render: (r) => num(r.cantidadDisponible ?? r.cantidadFisica) },
        ]}
        actions={(r) => (
          <Tooltip title="Ver kardex">
            <IconButton size="small" aria-label="Ver kardex"
              onClick={() => navigate(`/inventario/movimientos?productoId=${r.productoId}&almacenId=${r.almacenId}`)}>
              <HistoryIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      />
    </Box>
  )
}
