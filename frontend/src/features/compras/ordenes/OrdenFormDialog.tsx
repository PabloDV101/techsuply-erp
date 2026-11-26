import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { useMutation, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Autocomplete, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, IconButton, TextField, Typography,
} from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import { http, errorMessage } from '../../../api/http'
import { PATHS } from '../../../config/contract'
import { useLookup, type Row } from '../../../shared/api'
import { money } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'

interface Line { productoId: string; cantidad: string; precioUnitario: string; descuentoPct: string; tasaImpuesto: string }
interface Values { proveedorId: string; departamentoId: string; observaciones: string; detalles: Line[] }

const precision = (value: string, integer: number, fraction: number, positive = false) => {
  const pattern = new RegExp(`^${positive ? '(?=.*[1-9])' : ''}\\d{1,${integer}}(?:\\.\\d{1,${fraction}})?$`)
  return pattern.test(value) || `Usa hasta ${integer} enteros y ${fraction} decimales.`
}

export default function OrdenFormDialog({ onClose }: { onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const suppliersQuery = useLookup(PATHS.proveedores)
  const departmentsQuery = useLookup(PATHS.departamentos)
  const productsQuery = useLookup(PATHS.productos)
  const suppliers = suppliersQuery.data ?? []
  const departments = departmentsQuery.data ?? []
  const products = productsQuery.data ?? []

  const { control, register, handleSubmit, setValue, formState: { errors } } = useForm<Values>({
    defaultValues: {
      proveedorId: '', departamentoId: '', observaciones: '',
      detalles: [{ productoId: '', cantidad: '1', precioUnitario: '', descuentoPct: '0', tasaImpuesto: '0' }],
    },
  })
  const { fields, append, remove } = useFieldArray({ control, name: 'detalles' })
  const lines = useWatch({ control, name: 'detalles' }) ?? []
  const totalEstimado = lines.reduce((sum, line) => {
    const bruto = (Number(line.cantidad) || 0) * (Number(line.precioUnitario) || 0)
    const base = bruto * (1 - (Number(line.descuentoPct) || 0) / 100)
    return sum + base * (1 + (Number(line.tasaImpuesto) || 0) / 100)
  }, 0)

  const save = useMutation({
    mutationFn: (body: Record<string, unknown>) => http.post(PATHS.ordenes, body),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: [PATHS.ordenes] })
      notify('Orden creada en borrador.')
      onClose()
    },
    onError: (e) => notify(errorMessage(e), 'error'),
  })

  const submit = (values: Values) => {
    const ids = values.detalles.map((line) => line.productoId)
    if (new Set(ids).size !== ids.length) {
      notify('Cada producto debe aparecer una sola vez en la orden.', 'error')
      return
    }
    save.mutate({
      proveedorId: Number(values.proveedorId),
      departamentoId: Number(values.departamentoId),
      observaciones: values.observaciones.trim() || null,
      detalles: values.detalles.map((line) => ({
        productoId: Number(line.productoId),
        cantidad: Number(line.cantidad),
        precioUnitario: Number(line.precioUnitario),
        descuentoPct: Number(line.descuentoPct),
        tasaImpuesto: Number(line.tasaImpuesto),
      })),
    })
  }

  const optionError = (query: { error: Error | null }) => query.error ? errorMessage(query.error) : undefined

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="md">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <DialogTitle>Nueva orden de compra</DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>La API crea la orden como borrador. No reserva ni aumenta existencias.</Alert>
          <Box sx={{ display: 'grid', gridTemplateColumns: { sm: '1fr 1fr' }, gap: 2, pt: 1 }}>
            <Controller name="proveedorId" control={control} rules={{ required: 'Elige un proveedor activo.' }}
              render={({ field, fieldState }) => (
                <Autocomplete size="small" options={suppliers} loading={suppliersQuery.isLoading}
                  getOptionLabel={(item: Row) => `${item.codigo ?? ''} — ${item.nombreRazonSocial ?? ''}`}
                  isOptionEqualToValue={(a, b) => a.id === b.id}
                  value={suppliers.find((item) => String(item.id) === field.value) ?? null}
                  onChange={(_event, value) => field.onChange(value ? String(value.id) : '')}
                  renderInput={(params) => <TextField {...params} label="Proveedor" required error={!!fieldState.error || !!suppliersQuery.error}
                    helperText={fieldState.error?.message ?? optionError(suppliersQuery)} />}
                />
              )} />
            <Controller name="departamentoId" control={control} rules={{ required: 'Elige un departamento.' }}
              render={({ field, fieldState }) => (
                <Autocomplete size="small" options={departments} loading={departmentsQuery.isLoading}
                  getOptionLabel={(item: Row) => `${item.codigo ?? ''} — ${item.nombre ?? ''}`}
                  isOptionEqualToValue={(a, b) => a.id === b.id}
                  value={departments.find((item) => String(item.id) === field.value) ?? null}
                  onChange={(_event, value) => field.onChange(value ? String(value.id) : '')}
                  renderInput={(params) => <TextField {...params} label="Departamento" required error={!!fieldState.error || !!departmentsQuery.error}
                    helperText={fieldState.error?.message ?? optionError(departmentsQuery)} />}
                />
              )} />
            <TextField label="Observaciones" size="small" multiline minRows={2} slotProps={{ htmlInput: { maxLength: 2000 } }}
              sx={{ gridColumn: { sm: '1 / -1' } }} {...register('observaciones')} />
          </Box>

          <Typography sx={{ mt: 3, mb: 1, fontWeight: 600 }}>Productos</Typography>
          {productsQuery.error && <Alert severity="error" sx={{ mb: 2 }}>{errorMessage(productsQuery.error)}</Alert>}
          {fields.map((item, index) => (
            <Box key={item.id} sx={{ display: 'grid', gridTemplateColumns: { sm: '2.5fr repeat(4, 1fr) auto' }, gap: 1.25, mb: 1.5, alignItems: 'start' }}>
              <Controller name={`detalles.${index}.productoId`} control={control}
                rules={{ required: 'Elige un producto activo.', validate: (value) => !value || idsUnique(lines, index) || 'No repitas el producto.' }}
                render={({ field, fieldState }) => (
                  <Autocomplete size="small" options={products} loading={productsQuery.isLoading}
                    getOptionLabel={(product: Row) => `${product.sku ?? ''} — ${product.nombre ?? ''}`}
                    isOptionEqualToValue={(a, b) => a.id === b.id}
                    value={products.find((product) => String(product.id) === field.value) ?? null}
                    onChange={(_event, product) => {
                      field.onChange(product ? String(product.id) : '')
                      if (product) {
                        setValue(`detalles.${index}.precioUnitario`, String(product.costoReferencia ?? ''))
                        setValue(`detalles.${index}.tasaImpuesto`, String(product.tasaImpuesto ?? '0'))
                      }
                    }}
                    renderInput={(params) => <TextField {...params} label="Producto" required error={!!fieldState.error || !!productsQuery.error}
                      helperText={fieldState.error?.message ?? optionError(productsQuery)} />}
                  />
                )} />
              <TextField label="Cantidad" type="number" size="small" slotProps={{ htmlInput: { min: 0, step: '0.001' } }}
                error={!!errors.detalles?.[index]?.cantidad} helperText={errors.detalles?.[index]?.cantidad?.message}
                {...register(`detalles.${index}.cantidad`, { validate: (value) => precision(value, 11, 3, true) })} />
              <TextField label="Precio unitario" type="number" size="small" slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
                error={!!errors.detalles?.[index]?.precioUnitario} helperText={errors.detalles?.[index]?.precioUnitario?.message}
                {...register(`detalles.${index}.precioUnitario`, { validate: (value) => precision(value, 12, 2) })} />
              <TextField label="Descuento %" type="number" size="small" slotProps={{ htmlInput: { min: 0, max: 100, step: '0.01' } }}
                error={!!errors.detalles?.[index]?.descuentoPct} helperText={errors.detalles?.[index]?.descuentoPct?.message}
                {...register(`detalles.${index}.descuentoPct`, { validate: (value) => (Number(value) <= 100 && precision(value, 3, 2)) || 'Entre 0 y 100, con hasta 2 decimales.' })} />
              <TextField label="Impuesto %" type="number" size="small" slotProps={{ htmlInput: { min: 0, max: 100, step: '0.01' } }}
                error={!!errors.detalles?.[index]?.tasaImpuesto} helperText={errors.detalles?.[index]?.tasaImpuesto?.message}
                {...register(`detalles.${index}.tasaImpuesto`, { validate: (value) => (Number(value) <= 100 && precision(value, 3, 2)) || 'Entre 0 y 100, con hasta 2 decimales.' })} />
              <IconButton onClick={() => remove(index)} disabled={fields.length === 1} aria-label="Quitar línea"><DeleteIcon /></IconButton>
            </Box>
          ))}
          <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1, gap: 2 }}>
            <Button startIcon={<AddIcon />} onClick={() => append({ productoId: '', cantidad: '1', precioUnitario: '', descuentoPct: '0', tasaImpuesto: '0' })}>Agregar producto</Button>
            <Typography sx={{ fontWeight: 600 }}>Total estimado: {money(totalEstimado)}</Typography>
          </Box>
          <Typography variant="caption" color="text.secondary">El total definitivo y sus redondeos los calcula el servidor al guardar.</Typography>
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="contained" disabled={save.isPending || !!suppliersQuery.error || !!departmentsQuery.error || !!productsQuery.error}>
            {save.isPending ? 'Guardando…' : 'Guardar borrador'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}

function idsUnique(lines: Line[], index: number) {
  const current = lines[index]?.productoId
  return !current || lines.every((line, lineIndex) => lineIndex === index || line.productoId !== current)
}
