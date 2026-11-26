import { useRef, useState } from 'react'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Typography } from '@mui/material'
import { useNavigate } from 'react-router-dom'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { idValue } from '../../../shared/api'
import { useNotify } from '../../../shared/Notify'
import { AlmacenSelect } from '../../../shared/selects'

export default function PedidoConversionDialog({ quote, onClose }: { quote: { id: number | string; folio: string }; onClose: () => void }) {
  const qc = useQueryClient()
  const navigate = useNavigate()
  const notify = useNotify()
  const [almacenId, setAlmacenId] = useState('')
  const idempotency = useRef<{ fingerprint: string; key: string } | null>(null)
  const create = useMutation({
    mutationFn: (body: Record<string, unknown>) => http.post(PATHS.pedidos, body),
    onSuccess: (response) => {
      void qc.invalidateQueries({ queryKey: [PATHS.pedidos] })
      void qc.invalidateQueries({ queryKey: [PATHS.cotizaciones] })
      notify(`Pedido ${response.data?.folio ?? ''} creado en borrador. La modalidad CONTADO no acredita pago.`)
      onClose()
      navigate('/ventas/pedidos')
    },
    onError: (error) => notify(errorMessage(error), 'error'),
  })
  const submit = () => {
    if (!almacenId) return
    const payload = { cotizacionId: idValue(String(quote.id)), almacenId: idValue(almacenId), modalidad: 'CONTADO' }
    const fingerprint = JSON.stringify(payload)
    if (!idempotency.current || idempotency.current.fingerprint !== fingerprint) {
      idempotency.current = { fingerprint, key: crypto.randomUUID() }
    }
    create.mutate({ ...payload, claveIdempotencia: idempotency.current.key })
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>Convertir cotización {quote.folio} en pedido</DialogTitle>
      <DialogContent>
        <Box sx={{ display: 'grid', gap: 2, pt: 1 }}>
          <Typography variant="body2">Se conservarán cliente, productos, precios, descuentos e impuestos aceptados.</Typography>
          <AlmacenSelect required value={almacenId} onChange={setAlmacenId} label="Almacén de despacho" />
          <Alert severity="info">La API crea el pedido en modalidad CONTADO. Esto no registra pago ni cobro; confirmar el pedido reservará existencias.</Alert>
        </Box>
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cancelar</Button>
        <Button variant="contained" disabled={!almacenId || create.isPending} onClick={submit}>{create.isPending ? 'Convirtiendo…' : 'Crear pedido'}</Button>
      </DialogActions>
    </Dialog>
  )
}
