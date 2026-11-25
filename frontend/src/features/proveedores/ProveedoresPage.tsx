import CrudPage, { type Column, type Field } from '../../shared/CrudPage'
import { PATHS, PERM } from '../../config/contract'
import { txt } from '../../shared/format'

const fields: Field[] = [
  { name: 'codigo', label: 'Código', required: true, half: true },
  { name: 'ruc', label: 'RUC / NIT', required: true, half: true },
  { name: 'razonSocial', label: 'Razón social', required: true },
  { name: 'contacto', label: 'Persona de contacto', half: true },
  { name: 'telefono', label: 'Teléfono', half: true },
  { name: 'email', label: 'Correo electrónico', type: 'email', pattern: /^\S+@\S+\.\S+$/, patternMessage: 'Correo no válido' },
  { name: 'direccion', label: 'Dirección', type: 'multiline' },
]

const columns: Column[] = [
  { header: 'Código', render: (r) => txt(r.codigo) },
  { header: 'RUC / NIT', render: (r) => txt(r.ruc) },
  { header: 'Razón social', render: (r) => txt(r.razonSocial) },
  { header: 'Contacto', render: (r) => txt(r.contacto) },
  { header: 'Teléfono', render: (r) => txt(r.telefono) },
]

export default function ProveedoresPage() {
  return (
    <CrudPage title="Proveedores" singular="Proveedor" endpoint={PATHS.proveedores} writePermission={PERM.proveedorWrite}
      fields={fields} columns={columns} searchHint="Buscar por código, RUC o razón social" />
  )
}
