import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import {
  Box, Button, IconButton, MenuItem, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination,
  TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, type Row } from '../../../shared/api'
import { ESTADOS_RECEPCION, EstadoChip, estadoLabel } from '../../../shared/EstadoChip'
import { fecha, txt } from '../../../shared/format'
import { useAuth } from '../../auth/AuthContext'
import RecepcionDetalleDialog from './RecepcionDetalleDialog'
import RecepcionFormDialog from './RecepcionFormDialog'

export default function RecepcionesPage() {
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [estado, setEstado] = useState('')
  const [creating, setCreating] = useState(false)
  const [viewing, setViewing] = useState<Row['id'] | null>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [PATHS.recepciones, page, size, estado],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (estado) params.estado = estado
      return normalizePage((await http.get(PATHS.recepciones, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Recepción de bienes</Typography>
        {can(PERM.recepcionWrite) && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Nueva recepción</Button>}
      </Box>

      <Paper elevation={0} sx={{ p: 2, mb: 2 }}>
        <TextField select size="small" label="Estado" value={estado} sx={{ width: 190 }} onChange={(e) => { setEstado(e.target.value); setPage(0) }}>
          <MenuItem value="">Todos</MenuItem>
          {ESTADOS_RECEPCION.map((e) => <MenuItem key={e} value={e}>{estadoLabel(e)}</MenuItem>)}
        </TextField>
      </Paper>

      <Paper elevation={0}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                {['Número', 'Orden de compra', 'Proveedor', 'Fecha', 'Estado'].map((h) => <TableCell key={h} sx={{ fontWeight: 600 }}>{h}</TableCell>)}
                <TableCell align="right" sx={{ fontWeight: 600 }}>Acciones</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading && <TableRow><TableCell colSpan={6}>Cargando…</TableCell></TableRow>}
              {isError && <TableRow><TableCell colSpan={6} sx={{ color: 'error.main' }}>{errorMessage(error)}</TableCell></TableRow>}
              {!isLoading && !isError && data?.rows.length === 0 && (
                <TableRow><TableCell colSpan={6} sx={{ py: 4, textAlign: 'center' }}>
                  {estado ? 'Ningún resultado con ese estado.' : 'Aún no hay recepciones. Registra la primera contra una orden aprobada.'}
                </TableCell></TableRow>
              )}
              {data?.rows.map((r) => (
                <TableRow key={r.id} hover>
                  <TableCell>{txt(r.numero)}</TableCell>
                  <TableCell>{txt(r.ordenCompra?.numero ?? r.ordenCompraNumero)}</TableCell>
                  <TableCell>{txt(r.proveedor ?? r.ordenCompra?.proveedor ?? r.proveedorNombre)}</TableCell>
                  <TableCell>{fecha(r.fecha)}</TableCell>
                  <TableCell><EstadoChip estado={r.estado} /></TableCell>
                  <TableCell align="right">
                    <Tooltip title="Ver detalle"><IconButton size="small" aria-label="Ver detalle" onClick={() => setViewing(r.id)}><VisibilityIcon fontSize="small" /></IconButton></Tooltip>
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={data?.total ?? 0} page={page} rowsPerPage={size} rowsPerPageOptions={[10, 25, 50]}
          onPageChange={(_e, p) => setPage(p)} onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}
          labelRowsPerPage="Filas por página" labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      </Paper>

      {creating && <RecepcionFormDialog onClose={() => setCreating(false)} />}
      {viewing !== null && <RecepcionDetalleDialog id={viewing} onClose={() => setViewing(null)} />}
    </Box>
  )
}
