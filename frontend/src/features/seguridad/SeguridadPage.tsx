import { useMemo, useState } from 'react'
import { Alert, Box, Button, Checkbox, FormControl, FormControlLabel, FormGroup, InputLabel, MenuItem, Paper, Select, Stack, Tab, Tabs, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { http, errorMessage } from '../../api/http'
import { PATHS, PERM } from '../../config/contract'
import { useAuth } from '../auth/AuthContext'
import { useNotify } from '../../shared/Notify'

type User = { id: number; username: string; correo: string; activo: boolean; ultimoAcceso?: string | null; roles: string[]; permisos: string[] }
type Role = { id: number; codigo: string; nombre: string; descripcion?: string | null; permisos: string[] }
type Permission = { id: number; codigo: string; modulo: string; accion: string; descripcion?: string | null }

export default function SeguridadPage() {
  const { can } = useAuth()
  const [tab, setTab] = useState(0)
  const panels = [
    { label: 'Usuarios', allowed: can(PERM.seguridadUsuariosAdmin), element: <UsersPanel /> },
    { label: 'Roles', allowed: can(PERM.seguridadRolesAdmin), element: <RolesPanel /> },
    { label: 'Permisos', allowed: can(PERM.seguridadPermisosAdmin), element: <PermissionsPanel /> },
  ].filter((panel) => panel.allowed)
  const selected = panels[Math.min(tab, panels.length - 1)]
  return <Stack spacing={2}>
    <Box><Typography variant="h4">Seguridad y acceso</Typography><Typography color="text.secondary">Administración de cuentas, roles y permisos mediante las operaciones permitidas al usuario actual.</Typography></Box>
    {panels.length > 0 && <><Paper><Tabs value={Math.min(tab, panels.length - 1)} onChange={(_, value) => setTab(value)} aria-label="Administración de seguridad">{panels.map((panel) => <Tab key={panel.label} label={panel.label}/>)}</Tabs></Paper>{selected?.element}</>}
    {!panels.length && <Alert severity="warning">Tu cuenta no tiene permisos para administrar seguridad.</Alert>}
  </Stack>
}

function useAdminData<T>(path: string, enabled: boolean) {
  return useQuery({ queryKey: [path], enabled, queryFn: async () => (await http.get(path)).data as T[] })
}
function useAdminMutation<T>(path: string, method: 'post' | 'put' | 'patch', success: string, key: string) {
  const client = useQueryClient(); const notify = useNotify()
  return useMutation({ mutationFn: async ({ url = path, body }: { url?: string; body: unknown }) => (await http[method](url, body)).data as T,
    onSuccess: () => { notify(success); void client.invalidateQueries({ queryKey: [path] }); void client.invalidateQueries({ queryKey: [key] }) },
    onError: (e) => notify(errorMessage(e), 'error') })
}

function UsersPanel() {
  const { can, user: actor } = useAuth(); const notify = useNotify()
  const users = useAdminData<User>(PATHS.adminUsers, can(PERM.seguridadUsuariosAdmin))
  const roles = useAdminData<Role>(PATHS.adminRoles, can(PERM.seguridadRolesAdmin))
  const [form, setForm] = useState({ username: '', correo: '', password: '', roles: [] as string[] })
  const [selected, setSelected] = useState<User | null>(null); const [selectedRoles, setSelectedRoles] = useState<string[]>([])
  const create = useAdminMutation<User>(PATHS.adminUsers, 'post', 'Usuario creado.', PATHS.adminRoles)
  const status = useAdminMutation<User>(PATHS.adminUsers, 'patch', 'Estado actualizado. Las sesiones del usuario desactivado se revocan.', PATHS.adminUsers)
  const replace = useAdminMutation<User>(PATHS.adminUsers, 'put', 'Roles actualizados. Las sesiones activas se revocan.', PATHS.adminRoles)
  const delegable = useMemo(() => (roles.data || []).filter((r) => r.permisos.every((p) => actor?.permisos.includes(p))), [roles.data, actor?.permisos])
  const canCreate = can(PERM.seguridadUsuariosAdmin) && can(PERM.seguridadRolesAdmin)
  const submitCreate = () => {
    if (new TextEncoder().encode(form.password).length > 72 || form.password.length < 12 || form.roles.length === 0) { notify('La contraseña requiere 12–72 caracteres UTF-8 y al menos un rol delegable.', 'error'); return }
    create.mutate({ body: form }, { onSuccess: () => setForm({ username: '', correo: '', password: '', roles: [] }) })
  }
  return <Stack spacing={2}>
    {!canCreate && can(PERM.seguridadUsuariosAdmin) && <Alert severity="info">Para crear usuarios también se requiere administración de roles; la cuenta debe recibir solo roles cuyos permisos puedas delegar.</Alert>}
    {canCreate && <Paper sx={{ p: 2 }}><Typography variant="h6" gutterBottom>Crear usuario</Typography><Stack direction={{ xs: 'column', md: 'row' }} spacing={1}>
      <TextField label="Usuario" value={form.username} onChange={(e) => setForm({ ...form, username: e.target.value })}/><TextField label="Correo" type="email" value={form.correo} onChange={(e) => setForm({ ...form, correo: e.target.value })}/><TextField label="Contraseña temporal" type="password" autoComplete="new-password" value={form.password} helperText="12–72 caracteres; el campo se limpia al crear la cuenta." onChange={(e) => setForm({ ...form, password: e.target.value })}/>
      <FormControl sx={{ minWidth: 220 }}><InputLabel id="roles-new-label">Roles</InputLabel><Select labelId="roles-new-label" multiple label="Roles" value={form.roles} onChange={(e) => setForm({ ...form, roles: e.target.value as string[] })}>{delegable.map((r) => <MenuItem key={r.codigo} value={r.codigo}>{r.nombre} ({r.codigo})</MenuItem>)}</Select></FormControl>
      <Button variant="contained" disabled={create.isPending} onClick={submitCreate}>Crear</Button>
    </Stack></Paper>}
    {can(PERM.seguridadRolesAdmin) && delegable.length < (roles.data?.length || 0) && <Alert severity="info">Los roles con permisos que no posees se ocultan para evitar asignación no delegable.</Alert>}
    <Paper sx={{ overflowX: 'auto' }}><Table size="small"><TableHead><TableRow>{['ID','Usuario','Correo','Roles','Estado','Último acceso','Acciones'].map((x) => <TableCell key={x}>{x}</TableCell>)}</TableRow></TableHead><TableBody>
      {(users.data || []).map((u) => { const editable = can(PERM.seguridadRolesAdmin) && u.roles.every((r) => delegable.some((d) => d.codigo === r)); return <TableRow key={u.id}><TableCell>{u.id}</TableCell><TableCell>{u.username}</TableCell><TableCell>{u.correo}</TableCell><TableCell>{u.roles.join(', ')}</TableCell><TableCell>{u.activo ? 'Activo' : 'Inactivo'}</TableCell><TableCell>{u.ultimoAcceso ? new Date(u.ultimoAcceso).toLocaleString() : 'Nunca'}</TableCell><TableCell><Stack direction="row"><Button size="small" disabled={!can(PERM.seguridadUsuariosAdmin) || status.isPending} onClick={() => status.mutate({ url: `${PATHS.adminUsers}/${u.id}/status`, body: { activo: !u.activo } })}>{u.activo ? 'Desactivar' : 'Activar'}</Button><Button size="small" disabled={!editable} onClick={() => { setSelected(u); setSelectedRoles(u.roles.filter((r) => delegable.some((d) => d.codigo === r))) }}>Roles</Button></Stack></TableCell></TableRow>})}
      {users.isLoading && <TableRow><TableCell colSpan={7}>Cargando usuarios…</TableCell></TableRow>}{users.isError && <TableRow><TableCell colSpan={7}>No se pudo cargar usuarios. Recarga para reintentar.</TableCell></TableRow>}
    </TableBody></Table></Paper>
    {selected && <Paper sx={{ p: 2 }}><Typography variant="h6">Roles para {selected.username}</Typography><Alert severity="warning" sx={{ my: 1 }}>Guardar reemplaza todos los roles actuales y revoca sus sesiones. No puedes editar usuarios con roles no delegables.</Alert><FormControl fullWidth><InputLabel id="roles-edit-label">Roles</InputLabel><Select labelId="roles-edit-label" multiple label="Roles" value={selectedRoles} onChange={(e) => setSelectedRoles(e.target.value as string[])}>{delegable.map((r) => <MenuItem key={r.codigo} value={r.codigo}>{r.nombre}</MenuItem>)}</Select></FormControl><Stack direction="row" spacing={1} sx={{ mt: 1 }}><Button disabled={!selectedRoles.length || replace.isPending} variant="contained" onClick={() => replace.mutate({ url: `${PATHS.adminUsers}/${selected.id}/roles`, body: { roles: selectedRoles } }, { onSuccess: () => setSelected(null) })}>Reemplazar roles</Button><Button onClick={() => setSelected(null)}>Cancelar</Button></Stack></Paper>}
  </Stack>
}

function RolesPanel() {
  const { user } = useAuth(); const roles = useAdminData<Role>(PATHS.adminRoles, true)
  const create = useAdminMutation<Role>(PATHS.adminRoles, 'post', 'Rol creado.', PATHS.adminRoles); const replace = useAdminMutation<Role>(PATHS.adminRoles, 'put', 'Permisos del rol actualizados.', PATHS.adminRoles)
  const [name, setName] = useState(''); const [code, setCode] = useState(''); const [description, setDescription] = useState(''); const [newPerms, setNewPerms] = useState<string[]>([]); const [editing, setEditing] = useState<Role | null>(null); const [editPerms, setEditPerms] = useState<string[]>([])
  const owned = user?.permisos || []; const canEdit = (r: Role) => r.permisos.every((p) => owned.includes(p))
  return <Stack spacing={2}><Alert severity="info">Solo puedes conceder permisos que posees. Los roles con permisos fuera de tu alcance no se pueden modificar desde esta pantalla.</Alert>
    <Paper sx={{ p: 2 }}><Typography variant="h6">Crear rol</Typography><Stack direction={{ xs: 'column', md: 'row' }} spacing={1} sx={{ mt: 1 }}><TextField label="Código" value={code} onChange={(e) => setCode(e.target.value.toUpperCase())}/><TextField label="Nombre" value={name} onChange={(e) => setName(e.target.value)}/><TextField label="Descripción" value={description} onChange={(e) => setDescription(e.target.value)}/><FormControl sx={{ minWidth: 260 }}><InputLabel id="new-role-perms">Permisos propios</InputLabel><Select labelId="new-role-perms" multiple label="Permisos propios" value={newPerms} onChange={(e) => setNewPerms(e.target.value as string[])}>{owned.map((p) => <MenuItem key={p} value={p}>{p}</MenuItem>)}</Select></FormControl><Button variant="contained" disabled={create.isPending || !newPerms.length} onClick={() => create.mutate({ body: { codigo: code, nombre: name, descripcion: description || null, permisos: newPerms } }, { onSuccess: () => { setCode(''); setName(''); setDescription(''); setNewPerms([]) } })}>Crear</Button></Stack></Paper>
    <Paper sx={{ overflowX: 'auto' }}><Table size="small"><TableHead><TableRow>{['Código','Nombre','Descripción','Permisos',''].map((x) => <TableCell key={x}>{x}</TableCell>)}</TableRow></TableHead><TableBody>{(roles.data || []).map((r) => <TableRow key={r.id}><TableCell>{r.codigo}</TableCell><TableCell>{r.nombre}</TableCell><TableCell>{r.descripcion}</TableCell><TableCell>{r.permisos.join(', ')}</TableCell><TableCell><Button disabled={!canEdit(r)} onClick={() => { setEditing(r); setEditPerms(r.permisos) }}>Editar permisos</Button></TableCell></TableRow>)}</TableBody></Table></Paper>
    {editing && <Paper sx={{ p: 2 }}><Typography variant="h6">Permisos de {editing.codigo}</Typography><FormGroup sx={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(240px,1fr))' }}>{owned.map((p) => <FormControlLabel key={p} control={<Checkbox checked={editPerms.includes(p)} onChange={(e) => setEditPerms(e.target.checked ? [...editPerms,p] : editPerms.filter((x) => x !== p))}/>} label={p}/>)}</FormGroup><Stack direction="row" spacing={1}><Button variant="contained" disabled={replace.isPending} onClick={() => replace.mutate({ url: `${PATHS.adminRoles}/${editing.id}/permissions`, body: { permisos: editPerms } }, { onSuccess: () => setEditing(null) })}>Guardar permisos</Button><Button onClick={() => setEditing(null)}>Cancelar</Button></Stack></Paper>}
  </Stack>
}

function PermissionsPanel() {
  const query = useAdminData<Permission>(PATHS.adminPermissions, true); const create = useAdminMutation<Permission>(PATHS.adminPermissions, 'post', 'Permiso creado.', PATHS.adminPermissions)
  const [codigo, setCodigo] = useState(''); const [modulo, setModulo] = useState(''); const [accion, setAccion] = useState(''); const [descripcion, setDescripcion] = useState('')
  return <Stack spacing={2}><Paper sx={{ p: 2 }}><Typography variant="h6">Registrar permiso</Typography><Alert severity="info" sx={{ my: 1 }}>Crear el código no lo asigna automáticamente a ningún rol.</Alert><Stack direction={{ xs: 'column', md: 'row' }} spacing={1}><TextField label="Código" value={codigo} onChange={(e) => setCodigo(e.target.value.toUpperCase())}/><TextField label="Módulo" value={modulo} onChange={(e) => setModulo(e.target.value.toUpperCase())}/><TextField label="Acción" value={accion} onChange={(e) => setAccion(e.target.value.toUpperCase())}/><TextField label="Descripción" value={descripcion} onChange={(e) => setDescripcion(e.target.value)}/><Button variant="contained" disabled={create.isPending} onClick={() => create.mutate({ body: { codigo, modulo, accion, descripcion: descripcion || null } }, { onSuccess: () => { setCodigo(''); setModulo(''); setAccion(''); setDescripcion('') } })}>Crear</Button></Stack></Paper><Paper sx={{ overflowX: 'auto' }}><Table size="small"><TableHead><TableRow>{['Código','Módulo','Acción','Descripción'].map((x) => <TableCell key={x}>{x}</TableCell>)}</TableRow></TableHead><TableBody>{(query.data || []).map((p) => <TableRow key={p.id}><TableCell>{p.codigo}</TableCell><TableCell>{p.modulo}</TableCell><TableCell>{p.accion}</TableCell><TableCell>{p.descripcion}</TableCell></TableRow>)}</TableBody></Table></Paper></Stack>
}
