import { useState } from 'react'
import { Alert, Button, MenuItem, Paper, Stack, Table, TableBody, TableCell, TableHead, TablePagination, TableRow, TextField, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { http, errorMessage } from '../../api/http'
import { PATHS } from '../../config/contract'
import { normalizePage, type Row } from '../../shared/api'
import { useNotify } from '../../shared/Notify'

type Notification = Row & { tipoEvento: string; modulo: string; entidad: string; entidadId: number; asunto: string; cuerpo: string; estado: 'NO_LEIDA' | 'LEIDA'; creadaEn: string; leidaEn?: string | null }
export default function NotificacionesPage() {
  const [page, setPage] = useState(0); const [read, setRead] = useState(''); const client = useQueryClient(); const notify = useNotify()
  const query = useQuery({ queryKey: [PATHS.notifications, page, read], queryFn: async () => normalizePage((await http.get(PATHS.notifications, { params: { page, size: 20, sort: 'creadaEn', direction: 'desc', ...(read ? { leida: read === 'true' } : {}) } })).data) as { rows: Notification[]; total: number } })
  const mutation = useMutation({ mutationFn: async (id: number | string) => (await http.patch(`${PATHS.notifications}/${id}/read`)).data,
    onSuccess: () => { notify('Notificación marcada como leída.'); void client.invalidateQueries({ queryKey: [PATHS.notifications] }) }, onError: (e) => notify(errorMessage(e), 'error') })
  return <Stack spacing={2}><div><Typography variant="h4">Notificaciones</Typography><Typography color="text.secondary">Bandeja personal del usuario autenticado. No se pueden crear notificaciones desde esta pantalla.</Typography></div>
    <TextField select label="Estado" value={read} onChange={(e) => { setRead(e.target.value); setPage(0) }} sx={{ maxWidth: 240 }}><MenuItem value="">Todas</MenuItem><MenuItem value="false">No leídas</MenuItem><MenuItem value="true">Leídas</MenuItem></TextField>
    {query.isError && <Alert severity="error">No se pudo cargar la bandeja.</Alert>}
    <Paper sx={{ overflowX: 'auto' }}><Table><TableHead><TableRow>{['Estado','Fecha','Tipo','Asunto','Detalle','Referencia','Leída','Acción'].map((x) => <TableCell key={x}>{x}</TableCell>)}</TableRow></TableHead><TableBody>{(query.data?.rows || []).map((n) => <TableRow key={n.id}><TableCell>{n.estado === 'NO_LEIDA' ? 'No leída' : 'Leída'}</TableCell><TableCell>{new Date(n.creadaEn).toLocaleString()}</TableCell><TableCell>{n.tipoEvento}</TableCell><TableCell>{n.asunto}</TableCell><TableCell>{n.cuerpo}</TableCell><TableCell>{n.modulo} / {n.entidad} #{n.entidadId}</TableCell><TableCell>{n.leidaEn ? new Date(n.leidaEn).toLocaleString() : '—'}</TableCell><TableCell><Button size="small" disabled={n.estado === 'LEIDA' || mutation.isPending} onClick={() => mutation.mutate(n.id)}>Marcar leída</Button></TableCell></TableRow>)}{query.isLoading && <TableRow><TableCell colSpan={8}>Cargando notificaciones…</TableCell></TableRow>}{!query.isLoading && !query.isError && !query.data?.rows.length && <TableRow><TableCell colSpan={8}>No tienes notificaciones para este filtro.</TableCell></TableRow>}</TableBody></Table><TablePagination component="div" count={query.data?.total || 0} page={page} rowsPerPage={20} rowsPerPageOptions={[20]} onPageChange={(_, value) => setPage(value)} /></Paper>
  </Stack>
}
