import { Controller, useFieldArray, useWatch, type Control, type FieldErrors, type UseFormRegister, type UseFormSetValue } from 'react-hook-form'
import { Autocomplete, Box, Button, IconButton, TextField, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import { PATHS } from '../config/contract'
import { useLookup } from './api'
import { money, productoLabel } from './format'

export interface LineaValues { productoId: string; cantidad: string; precioUnitario: string; descuentoPct: string; tasaImpuesto: string }
export interface DocValues { clienteId: string; vigenciaHasta: string; detalles: LineaValues[] }

export const lineTotal = (line: LineaValues) =>
  (Number(line.cantidad) || 0) * (Number(line.precioUnitario) || 0) * (1 - (Number(line.descuentoPct) || 0) / 100)
export const emptyLine = (): LineaValues => ({ productoId: '', cantidad: '1', precioUnitario: '', descuentoPct: '0', tasaImpuesto: '0' })

interface Props {
  control: Control<DocValues>
  register: UseFormRegister<DocValues>
  setValue: UseFormSetValue<DocValues>
  errors: FieldErrors<DocValues>
}

export default function LineasForm({ control, register, setValue, errors }: Props) {
  const productos = useLookup(PATHS.productos).data ?? []
  const { fields, append, remove } = useFieldArray({ control, name: 'detalles' })
  const lines = useWatch({ control, name: 'detalles' }) ?? []
  const subtotal = lines.reduce((sum, line) => sum + lineTotal(line), 0)

  return (
    <>
      <Typography sx={{ mt: 3, mb: 1, fontWeight: 600 }}>Productos cotizados</Typography>
      {fields.map((field, index) => (
        <Box key={field.id} sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: '3fr 1fr 1fr 1fr 1fr auto' }, gap: 1.5, mb: 1.5, alignItems: 'start' }}>
          <Controller
            name={`detalles.${index}.productoId`} control={control} rules={{ required: 'Elige un producto' }}
            render={({ field: input, fieldState }) => (
              <Autocomplete
                size="small" options={productos} getOptionLabel={productoLabel}
                isOptionEqualToValue={(a, b) => a.id === b.id}
                value={productos.find((product) => String(product.id) === input.value) ?? null}
                onChange={(_event, product) => {
                  input.onChange(product ? String(product.id) : '')
                  setValue(`detalles.${index}.precioUnitario`, product?.precioLista == null ? '' : String(product.precioLista))
                  setValue(`detalles.${index}.tasaImpuesto`, product?.tasaImpuesto == null ? '0' : String(product.tasaImpuesto))
                }}
                renderInput={(params) => <TextField {...params} label="Producto" required error={!!fieldState.error} helperText={fieldState.error?.message} />}
              />
            )}
          />
          <TextField label="Cantidad" type="number" size="small" slotProps={{ htmlInput: { min: 0, step: '0.001' } }}
            error={!!errors.detalles?.[index]?.cantidad} helperText={errors.detalles?.[index]?.cantidad?.message}
            {...register(`detalles.${index}.cantidad`, { validate: (value) => /^\d{1,11}(?:\.\d{1,3})?$/.test(value) && Number(value) > 0 || 'Cantidad positiva, hasta 3 decimales' })} />
          <TextField label="Precio unitario" type="number" size="small" slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
            error={!!errors.detalles?.[index]?.precioUnitario} helperText={errors.detalles?.[index]?.precioUnitario?.message}
            {...register(`detalles.${index}.precioUnitario`, { validate: (value) => /^\d{1,12}(?:\.\d{1,2})?$/.test(value) || 'Hasta 2 decimales' })} />
          <TextField label="Descuento %" type="number" size="small" slotProps={{ htmlInput: { min: 0, max: 100, step: '0.01' } }}
            error={!!errors.detalles?.[index]?.descuentoPct} helperText={errors.detalles?.[index]?.descuentoPct?.message}
            {...register(`detalles.${index}.descuentoPct`, { validate: (value) => /^\d{1,3}(?:\.\d{1,2})?$/.test(value) && Number(value) <= 100 || '0 a 100, hasta 2 decimales' })} />
          <TextField label="Impuesto %" size="small" disabled {...register(`detalles.${index}.tasaImpuesto`)} />
          <IconButton onClick={() => remove(index)} disabled={fields.length === 1} aria-label="Quitar línea"><DeleteIcon /></IconButton>
        </Box>
      ))}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1 }}>
        <Button startIcon={<AddIcon />} onClick={() => append(emptyLine())}>Agregar producto</Button>
        <Typography sx={{ fontWeight: 600 }}>Subtotal estimado sin impuestos: {money(subtotal)}</Typography>
      </Box>
    </>
  )
}
