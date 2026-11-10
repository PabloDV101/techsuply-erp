import { useMemo, useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import {
  Box, Button, LinearProgress, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow,
  TextField, Typography,
} from '@mui/material'
import DownloadIcon from '@mui/icons-material/FileDownloadOutlined'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { normalizePage } from '../../../shared/api'
import { EstadoChip } from '../../../shared/EstadoChip'
import { daysAgo, num, productoLabel, today } from '../../../shared/format'
import { AlmacenSelect } from '../../../shared/selects'

interface Item {
  key: string
  producto: string
  salidas: number
  stockPromedio: number
  rotacion: number
  dias: number | null
  clase: string
}

const clasificar = (rot: number) => (rot >= 4 ? 'ALTA' : rot >= 1 ? 'MEDIA' : rot > 0 ? 'BAJA' : 'SIN_MOVIMIENTO')

function csv(items: Item[]) {
  const head = ['Producto', 'Salidas', 'Stock promedio', 'Rotación', 'Días de inventario', 'Clasificación']
  const lines = items.map((i) => [i.producto, i.salidas, i.stockPromedio, i.rotacion.toFixed(2), i.dias ?? '', i.clase])
  const body = [head, ...lines].map((r) => r.map((c) => `"${String(c).replace(/"/g, '""')}"`).join(',')).join('\n')
  const url = URL.createObjectURL(new Blob(['\uFEFF' + body], { type: 'text/csv;charset=utf-8' }))
  const a = document.createElement('a')
  a.href = url
  a.download = 'rotacion-inventario.csv'
  a.click()
  URL.revokeObjectURL(url)
}

export default function RotacionPage() {
  const [desde, setDesde] = useState(daysAgo(90))
  const [hasta, setHasta] = useState(today())
  const [almacenId, setAlmacenId] = useState('')
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const validRange = !!desde && !!hasta && desde <= hasta

  const q = useQuery({
    queryKey: [PATHS.rotacion, desde, hasta, almacenId],
    enabled: validRange,
    queryFn: async () => {
      const params: Record<string, unknown> = { desde, hasta }
      if (almacenId) params.almacenId = almacenId
      return normalizePage((await http.get(PATHS.rotacion, { params })).data).rows
    },
  })

  const items: Item[] = useMemo(() => {
    const periodo = Math.max(1, Math.round((new Date(hasta).getTime() - new Date(desde).getTime()) / 86_400_000) + 1)
    return (q.data ?? [])
      .map((r, i) => {
        const salidas = Number(r.salidas ?? r.cantidadSalidas ?? 0)
        const prom = Number(r.stockPromedio ?? 0)
        const rot = Number(r.rotacion ?? (prom > 0 ? salidas / prom : 0))
        return {
          key: String(r.id ?? r.producto?.id ?? i),
          producto: productoLabel(r.producto ?? { codigo: r.codigo, descripcion: r.descripcion }),
          salidas,
          stockPromedio: prom,
          rotacion: rot,
          dias: r.diasInventario != null ? Number(r.diasInventario) : rot > 0 ? periodo / rot : null,
          clase: String(r.clasificacion ?? clasificar(rot)),
        }
      })
      .sort((a, b) => b.rotacion - a.rotacion)
  }, [q.data, desde, hasta])

  const max = Math.max(1, ...items.map((i) => i.rotacion))
  const sin = items.filter((i) => i.clase === 'SIN_MOVIMIENTO').length
  const promedio = items.length ? items.reduce((s, i) => s + i.rotacion, 0) / items.length : 0
  const rows = items.slice(page * size, page * size + size)

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Rotación de inventario</Typography>
        <Button startIcon={<DownloadIcon />} disabled={items.length === 0} onClick={() => csv(items)}>Exportar CSV</Button>
      </Box>

      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField size="small" type="date" label="Desde" value={desde} slotProps={{ inputLabel: { shrink: true } }} onChange={(e) => { setDesde(e.target.value); setPage(0) }} />
        <TextField size="small" type="date" label="Hasta" value={hasta} slotProps={{ inputLabel: { shrink: true } }}
          error={!validRange} helperText={!validRange ? 'Rango no válido' : undefined} onChange={(e) => { setHasta(e.target.value); setPage(0) }} />
        <AlmacenSelect value={almacenId} onChange={(v) => { setAlmacenId(v); setPage(0) }} width={220} />
      </Paper>

      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: '1fr', sm: 'repeat(3, 1fr)' }, mb: 2 }}>
        {[['Productos analizados', String(items.length)], ['Rotación promedio', num(promedio.toFixed(2))], ['Sin movimiento', String(sin)]].map(([label, value]) => (
          <Paper key={label} elevation={0} sx={{ p: 2 }}>
            <Typography color="text.secondary" variant="body2">{label}</Typography>
            <Typography variant="h4" color="primary" sx={{ fontWeight: 700 }}>{q.isLoading ? '…' : value}</Typography>
          </Paper>
        ))}
      </Box>

      <Paper elevation={0}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell sx={{ fontWeight: 600 }}>Producto</TableCell>
                <TableCell align="right" sx={{ fontWeight: 600 }}>Salidas</TableCell>
                <TableCell align="right" sx={{ fontWeight: 600 }}>Stock promedio</TableCell>
                <TableCell sx={{ fontWeight: 600, minWidth: 160 }}>Rotación</TableCell>
                <TableCell align="right" sx={{ fontWeight: 600 }}>Días de inventario</TableCell>
                <TableCell sx={{ fontWeight: 600 }}>Clasificación</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {q.isLoading && <TableRow><TableCell colSpan={6}>Calculando…</TableCell></TableRow>}
              {q.error && <TableRow><TableCell colSpan={6} sx={{ color: 'error.main' }}>{errorMessage(q.error)}</TableCell></TableRow>}
              {!q.isLoading && !q.error && items.length === 0 && (
                <TableRow><TableCell colSpan={6} sx={{ py: 4, textAlign: 'center' }}>No hay movimientos de salida en el período elegido.</TableCell></TableRow>
              )}
              {rows.map((i) => (
                <TableRow key={i.key} hover>
                  <TableCell>{i.producto}</TableCell>
                  <TableCell align="right">{num(i.salidas)}</TableCell>
                  <TableCell align="right">{num(i.stockPromedio)}</TableCell>
                  <TableCell>
                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                      <LinearProgress variant="determinate" value={(i.rotacion / max) * 100} sx={{ flexGrow: 1, height: 6, borderRadius: 3 }} />
                      <Typography variant="body2" sx={{ minWidth: 36, textAlign: 'right' }}>{i.rotacion.toFixed(2)}</Typography>
                    </Box>
                  </TableCell>
                  <TableCell align="right">{i.dias == null ? '—' : num(Math.round(i.dias))}</TableCell>
                  <TableCell><EstadoChip estado={i.clase} /></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={items.length} page={page} rowsPerPage={size} rowsPerPageOptions={[10, 25, 50]}
          onPageChange={(_e, p) => setPage(p)} onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}
          labelRowsPerPage="Filas por página" labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      </Paper>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
        Rotación = salidas del período ÷ stock promedio. Alta: 4 o más · Media: 1 a 4 · Baja: menos de 1.
      </Typography>
    </Box>
  )
}
