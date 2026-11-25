import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Box, IconButton, MenuItem, Paper, TextField, Tooltip, Typography } from '@mui/material'
import EditIcon from '@mui/icons-material/Edit'
import AddShoppingCartIcon from '@mui/icons-material/AddShoppingCart'
import { http } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, type Row } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { EstadoChip, estadoLabel, NIVELES_ALERTA } from '../../../shared/EstadoChip'
import { num, productoLabel, txt } from '../../../shared/format'
import { AlmacenSelect } from '../../../shared/selects'
import { useAuth } from '../../auth/AuthContext'
import StockMinimoDialog from './StockMinimoDialog'

export default function AlertasPage() {
  const navigate = useNavigate()
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [nivel, setNivel] = useState('')
  const [almacenId, setAlmacenId] = useState('')
  const [editing, setEditing] = useState<Row | null>(null)

  const q = useQuery({
    queryKey: [PATHS.alertas, page, size, nivel, almacenId],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (nivel) params.nivel = nivel
      if (almacenId) params.almacenId = almacenId
      return normalizePage((await http.get(PATHS.alertas, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  const canEdit = can(PERM.inventarioAdmin)
  const canBuy = can(PERM.ocWrite)

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 0.5 }}>Alertas de stock</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>Productos cuyo disponible está por debajo del mínimo configurado.</Typography>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField select size="small" label="Nivel" value={nivel} sx={{ width: 190 }} onChange={(e) => { setNivel(e.target.value); setPage(0) }}>
          <MenuItem value="">Todos</MenuItem>
          {NIVELES_ALERTA.map((n) => <MenuItem key={n} value={n}>{estadoLabel(n)}</MenuItem>)}
        </TextField>
        <AlmacenSelect value={almacenId} onChange={(v) => { setAlmacenId(v); setPage(0) }} width={220} />
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={nivel || almacenId ? 'Ninguna alerta con esos filtros.' : 'Sin alertas: todo el inventario está sobre su mínimo.'}
        columns={[
          { header: 'Nivel', render: (r) => <EstadoChip estado={r.nivel} /> },
          { header: 'Producto', render: (r) => productoLabel(r.producto) },
          { header: 'Almacén', render: (r) => txt(r.almacen) },
          { header: 'Disponible', align: 'right', render: (r) => num(r.cantidadDisponible) },
          { header: 'Mínimo', align: 'right', render: (r) => num(r.stockMinimo) },
          { header: 'Faltante', align: 'right', render: (r) => num(r.faltante ?? Math.max(0, Number(r.stockMinimo) - Number(r.cantidadDisponible))) },
        ]}
        actions={canEdit || canBuy ? (r) => (
          <>
            {canEdit && <Tooltip title="Cambiar stock mínimo"><IconButton size="small" aria-label="Cambiar stock mínimo" onClick={() => setEditing(r)}><EditIcon fontSize="small" /></IconButton></Tooltip>}
            {canBuy && <Tooltip title="Ir a órdenes de compra"><IconButton size="small" aria-label="Ir a órdenes de compra" onClick={() => navigate('/ordenes-compra')}><AddShoppingCartIcon fontSize="small" /></IconButton></Tooltip>}
          </>
        ) : undefined}
      />
      {editing && <StockMinimoDialog row={editing} onClose={() => setEditing(null)} />}
    </Box>
  )
}
