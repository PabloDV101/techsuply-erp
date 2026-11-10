import { Autocomplete, TextField } from '@mui/material'
import { useLookup } from './api'
import { productoLabel } from './format'
import { PATHS } from '../config/contract'

interface Props {
  value: string
  onChange: (v: string) => void
  label?: string
  required?: boolean
  error?: boolean
  helperText?: string
  disabled?: boolean
  width?: number | string
}

function LookupSelect({ endpoint, getLabel, label, value, onChange, required, error, helperText, disabled, width }: Props & {
  endpoint: string
  getLabel: (o: any) => string
}) {
  const { data = [] } = useLookup(endpoint)
  return (
    <Autocomplete
      size="small" options={data} disabled={disabled} sx={{ width }}
      getOptionLabel={getLabel}
      isOptionEqualToValue={(a, b) => a.id === b.id}
      value={data.find((o) => String(o.id) === value) ?? null}
      onChange={(_e, v) => onChange(v ? String(v.id) : '')}
      noOptionsText="Sin resultados"
      renderInput={(p) => <TextField {...p} label={label} required={required} error={error} helperText={helperText} />}
    />
  )
}

export const ProductoSelect = (p: Props) => (
  <LookupSelect {...p} label={p.label ?? 'Producto'} endpoint={PATHS.productos} getLabel={productoLabel} />
)

export const AlmacenSelect = (p: Props) => (
  <LookupSelect {...p} label={p.label ?? 'Almacén'} endpoint={PATHS.almacenes} getLabel={(o) => String(o.nombre ?? o.codigo ?? o.id)} />
)
