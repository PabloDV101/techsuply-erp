import { useRef, useState } from 'react'
import { useForm } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Table, TableBody, TableCell, TableHead,
  TableRow, TextField, Typography,
} from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { EstadoChip } from '../../../shared/EstadoChip'
import { fecha, money, num, productoLabel, today, txt } from '../../../shared/format'
import { MotivoDialog } from '../../../shared/MotivoDialog'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'

const METODOS = ['EFECTIVO', 'TRANSFERENCIA', 'CHEQUE', 'TARJETA']

function PagoDialog({ id, saldo, onClose, onDone }: { id: number | string; saldo: number; onClose: () => void; onDone: () => void }) {
  const notify = useNotify()
  // Una clave por apertura del diálogo: un doble clic o un reintento no cobra dos veces.
  const key = useRef(crypto.randomUUID())
  const { register, handleSubmit, formState: { errors } } = useForm<{ monto: string; fecha: string; metodo: string; referencia: string }>({
    defaultValues: { monto: String(saldo), fecha: today(), metodo: 'TRANSFERENCIA', referencia: '' },
  })
  const save = useMutation({
    mutationFn: (v: { monto: string; fecha: string; metodo: string; referencia: string }) =>
      http.post(`${PATHS.facturas}/${id}/pagos`, { monto: Number(v.monto), fecha: v.fecha, metodo: v.metodo, referencia: v.referencia.trim() || null },
        { headers: { 'Idempotency-Key': key.current } }),
    onSuccess: () => { notify('Pago registrado.'); onDone() },
    onError: (e) => notify(errorMessage(e), 'error'),
  })
  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="xs">
      <form onSubmit={handleSubmit((v) => save.mutate(v))} noValidate>
        <DialogTitle>Registrar pago</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gap: 2, pt: 1 }}>
            <Alert severity="info" icon={false}>Saldo pendiente: <b>{money(saldo)}</b></Alert>
            <TextField label="Monto" type="number" size="small" required slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
              error={!!errors.monto} helperText={errors.monto?.message}
              {...register('monto', { validate: (v) => (Number(v) > 0 && Number(v) <= saldo) || `Entre 0,01 y ${money(saldo)}` })} />
            <TextField label="Fecha" type="date" size="small" required slotProps={{ inputLabel: { shrink: true } }} {...register('fecha', { required: true })} />
            <TextField select label="Método" size="small" defaultValue="TRANSFERENCIA" {...register('metodo')}>
              {METODOS.map((m) => <MenuItem key={m} value={m}>{m.charAt(0) + m.slice(1).toLowerCase()}</MenuItem>)}
            </TextField>
            <TextField label="Referencia (opcional)" size="small" {...register('referencia')} />
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={save.isPending}>{save.isPending ? 'Guardando…' : 'Registrar pago'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}

