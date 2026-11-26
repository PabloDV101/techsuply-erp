import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { useLookup } from '../../../shared/api'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, num, productoLabel } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { ProductoSelect } from '../../../shared/selects'
import { useAuth } from '../../auth/AuthContext'

export default function ConteoDetalleDialog({ id, onClose }: { id: number | string; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [productoId, setProductoId] = useState('')
  const [cantidadContada, setCantidadContada] = useState('')
  const [confirmingClose, setConfirmingClose] = useState(false)
  const { data: c, isLoading, error } = useQuery({
    queryKey: ['conteo', id],
    queryFn: async () => (await http.get(`${PATHS.conteos}/${id}`)).data,
  })
  const products = useLookup(PATHS.productos)
  const warehouses = useLookup(PATHS.almacenes)
  const productMap = new Map((products.data ?? []).map((p) => [String(p.id), p]))
  const warehouse = (warehouses.data ?? []).find((w) => String(w.id) === String(c?.almacenId))
  const lines: any[] = c?.detalles ?? []
  const validCount = /^(?:\d{1,11})(?:\.\d{1,3})?$/.test(cantidadContada)
  const editable = ['PROGRAMADO', 'EN_CAPTURA'].includes(c?.estado) && can(PERM.inventarioContar)
  const counted = lines.filter((l) => l.cantidadContada != null).length

  const refresh = () => {
    void qc.invalidateQueries({ queryKey: [PATHS.conteos] })
    void qc.invalidateQueries({ queryKey: ['conteo', id] })
    void qc.invalidateQueries({ queryKey: [PATHS.existencias] })
    void qc.invalidateQueries({ queryKey: [PATHS.movimientos] })
    void qc.invalidateQueries({ queryKey: [PATHS.alertas] })
    void qc.invalidateQueries({ queryKey: [PATHS.ajustes] })
  }
  const capture = useMutation({
    mutationFn: () => http.put(`${PATHS.conteos}/${id}/lines/${productoId}`, { cantidadContada: Number(cantidadContada) }),
    onSuccess: () => { refresh(); notify('Cantidad capturada con la referencia vigente del sistema.'); setCantidadContada('') },
    onError: (e) => notify(errorMessage(e), 'error'),
  })
  const closeCount = useMutation({
    mutationFn: () => http.post(`${PATHS.conteos}/${id}/close`),
    onSuccess: () => { refresh(); notify('Conteo cerrado. No se modificó el stock.'); setConfirmingClose(false) },
    onError: (e) => { notify(errorMessage(e), 'error'); setConfirmingClose(false) },
  })

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Conteo físico {c?.folio ?? ''}</DialogTitle>
      <DialogContent>
        {isLoading && <Typography>Cargando…</Typography>}
        {error && <Typography color="error">{errorMessage(error)}</Typography>}
        {c && <>
          <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(3, 1fr)' }, gap: 2, mb: 2 }}>
            <Box><Typography variant="caption" color="text.secondary">Almacén</Typography><Typography>{warehouse ? `${warehouse.codigo} — ${warehouse.nombre}` : `ID ${c.almacenId}`}</Typography></Box>
            <Box><Typography variant="caption" color="text.secondary">Fecha programada</Typography><Typography>{fecha(c.fechaProgramada)}</Typography></Box>
            <Box><Typography variant="caption" color="text.secondary">Estado</Typography><EstadoChip estado={c.estado} /></Box>
          </Box>
          <Typography variant="body2" sx={{ mb: 1 }}>{counted} productos capturados · {lines.filter((l) => Number(l.diferencia ?? 0) !== 0).length} con diferencia</Typography>
          {editable && <Alert severity="info" sx={{ mb: 2 }}>
            Captura o recaptura cada producto. La API vuelve a leer la existencia al guardar; si recibes un conflicto por cambios concurrentes, verifica físicamente el producto y vuelve a capturarlo. Cerrar solo congela el conteo.
          </Alert>}
          {editable && <Box sx={{ display: 'flex', gap: 1, alignItems: 'flex-start', flexWrap: 'wrap', mb: 2 }}>
            <ProductoSelect value={productoId} onChange={setProductoId} width={320} />
            <TextField label="Cantidad contada" type="number" size="small" value={cantidadContada} onChange={(e) => setCantidadContada(e.target.value)}
              slotProps={{ htmlInput: { min: 0, step: '0.001' } }} />
            <Button variant="contained" disabled={!productoId || !validCount || capture.isPending}
              onClick={() => capture.mutate()}>{capture.isPending ? 'Guardando…' : 'Capturar producto'}</Button>
          </Box>}
          <Box sx={{ overflowX: 'auto' }}><Table size="small">
            <TableHead><TableRow><TableCell>Producto</TableCell><TableCell align="right">Sistema al capturar</TableCell><TableCell align="right">Contado</TableCell><TableCell align="right">Diferencia</TableCell><TableCell>Referencia</TableCell></TableRow></TableHead>
            <TableBody>
              {lines.length === 0 && <TableRow><TableCell colSpan={5} sx={{ textAlign: 'center', py: 3 }}>Aún no hay productos capturados.</TableCell></TableRow>}
              {lines.map((l) => <TableRow key={l.id}>
                <TableCell>{productoLabel(productMap.get(String(l.productoId)) ?? { id: l.productoId })}</TableCell>
                <TableCell align="right">{num(l.cantidadSistema)}</TableCell>
                <TableCell align="right">{l.cantidadContada == null ? '—' : num(l.cantidadContada)}</TableCell>
                <TableCell align="right">{l.diferencia == null ? '—' : `${Number(l.diferencia) > 0 ? '+' : ''}${num(l.diferencia)}`}</TableCell>
                <TableCell>{l.existenciaPrevia ? `Versión ${l.versionExistencia}` : 'Sin existencia previa'}</TableCell>
              </TableRow>)}
            </TableBody>
          </Table></Box>
        </>}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cerrar</Button>
        {editable && lines.length > 0 && <Button variant="contained" onClick={() => setConfirmingClose(true)} disabled={closeCount.isPending}>
          Cerrar conteo
        </Button>}
      </DialogActions>
      <ConfirmDialog open={confirmingClose} title="Cerrar conteo" text="Se congelarán las líneas capturadas. Esta acción no ajusta existencias; las diferencias deberán tramitarse mediante una solicitud de ajuste aprobable." confirmLabel="Cerrar conteo"
        onConfirm={() => closeCount.mutate()} onClose={() => setConfirmingClose(false)} />
    </Dialog>
  )
}
