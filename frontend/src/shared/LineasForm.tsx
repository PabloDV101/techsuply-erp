import { Controller, useFieldArray, useWatch, type Control, type FieldErrors, type UseFormRegister, type UseFormSetValue } from 'react-hook-form'
import { Autocomplete, Box, Button, IconButton, TextField, Typography } from '@mui/material'
import AddIcon from '@mui/icons-material/Add'
import DeleteIcon from '@mui/icons-material/DeleteOutlined'
import { PATHS } from '../config/contract'
import { useLookup } from './api'
import { money } from './format'

export interface LineaValues { productoId: string; cantidad: string; precioUnitario: string; descuento: string }
export interface DocValues {
  clienteId: string
  fecha: string
  vence: string
  almacenId: string
  observaciones: string
  detalles: LineaValues[]
}

export const lineTotal = (l: LineaValues) =>
  (Number(l.cantidad) || 0) * (Number(l.precioUnitario) || 0) * (1 - (Number(l.descuento) || 0) / 100)
export const emptyLine = (): LineaValues => ({ productoId: '', cantidad: '1', precioUnitario: '', descuento: '0' })

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
  const total = lines.reduce((s, l) => s + lineTotal(l), 0)

  return (
    <>
      <Typography sx={{ mt: 3, mb: 1, fontWeight: 600 }}>Productos</Typography>
      {fields.map((f, i) => (
        <Box key={f.id} sx={{ display: 'grid', gridTemplateColumns: { sm: '3fr 1fr 1fr 1fr auto' }, gap: 1.5, mb: 1.5, alignItems: 'start' }}>
          <Controller
            name={`detalles.${i}.productoId`} control={control} rules={{ required: 'Elige un producto' }}
            render={({ field, fieldState }) => (
              <Autocomplete
                size="small" options={productos}
                getOptionLabel={(o) => `${o.codigo ?? ''} — ${o.descripcion ?? ''}`}
                isOptionEqualToValue={(a, b) => a.id === b.id}
                value={productos.find((p) => String(p.id) === field.value) ?? null}
                onChange={(_e, v) => {
                  field.onChange(v ? String(v.id) : '')
                  const price = v?.precioVenta ?? v?.precioReferencia
                  if (price != null) setValue(`detalles.${i}.precioUnitario`, String(price))
                }}
                renderInput={(p) => <TextField {...p} label="Producto" required error={!!fieldState.error} helperText={fieldState.error?.message} />}
              />
            )}
          />
          <TextField label="Cantidad" type="number" size="small" slotProps={{ htmlInput: { min: 0, step: 'any' } }}
            error={!!errors.detalles?.[i]?.cantidad} helperText={errors.detalles?.[i]?.cantidad?.message}
            {...register(`detalles.${i}.cantidad`, { validate: (v) => Number(v) > 0 || 'Mayor que 0' })} />
          <TextField label="Precio" type="number" size="small" slotProps={{ htmlInput: { min: 0, step: '0.01' } }}
            error={!!errors.detalles?.[i]?.precioUnitario} helperText={errors.detalles?.[i]?.precioUnitario?.message}
            {...register(`detalles.${i}.precioUnitario`, { validate: (v) => (v !== '' && Number(v) >= 0) || 'Indica el precio' })} />
          <TextField label="Desc. %" type="number" size="small" slotProps={{ htmlInput: { min: 0, max: 100, step: '0.1' } }}
            error={!!errors.detalles?.[i]?.descuento} helperText={errors.detalles?.[i]?.descuento?.message}
            {...register(`detalles.${i}.descuento`, { validate: (v) => (Number(v) >= 0 && Number(v) <= 100) || '0 a 100' })} />
          <IconButton onClick={() => remove(i)} disabled={fields.length === 1} aria-label="Quitar línea"><DeleteIcon /></IconButton>
        </Box>
      ))}
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mt: 1 }}>
        <Button startIcon={<AddIcon />} onClick={() => append(emptyLine())}>Agregar producto</Button>
        <Typography sx={{ fontWeight: 600 }}>Subtotal: {money(total)}</Typography>
      </Box>
    </>
  )
}