export default function FacturaDetalleDialog({ id, onClose }: { id: number | string; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [paying, setPaying] = useState(false)
  const [voiding, setVoiding] = useState(false)

  const { data: f, isLoading, error } = useQuery({
    queryKey: ['factura', id],
    queryFn: async () => (await http.get(`${PATHS.facturas}/${id}`)).data,
  })
  const refresh = () => {
    for (const k of [[PATHS.facturas], ['factura', id], [PATHS.credito], ['credito']]) qc.invalidateQueries({ queryKey: k })
  }

  const anular = useMutation({
    mutationFn: (motivo: string) => http.post(`${PATHS.facturas}/${id}/anular`, { motivo }),
    onSuccess: () => { refresh(); notify('Factura anulada.'); setVoiding(false) },
    onError: (e) => { notify(errorMessage(e), 'error'); setVoiding(false) },
  })

  const lines: any[] = f?.detalles ?? []
  const pagos: any[] = f?.pagos ?? []
  const saldo = Number(f?.saldo ?? 0)
  const open = f && f.estado !== 'ANULADA' && f.estado !== 'PAGADA'
  const Info = ({ label, value }: { label: string; value: string }) => (
    <Box><Typography variant="caption" color="text.secondary">{label}</Typography><Typography>{value}</Typography></Box>
  )

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Factura {f?.numero ?? ''}</DialogTitle>
      <DialogContent>
        {isLoading && <Typography>Cargando…</Typography>}
        {error && <Typography color="error">{errorMessage(error)}</Typography>}
        {f && (
          <>
            <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(4, 1fr)' }, gap: 2, mb: 2 }}>
              <Info label="Cliente" value={txt(f.cliente ?? f.clienteNombre)} />
              <Info label="Emisión" value={fecha(f.fechaEmision ?? f.fecha)} />
              <Info label="Vencimiento" value={fecha(f.fechaVencimiento)} />
              <Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={f.estado} /></Box></Box>
            </Box>
            {(f.pedido?.numero ?? f.pedidoNumero) && <Info label="Origen" value={`Orden de venta ${f.pedido?.numero ?? f.pedidoNumero}`} />}
            <Box sx={{ overflowX: 'auto', mt: 2 }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Producto</TableCell>
                    <TableCell align="right">Cantidad</TableCell>
                    <TableCell align="right">Precio</TableCell>
                    <TableCell align="right">Subtotal</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {lines.map((l, i) => (
                    <TableRow key={l.id ?? i}>
                      <TableCell>{productoLabel(l.producto)}</TableCell>
                      <TableCell align="right">{num(l.cantidad)}</TableCell>
                      <TableCell align="right">{money(l.precioUnitario)}</TableCell>
                      <TableCell align="right">{money(l.subtotal ?? Number(l.cantidad) * Number(l.precioUnitario))}</TableCell>
                    </TableRow>
                  ))}
                  {[['Subtotal', f.subtotal], ['Impuestos', f.impuestos], ['Total', f.total], ['Saldo pendiente', f.saldo]].filter(([, v]) => v != null).map(([label, v]) => (
                    <TableRow key={String(label)}>
                      <TableCell colSpan={3} align="right" sx={{ fontWeight: label === 'Total' || label === 'Saldo pendiente' ? 700 : 500 }}>{label}</TableCell>
                      <TableCell align="right" sx={{ fontWeight: label === 'Total' || label === 'Saldo pendiente' ? 700 : 500 }}>{money(v)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
            {pagos.length > 0 && (
              <>
                <Typography sx={{ mt: 3, mb: 1, fontWeight: 600 }}>Pagos registrados</Typography>
                <Table size="small">
                  <TableHead><TableRow><TableCell>Fecha</TableCell><TableCell>Método</TableCell><TableCell>Referencia</TableCell><TableCell align="right">Monto</TableCell></TableRow></TableHead>
                  <TableBody>
                    {pagos.map((p, i) => (
                      <TableRow key={p.id ?? i}>
                        <TableCell>{fecha(p.fecha)}</TableCell><TableCell>{txt(p.metodo)}</TableCell><TableCell>{txt(p.referencia)}</TableCell>
                        <TableCell align="right">{money(p.monto)}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </>
            )}
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}>
        <Button onClick={onClose}>Cerrar</Button>
        {open && pagos.length === 0 && can(PERM.facturaAnular) && <Button color="error" onClick={() => setVoiding(true)}>Anular factura</Button>}
        {open && saldo > 0 && can(PERM.facturaCobrar) && <Button variant="contained" onClick={() => setPaying(true)}>Registrar pago</Button>}
      </DialogActions>
      {paying && <PagoDialog id={id} saldo={saldo} onClose={() => setPaying(false)} onDone={() => { setPaying(false); refresh() }} />}
      {voiding && (
        <MotivoDialog title={`Anular factura ${f?.numero ?? ''}`} text="La factura dejará de contar como cuenta por cobrar. No se puede deshacer."
          confirmLabel="Anular factura" loading={anular.isPending} onSubmit={(m) => anular.mutate(m)} onClose={() => setVoiding(false)} />
      )}
    </Dialog>
  )
}
