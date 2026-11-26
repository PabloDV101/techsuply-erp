import { useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import {
  Box, Button, IconButton, Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination,
  TableRow, TextField, Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import VisibilityIcon from '@mui/icons-material/VisibilityOutlined'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { normalizePage, type Row } from '../../../shared/api'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fechaHora } from '../../../shared/format'
import { useAuth } from '../../auth/AuthContext'
import RecepcionDetalleDialog from './RecepcionDetalleDialog'
import RecepcionFormDialog from './RecepcionFormDialog'

export default function RecepcionesPage() {
  const { can } = useAuth()
  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [orderIdFilter, setOrderIdFilter] = useState('')
  const [creating, setCreating] = useState(false)
  const [viewing, setViewing] = useState<Row['id'] | null>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [PATHS.recepciones, page, size, orderIdFilter],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size, sort: 'fecha', direction: 'desc' }
      if (orderIdFilter) params.ordenCompraId = Number(orderIdFilter)
      return normalizePage((await http.get(PATHS.recepciones, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>Recepciones de compra</Typography>
        {can(PERM.recepcionWrite) && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setCreating(true)}>Registrar recepción</Button>}
      </Box>
      <Paper elevation={0} sx={{ p: 2, mb: 2 }}>
        <TextField type="number" size="small" label="ID de orden de compra" value={orderIdFilter} sx={{ width: 240 }}
          slotProps={{ htmlInput: { min: 1, step: 1 } }} onChange={(event) => { setOrderIdFilter(event.target.value); setPage(0) }} />
      </Paper>
      <Paper elevation={0}>
        <TableContainer>
          <Table size="small">
            <TableHead><TableRow>
              {['Folio', 'ID orden', 'ID almacén', 'Fecha', 'Estado'].map((heading) => <TableCell key={heading} sx={{ fontWeight: 600 }}>{heading}</TableCell>)}
              <TableCell align="right" sx={{ fontWeight: 600 }}>Acciones</TableCell>
            </TableRow></TableHead>
            <TableBody>
              {isLoading && <TableRow><TableCell colSpan={6}>Cargando…</TableCell></TableRow>}
              {isError && <TableRow><TableCell colSpan={6} sx={{ color: 'error.main' }}>{errorMessage(error)}</TableCell></TableRow>}
              {!isLoading && !isError && data?.rows.length === 0 && <TableRow><TableCell colSpan={6} sx={{ py: 4, textAlign: 'center' }}>
                {orderIdFilter ? 'No hay recepciones para esa orden.' : 'Aún no hay recepciones de compra.'}
              </TableCell></TableRow>}
              {data?.rows.map((receipt) => (
                <TableRow key={receipt.id} hover>
                  <TableCell>{receipt.folio}</TableCell>
                  <TableCell>{receipt.ordenCompraId}</TableCell>
                  <TableCell>{receipt.almacenId}</TableCell>
                  <TableCell>{fechaHora(receipt.fecha)}</TableCell>
                  <TableCell><EstadoChip estado={receipt.estado} /></TableCell>
                  <TableCell align="right"><Tooltip title="Ver detalle"><IconButton size="small" aria-label="Ver detalle" onClick={() => setViewing(receipt.id)}><VisibilityIcon fontSize="small" /></IconButton></Tooltip></TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination component="div" count={data?.total ?? 0} page={page} rowsPerPage={size} rowsPerPageOptions={[10, 25, 50]}
          onPageChange={(_event, next) => setPage(next)} onRowsPerPageChange={(event) => { setSize(Number(event.target.value)); setPage(0) }}
          labelRowsPerPage="Filas por página" labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`} />
      </Paper>
      {creating && <RecepcionFormDialog onCreated={(id) => { setCreating(false); setViewing(id) }} onClose={() => setCreating(false)} />}
      {viewing !== null && <RecepcionDetalleDialog id={viewing} onClose={() => setViewing(null)} />}
    </Box>
  )
}
