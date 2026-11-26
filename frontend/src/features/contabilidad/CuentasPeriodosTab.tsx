import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Paper, Tab, Tabs, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Tooltip, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import EditIcon from '@mui/icons-material/EditOutlined'
import ToggleIcon from '@mui/icons-material/ToggleOnOutlined'
import { http, errorMessage } from '../../api/http'
import { PATHS, PERM } from '../../config/contract'
import { type Row } from '../../shared/api'
import { ConfirmDialog } from '../../shared/ConfirmDialog'
import { useNotify } from '../../shared/Notify'
import { useAuth } from '../auth/AuthContext'
import { allPages } from './accountingApi'

const accountTypes = ['ACTIVO', 'PASIVO', 'CAPITAL', 'INGRESO', 'COSTO', 'GASTO']
const natures = ['DEUDORA', 'ACREEDORA']

export default function CuentasPeriodosTab() {
  const { can } = useAuth()
  const [view, setView] = useState(0)
  const tabs = [
    ...(can(PERM.contabilidadLeer) ? [{ label: 'Plan de cuentas', view: <Accounts/> }] : []),
    ...(can(PERM.periodosLeer) ? [{ label: 'Periodos contables', view: <Periods/> }] : []),
  ]
  const selected = Math.min(view, tabs.length - 1)
  return tabs.length ? <Box><Paper elevation={0} sx={{ mb: 2 }}><Tabs value={selected} onChange={(_, value) => setView(value)}>{tabs.map((tab) => <Tab key={tab.label} label={tab.label}/>)}</Tabs></Paper>{tabs[selected]?.view}</Box> : <Alert severity="info">No tienes permiso de lectura de cuentas ni de periodos.</Alert>
}

function Accounts() {
  const qc = useQueryClient(); const notify = useNotify(); const { can } = useAuth()
  const [dialog, setDialog] = useState<Row | 'new' | null>(null)
  const [statusTarget, setStatusTarget] = useState<Row | null>(null)
  const accounts = useQuery({ queryKey: [PATHS.cuentasContables, 'all'], enabled: can(PERM.contabilidadLeer), queryFn: () => allPages(PATHS.cuentasContables, { sort: 'codigo', direction: 'asc' }) })
  const save = useMutation({ mutationFn: ({ id, body }: { id?: number | string; body: Record<string, unknown> }) => id ? http.put(`${PATHS.cuentasContables}/${id}`, body) : http.post(PATHS.cuentasContables, body), onSuccess: () => { void qc.invalidateQueries({ queryKey: [PATHS.cuentasContables] }); notify('Cuenta contable guardada.'); setDialog(null) }, onError: (error) => notify(errorMessage(error), 'error') })
  const changeStatus = useMutation({ mutationFn: ({ account, active }: { account: Row; active: boolean }) => http.patch(`${PATHS.cuentasContables}/${account.id}/status`, { activa: active }), onSuccess: () => { void qc.invalidateQueries({ queryKey: [PATHS.cuentasContables] }); notify('Estado de cuenta actualizado.'); setStatusTarget(null) }, onError: (error) => { notify(errorMessage(error), 'error'); setStatusTarget(null) } })
  return <Paper elevation={0}>
    <Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 2 }}><Typography variant="h6" sx={{ flexGrow: 1 }}>Plan de cuentas</Typography>{can(PERM.cuentaAdmin) && <Button startIcon={<AddIcon/>} variant="contained" onClick={() => setDialog('new')}>Nueva cuenta</Button>}</Box>
    {accounts.error && <Alert severity="error" sx={{ m: 2 }}>{errorMessage(accounts.error)}</Alert>}
    <TableContainer><Table size="small"><TableHead><TableRow><TableCell>Código</TableCell><TableCell>Nombre</TableCell><TableCell>Tipo</TableCell><TableCell>Naturaleza</TableCell><TableCell>Nivel</TableCell><TableCell>Uso</TableCell><TableCell>Estado</TableCell><TableCell/></TableRow></TableHead><TableBody>
      {accounts.isLoading && <TableRow><TableCell colSpan={8}>Cargando cuentas…</TableCell></TableRow>}{!accounts.isLoading && !accounts.error && !accounts.data?.length && <TableRow><TableCell colSpan={8} align="center">Aún no hay cuentas contables.</TableCell></TableRow>}
      {accounts.data?.map((account) => <TableRow key={account.id}><TableCell>{account.codigo}</TableCell><TableCell>{account.nombre}</TableCell><TableCell>{account.tipo}</TableCell><TableCell>{account.naturaleza}</TableCell><TableCell>{account.nivel}</TableCell><TableCell>{account.aceptaMovimientos ? 'Acepta movimientos' : 'Agrupadora'}</TableCell><TableCell>{account.activa ? 'Activa' : 'Inactiva'}</TableCell><TableCell align="right">{can(PERM.cuentaAdmin) && <><Tooltip title="Editar"><Button aria-label={`Editar cuenta ${account.codigo}`} size="small" onClick={() => setDialog(account)}><EditIcon fontSize="small"/></Button></Tooltip><Tooltip title={account.activa ? 'Desactivar' : 'Activar'}><Button aria-label={`${account.activa ? 'Desactivar' : 'Activar'} cuenta ${account.codigo}`} size="small" onClick={() => setStatusTarget(account)}><ToggleIcon fontSize="small"/></Button></Tooltip></>}</TableCell></TableRow>)}
    </TableBody></Table></TableContainer>
    {dialog && <AccountDialog account={dialog === 'new' ? null : dialog} accounts={accounts.data ?? []} saving={save.isPending} onClose={() => setDialog(null)} onSubmit={(body) => save.mutate({ id: dialog === 'new' ? undefined : dialog.id, body })}/>}
    <ConfirmDialog open={!!statusTarget} title={`${statusTarget?.activa ? 'Desactivar' : 'Activar'} cuenta`} text={`${statusTarget?.codigo} — ${statusTarget?.nombre}. La API impedirá desactivar cuentas con partidas.`} confirmLabel={statusTarget?.activa ? 'Desactivar' : 'Activar'} onClose={() => setStatusTarget(null)} onConfirm={() => statusTarget && changeStatus.mutate({ account: statusTarget, active: !statusTarget.activa })}/>
  </Paper>
}

