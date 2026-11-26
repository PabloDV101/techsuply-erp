import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import {
  Box, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
  TextField, Typography,
} from '@mui/material'
import { errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { normalizePage, useLookup } from '../../../shared/api'
import { EstadoChip } from '../../../shared/EstadoChip'
import { daysAgo, today } from '../../../shared/format'
import { AlmacenSelect } from '../../../shared/selects'
import ExportReportButton from '../../reporteria/ExportReportButton'
import { exactDecimal, getReport } from '../../reporteria/reportingApi'

export default function RotacionPage() {
  const [desde, setDesde] = useState(daysAgo(90))
  const [hasta, setHasta] = useState(today())
  const [almacenId, setAlmacenId] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const validRange = !!desde && !!hasta && desde <= hasta && hasta <= today()
  const products = useLookup(PATHS.productos)
  const productMap = new Map((products.data ?? []).map((p) => [String(p.id), p]))

  const q = useQuery({
    queryKey: [PATHS.rotacion, desde, hasta, almacenId, page, size],
    enabled: validRange,
    placeholderData: keepPreviousData,
    queryFn: async () => {
      const params: Record<string, string | number> = { from: desde, to: hasta, page, size, sort: 'productId', direction: 'asc' }
      if (almacenId) params.warehouseId = almacenId
      return normalizePage(await getReport(PATHS.rotacion, params))
    },
  })
  const rows = q.data?.rows ?? []
  const available = rows.filter((r) => r.availability === 'DISPONIBLE' && r.value != null)
  const unavailable = rows.filter((r) => r.availability === 'NO_DISPONIBLE').length

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Rotación de inventario</Typography>
        <ExportReportButton report="inventory/turnover" params={{ from: desde, to: hasta, page, size, sort: 'productId', direction: 'asc', ...(almacenId ? { warehouseId: almacenId } : {}) }} filename="rotacion-inventario" disabled={!rows.length || q.isLoading || !!q.error}/>
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField size="small" type="date" label="Desde" value={desde} slotProps={{ inputLabel: { shrink: true } }} onChange={(e) => { setDesde(e.target.value); setPage(0) }} />
        <TextField size="small" type="date" label="Hasta" value={hasta} slotProps={{ inputLabel: { shrink: true } }}
          error={!validRange} helperText={!validRange ? 'El rango debe ser válido y no futuro.' : undefined} onChange={(e) => { setHasta(e.target.value); setPage(0) }} />
        <AlmacenSelect value={almacenId} onChange={(v) => { setAlmacenId(v); setPage(0) }} width={220} />
      </Paper>
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, mb: 2 }}>
        {[
          ['Productos en página', String(rows.length)],
          ['Disponibles en página', String(available.length)],
          ['No disponibles en página', String(unavailable)],
        ].map(([label, value]) => <Paper key={label} elevation={0} sx={{ p: 2 }}>
          <Typography color="text.secondary" variant="body2">{label}</Typography>
          <Typography variant="h4" color="primary" sx={{ fontWeight: 700 }}>{q.isLoading ? '…' : value}</Typography>
        </Paper>)}
      </Box>
      <Paper elevation={0}>
        <TableContainer><Table size="small">
          <TableHead><TableRow>
            <TableCell sx={{ fontWeight: 600 }}>Producto</TableCell><TableCell align="right" sx={{ fontWeight: 600 }}>Salidas (unidades)</TableCell>
            <TableCell align="right" sx={{ fontWeight: 600 }}>Existencia física media diaria</TableCell><TableCell align="right" sx={{ fontWeight: 600 }}>Rotación</TableCell>
            <TableCell sx={{ fontWeight: 600 }}>Disponibilidad</TableCell><TableCell sx={{ fontWeight: 600 }}>Detalle</TableCell>
          </TableRow></TableHead>
          <TableBody>
            {q.isLoading && <TableRow><TableCell colSpan={6}>Calculando…</TableCell></TableRow>}
            {q.error && <TableRow><TableCell colSpan={6} sx={{ color: 'error.main' }}>{errorMessage(q.error)}</TableCell></TableRow>}
            {!q.isLoading && !q.error && rows.length === 0 && <TableRow><TableCell colSpan={6} sx={{ py: 4, textAlign: 'center' }}>No hay productos con datos en el período.</TableCell></TableRow>}
            {rows.map((r) => {
              const product = productMap.get(String(r.productId))
              return <TableRow key={r.productId} hover>
              <TableCell>{product ? `${product.codigo} — ${product.descripcion}` : `Producto ${r.productId}`}</TableCell>
              <TableCell align="right">{exactDecimal(r.numerator)}</TableCell>
              <TableCell align="right">{exactDecimal(r.denominator)}</TableCell>
              <TableCell align="right">{exactDecimal(r.value, 2)}</TableCell>
              <TableCell><EstadoChip estado={r.availability} /></TableCell>
              <TableCell>{r.unavailabilityReason ?? 'Kardex conciliado'}</TableCell>
            </TableRow>
            })}
          </TableBody>
        </Table></TableContainer>
        <TablePagination component="div" count={q.data?.total ?? 0} page={page} rowsPerPage={size} rowsPerPageOptions={[10, 25, 50]}
          onPageChange={(_e, p) => setPage(p)} onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}
          labelRowsPerPage="Filas por página" labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      </Paper>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        Fuente: salidas confirmadas en unidades divididas entre existencia física media diaria del período. Cuando la cobertura del kardex no es fiable o la existencia media es cero, la API devuelve NO_DISPONIBLE sin inferir un valor.
      </Typography>
    </Box>
  )
}
