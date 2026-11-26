import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Box, IconButton, Paper, Tooltip, Typography } from '@mui/material'
import AddShoppingCartIcon from '@mui/icons-material/AddShoppingCart'
import { http } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, useLookup } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { num } from '../../../shared/format'
import { ProductoSelect } from '../../../shared/selects'
import { useAuth } from '../../auth/AuthContext'

export default function AlertasPage() {
  const navigate = useNavigate()
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [productoId, setProductoId] = useState('')
  const products = useLookup(PATHS.productos)

  const q = useQuery({
    queryKey: [PATHS.alertas, page, size, productoId],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (productoId) params.productoId = productoId
      return normalizePage((await http.get(PATHS.alertas, { params })).data)
    },
    placeholderData: keepPreviousData,
  })
  const summary = useQuery({
    queryKey: [PATHS.alertasResumen],
    queryFn: async () => (await http.get(PATHS.alertasResumen)).data,
  })
  const productMap = new Map((products.data ?? []).map((p) => [String(p.id), p]))
  const canBuy = can(PERM.ocWrite)

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 0.5 }}>Alertas globales de stock</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>Productos activos cuyo disponible global en almacenes activos está en o debajo de su punto de reorden global. No es un indicador por almacén.</Typography>
      <Paper elevation={0} sx={{ p: 2, mb: 2 }}>
        <Typography variant="body2" color="text.secondary">Productos en alerta (resumen global)</Typography>
        <Typography variant="h4" color="primary" sx={{ fontWeight: 700 }}>{summary.isLoading ? '…' : summary.error ? '—' : summary.data?.productosEnAlerta ?? 0}</Typography>
        {summary.data?.asOf && <Typography variant="caption" color="text.secondary">Consulta iniciada: {new Date(summary.data.asOf).toLocaleString()}</Typography>}
      </Paper>
      <Paper elevation={0} sx={{ p: 2, mb: 2 }}>
        <ProductoSelect value={productoId} onChange={(v) => { setProductoId(v); setPage(0) }} width={320} />
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={productoId ? 'Este producto no tiene alerta global activa.' : 'Sin alertas globales activas.'}
        columns={[
          { header: 'Producto', render: (r) => `${r.nombre ?? productMap.get(String(r.productoId))?.descripcion ?? `Producto ${r.productoId}`} (${r.sku ?? productMap.get(String(r.productoId))?.codigo ?? r.productoId})` },
          { header: 'Disponible global', align: 'right', render: (r) => num(r.disponibleGlobal) },
          { header: 'Punto de reorden global', align: 'right', render: (r) => num(r.puntoReorden) },
          { header: 'Cantidad de reorden configurada', align: 'right', render: (r) => num(r.cantidadReorden) },
        ]}
        actions={canBuy ? () => <Tooltip title="Ir a órdenes de compra"><IconButton size="small" aria-label="Ir a órdenes de compra" onClick={() => navigate('/ordenes-compra')}><AddShoppingCartIcon fontSize="small" /></IconButton></Tooltip> : undefined}
      />
    </Box>
  )
}
