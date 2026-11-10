import { useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Box, Button, MenuItem, Paper, TextField, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import { http } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { EstadoChip, estadoLabel, TIPOS_MOVIMIENTO } from '../../../shared/EstadoChip'
import { fechaHora, num, productoLabel, txt } from '../../../shared/format'
import { AlmacenSelect, ProductoSelect } from '../../../shared/selects'
import { useAuth } from '../../auth/AuthContext'
import MovimientoFormDialog from './MovimientoFormDialog'

export default function MovimientosPage() {
  const { can } = useAuth()
  const [params] = useSearchParams()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [productoId, setProductoId] = useState(params.get('productoId') ?? '')
  const [almacenId, setAlmacenId] = useState(params.get('almacenId') ?? '')
  const [tipo, setTipo] = useState('')
  const [desde, setDesde] = useState('')
  const [hasta, setHasta] = useState('')
  const [creating, setCreating] = useState(false)
  const set = (fn: (v: string) => void) => (v: string) => { fn(v); setPage(0) }

  const q = useQuery({
    queryKey: [PATHS.movimientos, page, size, productoId, almacenId, tipo, desde, hasta],
    queryFn: async () => {
      const p: Record<string, unknown> = { page, size }
      if (productoId) p.productoId = productoId
      if (almacenId) p.almacenId = almacenId
      if (tipo) p.tipo = tipo
      if (desde) p.desde = desde
      if (hasta) p.hasta = hasta
      return normalizePage((await http.get(PATHS.movimientos, { params: p })).data)
    },
    placeholderData: keepPreviousData,
  })

  const filtered = !!(productoId || almacenId || tipo || desde || hasta)

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Movimientos de inventario (kardex)</Typography>
        {can(PERM.inventarioMover) && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Registrar movimiento</Button>}
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <ProductoSelect value={productoId} onChange={set(setProductoId)} width={280} />
        <AlmacenSelect value={almacenId} onChange={set(setAlmacenId)} width={200} />
        <TextField select size="small" label="Tipo" value={tipo} sx={{ width: 170 }} onChange={(e) => set(setTipo)(e.target.value)}>
          <MenuItem value="">Todos</MenuItem>
          {TIPOS_MOVIMIENTO.map((t) => <MenuItem key={t} value={t}>{estadoLabel(t)}</MenuItem>)}
        </TextField>
        <TextField size="small" type="date" label="Desde" value={desde} slotProps={{ inputLabel: { shrink: true } }} onChange={(e) => set(setDesde)(e.target.value)} />
        <TextField size="small" type="date" label="Hasta" value={hasta} slotProps={{ inputLabel: { shrink: true } }} onChange={(e) => set(setHasta)(e.target.value)} />
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={filtered ? 'Ningún movimiento con esos filtros.' : 'Aún no hay movimientos registrados.'}
        columns={[
          { header: 'Fecha', render: (r) => fechaHora(r.fecha) },
          { header: 'Tipo', render: (r) => <EstadoChip estado={r.tipo} /> },
          { header: 'Producto', render: (r) => productoLabel(r.producto) },
          { header: 'Almacén', render: (r) => txt(r.almacen) },
          { header: 'Cantidad', align: 'right', render: (r) => num(r.cantidad) },
          { header: 'Saldo', align: 'right', render: (r) => num(r.saldo) },
          { header: 'Motivo / referencia', render: (r) => txt(r.motivo ?? r.referencia) },
          { header: 'Usuario', render: (r) => txt(r.usuario) },
        ]}
      />
      {creating && <MovimientoFormDialog onClose={() => setCreating(false)} />}
    </Box>
  )
}
