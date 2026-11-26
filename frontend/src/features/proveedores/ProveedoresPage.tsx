import CrudPage, { type Column, type Field } from '../../shared/CrudPage'
import { PATHS, PERM } from '../../config/contract'
import { txt } from '../../shared/format'

const fields: Field[] = [
  { name: 'codigo', label: 'Código', required: true, maxLength: 20, half: true },
  { name: 'nombreRazonSocial', label: 'Razón social', required: true, maxLength: 160 },
  { name: 'identificadorFiscal', label: 'Identificador fiscal', maxLength: 20, half: true },
  { name: 'contacto', label: 'Persona de contacto', maxLength: 100, half: true },
  { name: 'correo', label: 'Correo electrónico', type: 'email', maxLength: 150 },
  { name: 'telefono', label: 'Teléfono', maxLength: 30, half: true },
  { name: 'plazoCreditoDias', label: 'Plazo de crédito (días)', type: 'number', min: 0, max: 32767, step: '1', integerDigits: 5, fractionDigits: 0, half: true },
  { name: 'direccion', label: 'Dirección', type: 'multiline' },
]

const columns: Column[] = [
  { header: 'Código', render: (r) => txt(r.codigo) },
  { header: 'Razón social', render: (r) => txt(r.nombreRazonSocial) },
  { header: 'Identificador fiscal', render: (r) => txt(r.identificadorFiscal) },
  { header: 'Contacto', render: (r) => txt(r.contacto) },
  { header: 'Correo', render: (r) => txt(r.correo) },
  { header: 'Plazo', align: 'right', render: (r) => `${r.plazoCreditoDias ?? 0} días` },
]

export default function ProveedoresPage() {
  return (
    <CrudPage title="Proveedores" singular="Proveedor" endpoint={PATHS.proveedores}
      readPermission={PERM.proveedoresLeer} writePermission={PERM.proveedorWrite}
      fields={fields} columns={columns}
      filters={[{ queryParam: 'codigo', label: 'Código exacto', placeholder: 'Código' }]} />
  )
}
