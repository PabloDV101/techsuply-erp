import { useState, type ReactNode } from 'react'
import { Controller, useForm, type Control, type FieldErrors, type UseFormRegister } from 'react-hook-form'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Box, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, InputAdornment, MenuItem,
  Paper, Table, TableBody, TableCell, TableContainer, TableHead, TablePagination, TableRow, TextField,
  Tooltip, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import EditIcon from '@mui/icons-material/Edit'
import SearchIcon from '@mui/icons-material/Search'
import ToggleOnIcon from '@mui/icons-material/ToggleOn'
import ToggleOffIcon from '@mui/icons-material/ToggleOff'
import { http, errorMessage } from '../api/http'
import { useAuth } from '../features/auth/AuthContext'
import { SEARCH_PARAM } from '../config/contract'
import { idValue, normalizePage, useLookup, type Row } from './api'
import { useNotify } from './Notify'
import { ConfirmDialog } from './ConfirmDialog'

export interface Field {
  name: string
  label: string
  type?: 'text' | 'email' | 'number' | 'multiline' | 'select'
  required?: boolean
  options?: { value: string; label: string }[]
  /** Opciones tomadas de otro endpoint (p. ej. categorías). El valor enviado es el id. */
  remote?: { endpoint: string; label: (r: Row) => string; rowKey: string }
  pattern?: RegExp
  patternMessage?: string
  half?: boolean
}

export interface Column {
  header: string
  render: (row: Row) => ReactNode
  align?: 'right'
}

interface Props {
  title: string
  singular: string
  endpoint: string
  writePermission: string
  fields: Field[]
  columns: Column[]
  searchHint: string
}

type Values = Record<string, string>