function AccountDialog({ account, accounts, saving, onClose, onSubmit }: { account: Row | null; accounts: Row[]; saving: boolean; onClose: () => void; onSubmit: (body: Record<string, unknown>) => void }) {
  const [codigo, setCodigo] = useState(String(account?.codigo ?? '')); const [nombre, setNombre] = useState(String(account?.nombre ?? ''))
  const [tipo, setTipo] = useState(String(account?.tipo ?? 'ACTIVO')); const [naturaleza, setNaturaleza] = useState(String(account?.naturaleza ?? 'DEUDORA'))
  const [parent, setParent] = useState(account?.cuentaPadreId == null ? '' : String(account.cuentaPadreId)); const [movements, setMovements] = useState(Boolean(account?.aceptaMovimientos ?? true))
  const parents = accounts.filter((candidate) => candidate.activa && !candidate.aceptaMovimientos && String(candidate.id) !== String(account?.id))
  const submit = () => onSubmit({ codigo: codigo.trim(), nombre: nombre.trim(), tipo, naturaleza, cuentaPadreId: parent ? Number(parent) : null, aceptaMovimientos: movements })
  return <Dialog open onClose={onClose} fullWidth maxWidth="sm"><DialogTitle>{account ? 'Editar cuenta contable' : 'Nueva cuenta contable'}</DialogTitle><DialogContent sx={{ display: 'grid', gap: 2, pt: '8px!important' }}>
    <TextField label="Código" required value={codigo} onChange={(event) => setCodigo(event.target.value)} slotProps={{ htmlInput: { maxLength: 30 }}}/><TextField label="Nombre" required value={nombre} onChange={(event) => setNombre(event.target.value)} slotProps={{ htmlInput: { maxLength: 150 }}}/>
    <TextField select label="Tipo" value={tipo} disabled={!!account?.id} onChange={(event) => setTipo(event.target.value)}>{accountTypes.map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField>
    <TextField select label="Naturaleza" value={naturaleza} disabled={!!account?.id} onChange={(event) => setNaturaleza(event.target.value)}>{natures.map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField>
    <TextField select label="Cuenta padre" value={parent} disabled={!!account?.id} onChange={(event) => setParent(event.target.value)}><MenuItem value="">Sin cuenta padre</MenuItem>{parents.map((candidate) => <MenuItem key={candidate.id} value={candidate.id}>{candidate.codigo} — {candidate.nombre}</MenuItem>)}</TextField>
    <TextField select label="Uso" value={movements ? 'MOVIMIENTO' : 'AGRUPADORA'} disabled={!!account?.id} onChange={(event) => setMovements(event.target.value === 'MOVIMIENTO')}><MenuItem value="MOVIMIENTO">Acepta movimientos</MenuItem><MenuItem value="AGRUPADORA">Cuenta agrupadora</MenuItem></TextField>
    {account && <Alert severity="info">Con partidas registradas, el backend solo permite cambiar el nombre. El nivel y la estructura los determina la API.</Alert>}
  </DialogContent><DialogActions><Button onClick={onClose}>Cerrar</Button><Button variant="contained" disabled={saving || !codigo.trim() || !nombre.trim()} onClick={submit}>Guardar cuenta</Button></DialogActions></Dialog>
}

function Periods() {
  const qc = useQueryClient(); const notify = useNotify(); const { can } = useAuth()
  const [createOpen, setCreateOpen] = useState(false); const [closing, setClosing] = useState<Row | null>(null)
  const periods = useQuery({ queryKey: [PATHS.periodosContables, 'all'], enabled: can(PERM.periodosLeer), queryFn: () => allPages(PATHS.periodosContables, { sort: 'fechaInicio', direction: 'asc' }) })
  const create = useMutation({ mutationFn: (body: { fechaInicio: string; fechaFin: string }) => http.post(PATHS.periodosContables, body), onSuccess: () => { void qc.invalidateQueries({ queryKey: [PATHS.periodosContables] }); notify('Periodo contable creado.'); setCreateOpen(false) }, onError: (error) => notify(errorMessage(error), 'error') })
  const close = useMutation({ mutationFn: (period: Row) => http.post(`${PATHS.periodosContables}/${period.id}/close`), onSuccess: () => { void qc.invalidateQueries({ queryKey: [PATHS.periodosContables] }); notify('Periodo cerrado. Los periodos cerrados no se reabren.'); setClosing(null) }, onError: (error) => { notify(errorMessage(error), 'error'); setClosing(null) } })
  return <Paper elevation={0}><Box sx={{ p: 2, display: 'flex', alignItems: 'center', gap: 2 }}><Typography variant="h6" sx={{ flexGrow: 1 }}>Periodos contables</Typography>{can(PERM.periodoAdmin) && <Button startIcon={<AddIcon/>} variant="contained" onClick={() => setCreateOpen(true)}>Nuevo periodo</Button>}</Box>
    {periods.error && <Alert severity="error" sx={{ m: 2 }}>{errorMessage(periods.error)}</Alert>}<TableContainer><Table size="small"><TableHead><TableRow><TableCell>Inicio</TableCell><TableCell>Fin</TableCell><TableCell>Estado</TableCell><TableCell>Cerrado por</TableCell><TableCell/></TableRow></TableHead><TableBody>{periods.isLoading && <TableRow><TableCell colSpan={5}>Cargando periodos…</TableCell></TableRow>}{!periods.isLoading && !periods.error && !periods.data?.length && <TableRow><TableCell colSpan={5} align="center">Aún no hay periodos.</TableCell></TableRow>}{periods.data?.map((period) => <TableRow key={period.id}><TableCell>{period.fechaInicio}</TableCell><TableCell>{period.fechaFin}</TableCell><TableCell>{period.estado}</TableCell><TableCell>{period.cerradoPorUsuarioId ?? '—'}</TableCell><TableCell align="right">{period.estado === 'ABIERTO' && can(PERM.periodoCerrar) && <Button size="small" onClick={() => setClosing(period)}>Cerrar periodo</Button>}</TableCell></TableRow>)}</TableBody></Table></TableContainer>
    {createOpen && <PeriodDialog saving={create.isPending} onClose={() => setCreateOpen(false)} onSubmit={(body) => create.mutate(body)}/>}
    <ConfirmDialog open={!!closing} title="Cerrar periodo contable" text={`Se bloquearán nuevos asientos en el periodo ${closing?.fechaInicio} a ${closing?.fechaFin}. La API rechazará el cierre si existen borradores y no existe reapertura.`} confirmLabel="Cerrar periodo" onClose={() => setClosing(null)} onConfirm={() => closing && close.mutate(closing)}/>
  </Paper>
}

function PeriodDialog({ saving, onClose, onSubmit }: { saving: boolean; onClose: () => void; onSubmit: (body: { fechaInicio: string; fechaFin: string }) => void }) {
  const [start, setStart] = useState(''); const [end, setEnd] = useState('')
  return <Dialog open onClose={onClose} fullWidth maxWidth="xs"><DialogTitle>Nuevo periodo contable</DialogTitle><DialogContent sx={{ display: 'grid', gap: 2, pt: '8px!important' }}><TextField label="Fecha inicio" type="date" value={start} onChange={(event) => setStart(event.target.value)} slotProps={{ inputLabel: { shrink: true }}}/><TextField label="Fecha fin" type="date" value={end} onChange={(event) => setEnd(event.target.value)} slotProps={{ inputLabel: { shrink: true }}}/><Typography variant="caption" color="text.secondary">Se aceptan periodos pasados o futuros. No pueden solaparse.</Typography></DialogContent><DialogActions><Button onClick={onClose}>Cancelar</Button><Button variant="contained" disabled={!start || !end || start > end || saving} onClick={() => onSubmit({ fechaInicio: start, fechaFin: end })}>Crear periodo</Button></DialogActions></Dialog>
}
