import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Box, IconButton, InputAdornment, MenuItem, Paper, TextField, Tooltip, Typography } from '@mui/material'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import SearchIcon from '@mui/icons-material/Search'
import { http } from '../../../api/http'
import { PATHS, SEARCH_PARAM } from '../../../config/contract'
import { normalizePage, type Row } from '../../../shared/api'
import DataTable from '../../../shared/DataTable'
import { ESTADOS_FACTURA, EstadoChip, estadoLabel } from '../../../shared/EstadoChip'
import { fecha, money, today, txt } from '../../../shared/format'
import { ClienteSelect } from '../../../shared/selects'
import FacturaDetalleDialog from './FacturaDetalleDialog'

export default function FacturasPage() {
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [search, setSearch] = useState('')
  const [estado, setEstado] = useState('')
  const [clienteId, setClienteId] = useState('')
  const [viewing, setViewing] = useState<Row['id'] | null>(null)

  const q = useQuery({
    queryKey: [PATHS.facturas, page, size, search, estado, clienteId],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (search) params[SEARCH_PARAM] = search
      if (estado) params.estado = estado
      if (clienteId) params.clienteId = clienteId
      return normalizePage((await http.get(PATHS.facturas, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  return (
    <Box>
      <Typography variant="h5" sx={{ mb: 0.5 }}>Facturas</Typography>
      <Typography color="text.secondary" sx={{ mb: 2 }}>Se emiten automáticamente al confirmar un despacho.</Typography>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField size="small" placeholder="Buscar por número" value={search} sx={{ flex: '1 1 220px' }}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }} />
        <ClienteSelect value={clienteId} onChange={(v) => { setClienteId(v); setPage(0) }} width={260} />
        <TextField select size="small" label="Estado" value={estado} sx={{ width: 190 }} onChange={(e) => { setEstado(e.target.value); setPage(0) }}>
          <MenuItem value="">Todos</MenuItem>
          {ESTADOS_FACTURA.map((e) => <MenuItem key={e} value={e}>{estadoLabel(e)}</MenuItem>)}
        </TextField>
      </Paper>
      <DataTable
        data={q.data} loading={q.isLoading} error={q.error} page={page} size={size}
        onPage={setPage} onSize={(s) => { setSize(s); setPage(0) }}
        emptyText={search || estado || clienteId ? 'Ninguna factura con esos filtros.' : 'Aún no hay facturas. Se generan al confirmar despachos.'}
        columns={[
          { header: 'Número', render: (r) => txt(r.numero) },
          { header: 'Cliente', render: (r) => txt(r.cliente ?? r.clienteNombre) },
          { header: 'Emisión', render: (r) => fecha(r.fechaEmision ?? r.fecha) },
          {
            header: 'Vencimiento',
            render: (r) => {
              const late = Number(r.saldo) > 0 && r.fechaVencimiento && String(r.fechaVencimiento).slice(0, 10) < today() && r.estado !== 'ANULADA'
              return <Typography variant="body2" color={late ? 'error' : undefined} sx={{ fontWeight: late ? 600 : undefined }}>{fecha(r.fechaVencimiento)}{late ? ' · vencida' : ''}</Typography>
            },
          },
          { header: 'Estado', render: (r) => <EstadoChip estado={r.estado} /> },
          { header: 'Total', align: 'right', render: (r) => money(r.total) },
          { header: 'Saldo', align: 'right', render: (r) => money(r.saldo) },
        ]}
        actions={(r) => (
          <Tooltip title="Ver factura"><IconButton size="small" aria-label="Ver factura" onClick={() => setViewing(r.id)}><VisibilityIcon fontSize="small" /></IconButton></Tooltip>
        )}
      />
      {viewing !== null && <FacturaDetalleDialog id={viewing} onClose={() => setViewing(null)} />}
    </Box>
  )
}