export default function CrudPage({ title, singular, endpoint, writePermission, fields, columns, searchHint }: Props) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const canWrite = can(writePermission)

  const [page, setPage] = useState(0)
  const [size, setSize] = useState(10)
  const [search, setSearch] = useState('')
  const [estado, setEstado] = useState('')
  const [editing, setEditing] = useState<Row | 'new' | null>(null)
  const [toToggle, setToToggle] = useState<Row | null>(null)

  const { data, isLoading, isError, error } = useQuery({
    queryKey: [endpoint, page, size, search, estado],
    queryFn: async () => {
      const params: Record<string, unknown> = { page, size }
      if (search) params[SEARCH_PARAM] = search
      if (estado) params.activo = estado === 'activos'
      return normalizePage((await http.get(endpoint, { params })).data)
    },
    placeholderData: keepPreviousData,
  })

  const save = useMutation({
    mutationFn: (v: { id?: Row['id']; body: Record<string, unknown> }) =>
      v.id === undefined ? http.post(endpoint, v.body) : http.put(`${endpoint}/${v.id}`, v.body),
    onSuccess: (_r, v) => {
      qc.invalidateQueries({ queryKey: [endpoint] })
      qc.invalidateQueries({ queryKey: ['lookup', endpoint] })
      notify(v.id === undefined ? `${singular} creado.` : `${singular} actualizado.`)
      setEditing(null)
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  const toggle = useMutation({
    mutationFn: (row: Row) => http.patch(`${endpoint}/${row.id}/estado`, { activo: !row.activo }),
    onSuccess: (_r, row) => {
      qc.invalidateQueries({ queryKey: [endpoint] })
      qc.invalidateQueries({ queryKey: ['lookup', endpoint] })
      notify(row.activo ? `${singular} desactivado.` : `${singular} activado.`)
      setToToggle(null)
    },
    onError: (e) => { notify(errorMessage(e), 'error'); setToToggle(null) },
  })

  const colSpan = columns.length + 1 + (canWrite ? 1 : 0)

  return (
    <Box>
      <Box sx={{ display: 'flex', alignItems: 'center', gap: 2, mb: 2, flexWrap: 'wrap' }}>
        <Typography variant="h5" sx={{ flexGrow: 1 }}>{title}</Typography>
        {canWrite && <Button variant="contained" startIcon={<AddIcon />} onClick={() => setEditing('new')}>Nuevo {singular.toLowerCase()}</Button>}
      </Box>

      <Paper elevation={0} sx={{ p: 2, mb: 2, display: 'flex', gap: 2, flexWrap: 'wrap' }}>
        <TextField
          size="small" placeholder={searchHint} value={search} sx={{ flex: '1 1 260px' }}
          onChange={(e) => { setSearch(e.target.value); setPage(0) }}
          slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
        />
        <TextField select size="small" label="Estado" value={estado} onChange={(e) => { setEstado(e.target.value); setPage(0) }} sx={{ width: 160 }}>
          <MenuItem value="">Todos</MenuItem>
          <MenuItem value="activos">Activos</MenuItem>
          <MenuItem value="inactivos">Inactivos</MenuItem>
        </TextField>
      </Paper>

      <Paper elevation={0}>
        <TableContainer>
          <Table size="small">
            <TableHead>
              <TableRow>
                {columns.map((c) => <TableCell key={c.header} align={c.align} sx={{ fontWeight: 600 }}>{c.header}</TableCell>)}
                <TableCell sx={{ fontWeight: 600 }}>Estado</TableCell>
                {canWrite && <TableCell align="right" sx={{ fontWeight: 600 }}>Acciones</TableCell>}
              </TableRow>
            </TableHead>
            <TableBody>
              {isLoading && <TableRow><TableCell colSpan={colSpan}>Cargando…</TableCell></TableRow>}
              {isError && <TableRow><TableCell colSpan={colSpan} sx={{ color: 'error.main' }}>{errorMessage(error)}</TableCell></TableRow>}
              {!isLoading && !isError && data?.rows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={colSpan} sx={{ py: 4, textAlign: 'center' }}>
                    {search || estado ? 'Ningún resultado con esos filtros.' : `Aún no hay registros. Crea el primer ${singular.toLowerCase()}.`}
                  </TableCell>
                </TableRow>
              )}
              {data?.rows.map((row) => (
                <TableRow key={row.id} hover sx={{ opacity: row.activo ? 1 : 0.6 }}>
                  {columns.map((c) => <TableCell key={c.header} align={c.align}>{c.render(row)}</TableCell>)}
                  <TableCell><Chip size="small" label={row.activo ? 'Activo' : 'Inactivo'} color={row.activo ? 'success' : 'default'} variant="outlined" /></TableCell>
                  {canWrite && (
                    <TableCell align="right">
                      <Tooltip title="Editar"><IconButton size="small" onClick={() => setEditing(row)} aria-label="Editar"><EditIcon fontSize="small" /></IconButton></Tooltip>
                      <Tooltip title={row.activo ? 'Desactivar' : 'Activar'}>
                        <IconButton size="small" onClick={() => setToToggle(row)} aria-label={row.activo ? 'Desactivar' : 'Activar'}>
                          {row.activo ? <ToggleOnIcon color="success" /> : <ToggleOffIcon />}
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  )}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </TableContainer>
        <TablePagination
          component="div" count={data?.total ?? 0} page={page} rowsPerPage={size} rowsPerPageOptions={[10, 25, 50]}
          onPageChange={(_e, p) => setPage(p)}
          onRowsPerPageChange={(e) => { setSize(Number(e.target.value)); setPage(0) }}
          labelRowsPerPage="Filas por página"
          labelDisplayedRows={({ from, to, count }) => `${from}–${to} de ${count}`}
        />
      </Paper>

      {editing && (
        <FormDialog
          title={editing === 'new' ? `Nuevo ${singular.toLowerCase()}` : `Editar ${singular.toLowerCase()}`}
          fields={fields}
          initial={editing === 'new' ? null : editing}
          saving={save.isPending}
          onClose={() => setEditing(null)}
          onSubmit={(body) => save.mutate({ id: editing === 'new' ? undefined : editing.id, body })}
        />
      )}

      <ConfirmDialog
        open={!!toToggle}
        title={toToggle?.activo ? `Desactivar ${singular.toLowerCase()}` : `Activar ${singular.toLowerCase()}`}
        text={toToggle?.activo ? 'No aparecerá en nuevas órdenes, pero se conserva su historial.' : 'Volverá a estar disponible para nuevas órdenes.'}
        confirmLabel={toToggle?.activo ? 'Desactivar' : 'Activar'}
        onConfirm={() => toToggle && toggle.mutate(toToggle)}
        onClose={() => setToToggle(null)}
      />
    </Box>
  )
}

function initialValue(f: Field, initial: Row | null): string {
  if (!initial) return ''
  const direct = initial[f.name]
  if (direct != null) return String(direct)
  if (f.remote) {
    const nested = initial[f.remote.rowKey]
    if (nested && typeof nested === 'object') return String((nested as Row).id ?? '')
  }
  return ''
}

function FormDialog({ title, fields, initial, saving, onClose, onSubmit }: {
  title: string; fields: Field[]; initial: Row | null; saving: boolean
  onClose: () => void; onSubmit: (body: Record<string, unknown>) => void
}) {
  const { register, control, handleSubmit, formState: { errors } } = useForm<Values>({
    defaultValues: Object.fromEntries(fields.map((f) => [f.name, initialValue(f, initial)])),
  })

  const submit = (v: Values) => {
    const body: Record<string, unknown> = {}
    for (const f of fields) {
      const raw = (v[f.name] ?? '').trim()
      if (raw === '') body[f.name] = null
      else if (f.type === 'number') body[f.name] = Number(raw)
      else if (f.type === 'select') body[f.name] = f.remote ? idValue(raw) : raw
      else body[f.name] = raw
    }
    onSubmit(body)
  }

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="sm">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <DialogTitle>{title}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gridTemplateColumns: { sm: '1fr 1fr' }, gap: 2, pt: 1 }}>
            {fields.map((f) => <FieldInput key={f.name} f={f} control={control} register={register} errors={errors} />)}
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={saving}>{saving ? 'Guardando…' : 'Guardar cambios'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}

function FieldInput({ f, control, register, errors }: {
  f: Field; control: Control<Values>; register: UseFormRegister<Values>; errors: FieldErrors<Values>
}) {
  const remote = useLookup(f.remote?.endpoint ?? '', {}, !!f.remote)
  const error = errors[f.name]
  const full = !(f.half || f.type === 'select')
  const sx = { gridColumn: full ? { sm: '1 / -1' } : undefined }
  const required = f.required ? 'Este campo es obligatorio' : false

  if (f.type === 'select') {
    const options = f.remote ? (remote.data ?? []).map((r) => ({ value: String(r.id), label: f.remote!.label(r) })) : (f.options ?? [])
    return (
      <Controller
        name={f.name} control={control} rules={{ required }}
        render={({ field }) => (
          <TextField select size="small" label={f.label} required={f.required} sx={sx} value={field.value ?? ''} onChange={field.onChange} onBlur={field.onBlur}
            error={!!error} helperText={error?.message}>
            {!f.required && <MenuItem value="">Sin asignar</MenuItem>}
            {options.map((o) => <MenuItem key={o.value} value={o.value}>{o.label}</MenuItem>)}
          </TextField>
        )}
      />
    )
  }
  return (
    <TextField
      label={f.label} size="small" sx={sx} required={f.required}
      multiline={f.type === 'multiline'} minRows={f.type === 'multiline' ? 2 : undefined}
      type={f.type === 'number' ? 'number' : f.type === 'email' ? 'email' : 'text'}
      slotProps={f.type === 'number' ? { htmlInput: { step: '0.01', min: 0 } } : undefined}
      error={!!error} helperText={error?.message}
      {...register(f.name, { required, pattern: f.pattern ? { value: f.pattern, message: f.patternMessage ?? 'Formato no válido' } : undefined })}
    />
  )
}
