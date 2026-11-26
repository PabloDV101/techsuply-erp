import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Box, Paper, Typography } from '@mui/material'
import { http } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { normalizePage } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { EstadoChip, estadoLabel } from '../../../shared/EstadoChip'
import { fechaHora, num, productoLabel, txt } from '../../../shared/format'
import { AlmacenSelect, ProductoSelect } from '../../../shared/selects'
import { useLookup } from '../../../shared/api'

export default function MovimientosPage() {
  const [params] = useSearchParams()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [productoId, setProductoId] = useState(params.get('productoId') ?? '')
  const [almacenId, setAlmacenId] = useState(params.get('almacenId') ?? '')
  const products = useLookup(PATHS.productos)
  const warehouses = useLookup(PATHS.almacenes)
  const productMap = new Map((products.data ?? []).map((p) => [String(p.id), p]))
  const warehouseMap = new Map((warehouses.data ?? []).map((w) => [String(w.id), w]))

  const q = useQuery({
    queryKey: [PATHS.movimientos, page, size, productoId, almacenId],
    queryFn: async () => {
      const p: Record<string, unknown> = { page, size }
      if (productoId) p.productoId = productoId
      if (almacenId) p.almacenId = almacenId
      return normalizePage((await http.get(PATHS.movimientos, { params: p })).data)
    },
    placeholderData: keepPreviousData,
  })

  const filtered = !!(productoId || almacenId)

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Movimientos de inventario (kardex)</Typography>
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <ProductoSelect value={productoId} onChange={(v) => { setProductoId(v); setPage(0) }} width={280} />
        <AlmacenSelect value={almacenId} onChange={(v) => { setAlmacenId(v); setPage(0) }} width={200} />
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={filtered ? 'Ningún movimiento con esos filtros.' : 'Aún no hay movimientos registrados.'}
        columns={[
          { header: 'Fecha', render: (r) => fechaHora(r.fecha) },
          { header: 'Tipo', render: (r) => <EstadoChip estado={r.tipo} /> },
          { header: 'Motivo', render: (r) => estadoLabel(r.motivo) },
          { header: 'Producto', render: (r) => productoLabel(productMap.get(String(r.productoId)) ?? { id: r.productoId }) },
          { header: 'Almacén', render: (r) => txt(warehouseMap.get(String(r.almacenId)) ?? { id: r.almacenId }) },
          { header: 'Cantidad', align: 'right', render: (r) => num(r.cantidad) },
          { header: 'Costo unitario', align: 'right', render: (r) => num(r.costoUnitario) },
          { header: 'Usuario ID', align: 'right', render: (r) => txt(r.usuarioId) },
        ]}
      />
    </Box>
  )
}
