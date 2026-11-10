import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Box, FormControlLabel, IconButton, InputAdornment, Paper, Switch, TextField, Tooltip, Typography } from '@mui/material'
import SearchIcon from '@mui/icons-material/Search'
import HistoryIcon from '@mui/icons-material/History'
import { http } from '../../../api/http'
import { PATHS, SEARCH_PARAM } from '../../../config/contract'
import { normalizePage } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { EstadoChip } from '../../../shared/EstadoChip'
import { num, productoLabel, txt } from '../../../shared/format'
import { AlmacenSelect } from '../../../shared/selects'

export function nivelStock(r: Record<string, any>): string {
  const disp = Number(r.cantidadDisponible ?? r.cantidadFisica ?? 0)
  const min = Number(r.stockMinimo ?? 0)
  if (disp <= 0) return 'AGOTADO'
  if (min > 0 && disp < min) return 'BAJO'
  return 'OK'
}

export default function ExistenciasPage() {
  const navigate = useNavigate()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [search, setSearch] = useState('')
  const [almacenId, setAlmacenId] = useState('')
  const [bajo, setBajo] = useState(false)

  const q = useQuery({
    queryKey: [PATHS.existencias, page, size, search, almacenId, bajo],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (search) params[SEARCH_PARAM] = search
      if (almacenId) params.almacenId = almacenId
      if (bajo) params.bajoMinimo = true
      return normalizePage((await http.get(PATHS.existencias, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 2 }}>Existencias</Typography>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap', alignItems: 'center' }}>
        <TextField size="small" placeholder="Buscar por código o descripción" value={search} sx={{ flex: '1 1 260px' }}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <AlmacenSelect value={almacenId} onChange={(v) => { setAlmacenId(v); setPage(0) }} width={220} />
        <FormControlLabel control={<Switch checked={bajo} onChange={(e) => { setBajo(e.target.checked); setPage(0) }} />} label="Solo bajo mínimo" />
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={search || almacenId || bajo ? 'Ningún resultado con esos filtros.' : 'Aún no hay existencias. Se generan al recibir mercadería o registrar entradas.'}
        columns={[
          { header: 'Producto', render: (r) => productoLabel(r.producto) },
          { header: 'Almacén', render: (r) => txt(r.almacen) },
          { header: 'Físico', align: 'right', render: (r) => num(r.cantidadFisica) },
          { header: 'Reservado', align: 'right', render: (r) => num(r.cantidadReservada) },
          { header: 'Disponible', align: 'right', render: (r) => num(r.cantidadDisponible ?? r.cantidadFisica) },
          { header: 'Mínimo', align: 'right', render: (r) => num(r.stockMinimo) },
          { header: 'Nivel', render: (r) => <EstadoChip estado={nivelStock(r)} /> },
        ]}
        actions={(r) => (
          <Tooltip title="Ver kardex">
            <IconButton size="small" aria-label="Ver kardex"
              onClick={() => navigate(`/inventario/movimientos?productoId=${r.producto?.id ?? ''}&almacenId=${r.almacen?.id ?? ''}`)}>
              <HistoryIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}
      />
    </Box>
  )
}
