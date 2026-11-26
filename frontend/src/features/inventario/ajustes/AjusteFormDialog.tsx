import { useMemo, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { normalizePage } from '../../../shared/api'
import { num, productoLabel } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useLookup } from '../../../shared/api'

export default function AjusteFormDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const [countId, setCountId] = useState('')
  const [countsPage, setCountsPage] = useState(0)
  const [motivo, setMotivo] = useState('')
  const [costs, setCosts] = useState<Record<string, string>>({})
  const idempotency = useRef<{ fingerprint: string; key: string } | null>(null)
  const countsQuery = useQuery({
    queryKey: [PATHS.conteos, 'closed-for-adjustment', countsPage],
    queryFn: async () => normalizePage((await http.get(PATHS.conteos, { params: { page: countsPage, size: 100, estado: 'CERRADO' } })).data),
  })
  const products = useLookup(PATHS.productos)
  const productMap = new Map((products.data ?? []).map((p) => [String(p.id), p]))
  const counts: any[] = countsQuery.data?.rows ?? []
  const countPages = Math.ceil((countsQuery.data?.total ?? 0) / 100)
  const selected = counts.find((c) => String(c.id) === countId)
  const lines = useMemo(() => (selected?.detalles ?? []).filter((line: any) => Number(line.diferencia) !== 0), [selected])
  const validCost = (value: string) => /^\d{1,12}(?:\.\d{1,4})?$/.test(value)

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => http.post(PATHS.ajustes, body),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: [PATHS.ajustes] })
      notify('Solicitud registrada. El stock no cambia hasta que otra persona la apruebe y se aplique.')
      onClose()
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })
  const create = () => {
    if (!selected || !motivo.trim()) return
    const details = lines.map((line: any) => {
      const positive = Number(line.diferencia) > 0
      return {
        productoId: line.productoId,
        tipo: positive ? 'POSITIVO' : 'NEGATIVO',
        cantidad: Math.abs(Number(line.diferencia)),
        ...(positive ? { costoUnitario: Number(costs[String(line.id)]) } : {}),
        detalleConteoId: line.id,
      }
    })
    if (details.some((d: any, index: number) => d.tipo === 'POSITIVO' && (!Number.isFinite(d.costoUnitario) || !validCost(costs[String(lines[index].id)])))) {
      notify('Indica el costo unitario para cada diferencia positiva.', 'error')
      return
    }
    const body = { almacenId: selected.almacenId, motivo: motivo.trim(), claveIdempotencia: '', detalles: details }
    const fingerprint = JSON.stringify({ ...body, claveIdempotencia: undefined })
    if (!idempotency.current || idempotency.current.fingerprint !== fingerprint) {
      idempotency.current = { fingerprint, key: crypto.randomUUID() }
    }
    body.claveIdempotencia = idempotency.current.key
    save.mutate(body)
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Solicitar ajuste desde un conteo cerrado</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'grid', gap: 2, pt: 1 }}>
          <Alert severity="info">Solo se incluyen diferencias de un conteo cerrado. El ajuste requiere aprobación de otro usuario y no cambia existencias hasta aplicarse.</Alert>
          <TextField select label="Conteo cerrado" size="small" value={countId} onChange={(e) => { setCountId(e.target.value); setCosts({}) }} disabled={countsQuery.isLoading}>
            <MenuItem value="">Selecciona un conteo</MenuItem>
            {counts.map((c) => <MenuItem key={c.id} value={String(c.id)}>{c.folio} · almacén {c.almacenId} · {c.fechaProgramada}</MenuItem>)}
          </TextField>
          {countPages > 1 && <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
            <Button size="small" disabled={countsPage === 0 || !!countId} onClick={() => setCountsPage((page) => page - 1)}>Página anterior</Button>
            <Typography variant="caption">Página {countsPage + 1} de {countPages} · 100 por página</Typography>
            <Button size="small" disabled={countsPage + 1 >= countPages || !!countId} onClick={() => setCountsPage((page) => page + 1)}>Página siguiente</Button>
          </Box>}
          {countsQuery.error && <Alert severity="error">{errorMessage(countsQuery.error)}</Alert>}
          {selected && lines.length === 0 && <Alert severity="success">El conteo no tiene diferencias para ajustar.</Alert>}
          {lines.map((line: any) => <Box key={line.id} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', sm: '2fr 1fr 1.4fr' }, gap: 1, alignItems: 'center' }}>
            <Typography>{productoLabel(productMap.get(String(line.productoId)) ?? { id: line.productoId })}</Typography>
            <Typography color={Number(line.diferencia) > 0 ? 'success.main' : 'error.main'}>
              {Number(line.diferencia) > 0 ? '+' : '−'}{num(Math.abs(Number(line.diferencia)))}
            </Typography>
            {Number(line.diferencia) > 0 ? <TextField size="small" label="Costo unitario" type="number" value={costs[String(line.id)] ?? ''}
              onChange={(e) => setCosts((v) => ({ ...v, [String(line.id)]: e.target.value }))} slotProps={{ htmlInput: { min: 0, step: '0.0001', max: 999999999999.9999 } }} />
              : <Typography variant="body2" color="text.secondary">Costo de salida vigente al aplicar</Typography>}
          </Box>)}
          <TextField required label="Motivo de la solicitud" multiline minRows={2} value={motivo} onChange={(e) => setMotivo(e.target.value)} slotProps={{ htmlInput: { maxLength: 1000 } }} />
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancelar</Button>
        <Button variant="contained" disabled={!selected || lines.length === 0 || !motivo.trim() || save.isPending || lines.some((l: any) => Number(l.diferencia) > 0 && !validCost(costs[String(l.id)] ?? ''))} onClick={create}>
          {save.isPending ? 'Enviando…' : 'Enviar a aprobación'}
        </Button>
      </DialogActions>
    </Dialog>
  )
}
