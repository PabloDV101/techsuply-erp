import CrudPage, { type Column, type Field } from '../../shared/CrudPage'
import { PATHS, PERM } from '../../config/contract'
import { money, txt } from '../../shared/format'

const fields: Field[] = [
  { name: 'codigo', label: 'Código', required: true, half: true },
  { name: 'ruc', label: 'RUC / NIT / documento', required: true, half: true },
  { name: 'razonSocial', label: 'Razón social o nombre', required: true },
  { name: 'contacto', label: 'Persona de contacto', half: true },
  { name: 'telefono', label: 'Teléfono', half: true },
  { name: 'email', label: 'Correo electrónico', type: 'email', pattern: /^\S+@\S+\.\S+$/, patternMessage: 'Correo no válido' },
  { name: 'direccion', label: 'Dirección', type: 'multiline' },
  { name: 'limiteCredito', label: 'Límite de crédito', type: 'number', half: true },
  { name: 'plazoPagoDias', label: 'Plazo de pago (días)', type: 'number', half: true },
]

const columns: Column[] = [
  { header: 'Código', render: (r) => txt(r.codigo) },
  { header: 'RUC / documento', render: (r) => txt(r.ruc) },
  { header: 'Razón social', render: (r) => txt(r.razonSocial) },
  { header: 'Contacto', render: (r) => txt(r.contacto) },
  { header: 'Límite de crédito', align: 'right', render: (r) => money(r.limiteCredito) },
  { header: 'Plazo', align: 'right', render: (r) => (r.plazoPagoDias == null ? '—' : `${r.plazoPagoDias} días`) },
]

export default function ClientesPage() {
  return (
    <CrudPage title="Clientes" singular="Cliente" endpoint={PATHS.clientes} writePermission={PERM.clienteWrite}
      fields={fields} columns={columns} searchHint="Buscar por código, documento o razón social" />
  )
}
