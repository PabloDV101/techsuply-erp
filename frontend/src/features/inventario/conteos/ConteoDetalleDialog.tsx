import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, LinearProgress, Switch, Table,
  TableBody, TableCell, TableHead, TableRow, TextField, Typography,
} from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { ConfirmDialog } from '../../../shared/ConfirmDialog'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, num, productoLabel, txt } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'

export default function ConteoDetalleDialog({ id, onClose }: { id: number | string; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [counts, setCounts] = useState<Record<string, string>>({})
  const [onlyDiff, setOnlyDiff] = useState(false)
  const [confirming, setConfirming] = useState(false)
  const idempotencyKey = useRef(crypto.randomUUID())

  const { data: c, isLoading, error } = useQuery({
    queryKey: ['conteo', id],
    queryFn: async () => (await http.get(`${PATHS.conteos}/${id}`)).data,
  })
  const lines: any[] = c?.lineas ?? c?.detalles ?? []

  useEffect(() => {
    setCounts(Object.fromEntries(lines.map((l) => [String(l.id), l.cantidadContada == null ? '' : String(l.cantidadContada)])))
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [c])

  const editable = c?.estado === 'EN_CONTEO' && can(PERM.inventarioContar)
  const diffOf = (l: any) => {
    const v = counts[String(l.id)]
    return v === undefined || v === '' ? null : Number(v) - Number(l.cantidadSistema)
  }
  const counted = lines.filter((l) => counts[String(l.id)] !== '' && counts[String(l.id)] !== undefined).length
  const withDiff = lines.filter((l) => (diffOf(l) ?? 0) !== 0).length
  const visible = onlyDiff ? lines.filter((l) => (diffOf(l) ?? 0) !== 0) : lines

  const refresh = () => {
    for (const k of [[PATHS.conteos], ['conteo', id], [PATHS.existencias], [PATHS.movimientos], [PATHS.alertas], [PATHS.ajustes]]) qc.invalidateQueries({ queryKey: k })
  }

  const saveCounts = useMutation({
    mutationFn: () =>
      http.put(`${PATHS.conteos}/${id}/lineas`, {
        lineas: lines.filter((l) => counts[String(l.id)] !== '').map((l) => ({ id: l.id, cantidadContada: Number(counts[String(l.id)]) })),
      }),
    onSuccess: () => { refresh(); notify('Conteo guardado.') },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  const apply = useMutation({
    mutationFn: () => http.post(`${PATHS.conteos}/${id}/aplicar`, null, { headers: { 'Idempotency-Key': idempotencyKey.current } }),
    onSuccess: () => { refresh(); notify('Conteo aplicado. Se generaron los ajustes de inventario.'); setConfirming(false) },
    onError: (e) => { notify(errorMessage(e), 'error'); setConfirming(false) },
  })

  // Para aplicar primero se guardan las cantidades y luego se confirma.
  const applyAfterSave = async () => {
    try {
      await saveCounts.mutateAsync()
      apply.mutate()
    } catch { setConfirming(false) }
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Conteo físico {c?.numero ?? ''}</DialogTitle>
      <DialogContent>
        {isLoading && <Typography>Cargando…</Typography>}
        {error && <Typography color="error">{errorMessage(error)}</Typography>}
        {c && (
          <>
            <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(3, 1fr)' }, gap: 2, mb: 2 }}>
              <Box><Typography variant="caption" color="text.secondary">Almacén</Typography><Typography>{txt(c.almacen)}</Typography></Box>
              <Box><Typography variant="caption" color="text.secondary">Fecha</Typography><Typography>{fecha(c.fecha)}</Typography></Box>
              <Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={c.estado} /></Box></Box>
            </Box>
            <Box sx={{ mb: 1 }}>
              <Typography variant="body2">{counted} de {lines.length} productos contados · {withDiff} con diferencia</Typography>
              <LinearProgress variant="determinate" value={lines.length ? (counted / lines.length) * 100 : 0} sx={{ mt: 0.5 }} />
            </Box>
            <FormControlLabel control={<Switch checked={onlyDiff} onChange={(e) => setOnlyDiff(e.target.checked)} />} label="Solo diferencias" />
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Producto</TableCell>
                    <TableCell align="right">Sistema</TableCell>
                    <TableCell>Contado</TableCell>
                    <TableCell align="right">Diferencia</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {visible.length === 0 && <TableRow><TableCell colSpan={4} sx={{ textAlign: 'center', py: 3 }}>{onlyDiff ? 'No hay diferencias.' : 'Este conteo no tiene productos.'}</TableCell></TableRow>}
                  {visible.map((l) => {
                    const d = diffOf(l)
                    return (
                      <TableRow key={l.id}>
                        <TableCell>{productoLabel(l.producto)}</TableCell>
                        <TableCell align="right">{num(l.cantidadSistema)}</TableCell>
                        <TableCell>
                          {editable ? (
                            <TextField size="small" type="number" sx={{ width: 110 }} value={counts[String(l.id)] ?? ''}
                              slotProps={{ htmlInput: { min: 0, step: 'any', 'aria-label': `Cantidad contada de ${l.producto?.codigo ?? ''}` } }}
                              onChange={(e) => setCounts((s) => ({ ...s, [String(l.id)]: e.target.value }))} />
                          ) : num(l.cantidadContada)}
                        </TableCell>
                        <TableCell align="right" sx={{ color: d ? (d > 0 ? 'success.main' : 'error.main') : undefined, fontWeight: d ? 600 : undefined }}>
                          {d === null ? '—' : d === 0 ? '0' : `${d > 0 ? '+' : '−'}${num(Math.abs(d))}`}
                        </TableCell>
                      </TableRow>
                    )
                  })}
                </TableBody>
              </Table>
            </Box>
            {editable && counted < lines.length && (
              <Alert severity="info" sx={{ mt: 2 }}>Para aplicar el conteo debes registrar la cantidad de todos los productos (puedes guardar y continuar después).</Alert>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cerrar</Button>
        {editable && (
          <>
            <Button onClick={() => saveCounts.mutate()} disabled={saveCounts.isPending}>{saveCounts.isPending ? 'Guardando…' : 'Guardar conteo'}</Button>
            <Button variant="contained" onClick={() => setConfirming(true)} disabled={counted < lines.length || lines.length === 0 || apply.isPending}>
              Aplicar ajustes
            </Button>
          </>
        )}
      </DialogActions>
      <ConfirmDialog
        open={confirming}
        title="Aplicar conteo"
        text={`Se generarán ajustes para ${withDiff} producto(s) con diferencia y el stock del sistema quedará igual a lo contado. No se puede deshacer.`}
        confirmLabel="Aplicar ajustes"
        onConfirm={applyAfterSave}
        onClose={() => setConfirming(false)}
      />
    </Dialog>
  )
}
