import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Autocomplete, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, TextField, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { idValue, useLookup, type Row } from '../../../shared/api'
import { money, today } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'

interface Line { productoId: string; cantidad: string; precioUnitario: string }
interface Values { proveedorId: string; fecha: string; observaciones: string; detalles: Line[] }

/** Carga la orden (si se edita) y muestra el formulario cuando está lista. */
export default function OrdenFormDialog({ id, onClose }: { id?: number | string; onClose: () => void }) {
  const { data, isLoading, error } = useQuery({
    queryKey: ['orden', id],
    enabled: id !== undefined,
    queryFn: async () => (await http.get(`${PATHS.ordenes}/${id}`)).data,
  })
  if (id !== undefined && (isLoading || error)) {
    return (
      <Dialog open onClose={onClose}>
        <DialogContent>{error ? <Typography color="error">{errorMessage(error)}</Typography> : 'Cargando…'}</DialogContent>
        <DialogActions><Button onClick={onClose}>Cerrar</Button></DialogActions>
      </Dialog>
    )
  }
  return <Form id={id} existing={data} onClose={onClose} />
}

function Form({ id, existing, onClose }: { id?: number | string; existing?: any; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const proveedores = useLookup(PATHS.proveedores).data ?? []
  const productos = useLookup(PATHS.productos).data ?? []

  const { control, register, handleSubmit, setValue, formState: { errors } } = useForm<Values>({
    defaultValues: {
      proveedorId: String(existing?.proveedor?.id ?? existing?.proveedorId ?? ''),
      fecha: String(existing?.fecha ?? today()).slice(0, 10),
      observaciones: existing?.observaciones ?? '',
      detalles: existing?.detalles?.length
        ? existing.detalles.map((d: any) => ({
            productoId: String(d.producto?.id ?? d.productoId ?? ''),
            cantidad: String(d.cantidad ?? ''),
            precioUnitario: String(d.precioUnitario ?? ''),
          }))
        : [{ productoId: '', cantidad: '1', precioUnitario: '' }],
    },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'detalles' })
  const lines = useWatch({ control, name: 'detalles' })
  const total = (lines ?? []).reduce((s, l) => s + (Number(l.cantidad) || 0) * (Number(l.precioUnitario) || 0), 0)

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) =>
      id === undefined ? http.post(PATHS.ordenes, body) : http.put(`${PATHS.ordenes}/${id}`, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [PATHS.ordenes] })
      qc.invalidateQueries({ queryKey: ['orden', id] })
      notify(id === undefined ? 'Orden creada en borrador.' : 'Orden actualizada.')
      onClose()
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  const submit = (v: Values) =>
    save.mutate({
      proveedorId: idValue(v.proveedorId),
      fecha: v.fecha,
      observaciones: v.observaciones.trim() || null,
      detalles: v.detalles.map((d) => ({
        productoId: idValue(d.productoId),
        cantidad: Number(d.cantidad),
        precioUnitario: Number(d.precioUnitario),
      })),
    })

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <DialogTitle>{id === undefined ? 'Nueva orden de compra' : `Editar orden ${existing?.numero ?? ''}`}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: 'grid', gridTemplateColumns: { sm: '2fr 1fr' }, gap: 2, pt: 1 }}>
            <Controller
              name="proveedorId" control={control} rules={{ required: 'Elige un proveedor' }}
              render={({ field, fieldState }) => (
                <Autocomplete
                  size="small" options={proveedores}
                  getOptionLabel={(o) => `${o.codigo ?? ''} — ${o.razonSocial ?? ''}`}
                  isOptionEqualToValue={(a, b) => a.id === b.id}
                  value={proveedores.find((p) => String(p.id) === field.value) ?? null}
                  onChange={(_e, v) => field.onChange(v ? String(v.id) : '')}
                  renderInput={(p) => <TextField {...p} label="Proveedor" required error={!!fieldState.error} helperText={fieldState.error?.message} />}
                />
              )}
            />
            <TextField label="Fecha" type="date" size="small" required slotProps={{ inputLabel: { shrink: true } }}
              error={!!errors.fecha} helperText={errors.fecha?.message} {...register('fecha', { required: 'Indica la fecha' })} />
            <TextField label="Observaciones" size="small" multiline minRows={2} sx={{ gridColumn: { sm: '1 / -1' } }} {...register('observaciones')} />
          </Box>

          <Typography sx={{ mt: 3, mb: 1, fontWeight: 600 }}>Productos</Typography>
          {fields.map((f, i) => (
            <Box key={f.id} sx={{ display: 'grid', gridTemplateColumns: { sm: '3fr 1fr 1fr auto' }, gap: 1.5, mb: 1.5, alignItems: 'start' }}>
              <Controller
                name={`detalles.${i}.productoId`} control={control} rules={{ required: 'Elige un producto' }}
                render={({ field, fieldState }) => (
                  <Autocomplete
                    size="small" options={productos}
                    getOptionLabel={(o: Row) => `${o.codigo ?? ''} — ${o.descripcion ?? ''}`}
                    isOptionEqualToValue={(a, b) => a.id === b.id}
                    value={productos.find((p) => String(p.id) === field.value) ?? null}
                    onChange={(_e, v) => {
                      field.onChange(v ? String(v.id) : '')
                      if (v?.precioReferencia != null) setValue(`detalles.${i}.precioUnitario`, String(v.precioReferencia))
                    }}
                    renderInput={(p) => <TextField {...p} label="Producto" required error={!!fieldState.error} helperText={fieldState.error?.message} />}
                  />
                )}
              />
              <TextField label="Cantidad" type="number" size="small" slotProps={{ htmlInput: { min: 0, step: 'any' } }}
                error={!!errors.detalles?.[i]?.cantidad} helperText={errors.detalles?.[i]?.cantidad?.message}
                {...register(`detalles.${i}.cantidad`, { validate: (v) => Number(v) > 0 || 'Mayor que 0' })} />
              <TextField label="Precio unitario" type="number" size="small" slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
                error={!!errors.detalles?.[i]?.precioUnitario} helperText={errors.detalles?.[i]?.precioUnitario?.message}
                {...register(`detalles.${i}.precioUnitario`, { validate: (v) => (v !== '' && Number(v) >= 0) || 'Indica el precio' })} />
              <IconButton onClick={() => remove(i)} disabled={fields.length === 1} aria-label="Quitar línea"><DeleteIcon /></IconButton>
            </Box>
          ))}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1 }}>
            <Button startIcon={<AddIcon />} onClick={() => append({ productoId: '', cantidad: '1', precioUnitario: '' })}>Agregar producto</Button>
            <Typography sx={{ fontWeight: 600 }} >Total: {money(total)}</Typography>
          </Box>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={save.isPending}>{save.isPending ? 'Guardando…' : 'Guardar borrador'}</Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
