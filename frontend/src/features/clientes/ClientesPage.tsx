import CrudPage, { type Column, type Field } from '../../shared/CrudPage'
import { PATHS, PERM } from '../../config/contract'
import { money, txt } from '../../shared/format'

const fields: Field[] = [
  { name: 'codigo', label: 'Código', required: true, maxLength: 20, half: true },
  { name: 'nombreRazonSocial', label: 'Nombre / razón social', required: true, maxLength: 160 },
  { name: 'identificadorFiscal', label: 'Identificador fiscal', maxLength: 20, half: true },
  { name: 'correo', label: 'Correo electrónico', type: 'email', maxLength: 150 },
  { name: 'telefono', label: 'Teléfono', maxLength: 30, half: true },
  { name: 'direccion', label: 'Dirección', type: 'multiline' },
  {
    name: 'limiteCredito', label: 'Límite de crédito', type: 'number', required: true,
    permission: PERM.clientesCreditoAdmin, defaultValue: '0', min: 0, max: 999999999999.99,
    step: '0.01', integerDigits: 12, fractionDigits: 2, half: true,
  },
  {
    name: 'plazoCreditoDias', label: 'Plazo de crédito (días)', type: 'number', required: true,
    permission: PERM.clientesCreditoAdmin, defaultValue: '0', min: 0, max: 32767,
    step: '1', integerDigits: 5, fractionDigits: 0, half: true,
  },
]

const columns: Column[] = [
  { header: 'Código', render: (r) => txt(r.codigo) },
  { header: 'Nombre / razón social', render: (r) => txt(r.nombreRazonSocial) },
  { header: 'Identificador fiscal', render: (r) => txt(r.identificadorFiscal) },
  { header: 'Correo', render: (r) => txt(r.correo) },
  { header: 'Teléfono', render: (r) => txt(r.telefono) },
  { header: 'Límite de crédito', align: 'right', render: (r) => money(r.limiteCredito) },
  { header: 'Plazo', align: 'right', render: (r) => `${r.plazoCreditoDias ?? 0} días` },
]

export default function ClientesPage() {
  return (
    <CrudPage title="Clientes" singular="Cliente" endpoint={PATHS.clientes}
      readPermission={PERM.clientesLeer} writePermission={PERM.clienteWrite}
      fields={fields} columns={columns} />
  )
}
