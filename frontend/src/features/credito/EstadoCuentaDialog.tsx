import { useQuery } from '@tanstack/react-query'
import { Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Paper, Table, TableBody, TableCell, TableHead, TableRow, Typography } from '@mui/material'
import { http, errorMessage } from '../../api/http'
import { PATHS } from '../../config/contract'
import { EstadoChip } from '../../shared/EstadoChip'
import { fecha, money, num, txt } from '../../shared/format'

const BUCKETS: [string, string][] = [
  ['corriente', 'Por vencer'], ['dias1a30', '1–30 días'], ['dias31a60', '31–60 días'], ['dias61a90', '61–90 días'], ['mas90', 'Más de 90'],
]

export default function EstadoCuentaDialog({ clienteId, onClose }: { clienteId: number | string; onClose: () => void }) {
  const { data: c, isLoading, error } = useQuery({
    queryKey: ['estado-cuenta', clienteId],
    queryFn: async () => (await http.get(`${PATHS.credito}/clientes/${clienteId}/estado-cuenta`)).data,
  })
  const facturas: any[] = c?.facturas ?? []
  const aging = c?.antiguedad ?? {}

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>Estado de cuenta — {txt(c?.cliente)}</DialogTitle>
      <DialogContent>
        {isLoading && <Typography>Cargando…</Typography>}
        {error && <Typography color="error">{errorMessage(error)}</Typography>}
        {c && (
          <>
            <Box sx={{ display: 'grid', gridTemplateColumns: { sm: 'repeat(4, 1fr)' }, gap: 2, mb: 3 }}>
              {[['Límite de crédito', money(c.limiteCredito)], ['Saldo pendiente', money(c.saldoPendiente)], ['Crédito disponible', money(c.creditoDisponible)]].map(([l, v]) => (
                <Box key={l}><Typography variant="caption" color="text.secondary">{l}</Typography><Typography variant="h6">{v}</Typography></Box>
              ))}
              <Box><Typography variant="caption" color="text.secondary">Estado</Typography><Box><EstadoChip estado={c.estado} /></Box></Box>
            </Box>
            <Typography sx={{ mb: 1, fontWeight: 600 }}>Antigüedad de saldos</Typography>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(5, 1fr)' }, gap: 1.5, mb: 3 }}>
              {BUCKETS.map(([k, label]) => (
                <Paper key={k} variant="outlined" sx={{ p: 1.5 }}>
                  <Typography variant="caption" color="text.secondary">{label}</Typography>
                  <Typography sx={{ fontWeight: 600 }} color={k !== 'corriente' && Number(aging[k]) > 0 ? 'error' : undefined}>{money(aging[k] ?? 0)}</Typography>
                </Paper>
              ))}
            </Box>
            <Typography sx={{ mb: 1, fontWeight: 600 }}>Facturas pendientes</Typography>
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Factura</TableCell><TableCell>Emisión</TableCell><TableCell>Vencimiento</TableCell>
                    <TableCell align="right">Días vencida</TableCell><TableCell align="right">Total</TableCell><TableCell align="right">Saldo</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {facturas.length === 0 && <TableRow><TableCell colSpan={6} sx={{ textAlign: 'center', py: 3 }}>Sin facturas pendientes.</TableCell></TableRow>}
                  {facturas.map((f, i) => (
                    <TableRow key={f.id ?? i}>
                      <TableCell>{txt(f.numero)}</TableCell>
                      <TableCell>{fecha(f.fechaEmision)}</TableCell>
                      <TableCell>{fecha(f.fechaVencimiento)}</TableCell>
                      <TableCell align="right" sx={{ color: Number(f.diasVencido) > 0 ? 'error.main' : undefined }}>{num(f.diasVencido ?? 0)}</TableCell>
                      <TableCell align="right">{money(f.total)}</TableCell>
                      <TableCell align="right">{money(f.saldo)}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          </>
        )}
      </DialogContent>
      <DialogActions sx={{ px: 3, pb: 2 }}><Button onClick={onClose}>Cerrar</Button></DialogActions>
    </Dialog>
  )
}
