import CrudPage, { type Column, type Field } from '../../shared/CrudPage'
import { PATHS, PERM } from '../../config/contract'
import { txt } from '../../shared/format'

const fields: Field[] = [
  { name: 'codigo', label: 'Código', required: true, maxLength: 20, half: true },
  { name: 'nombre', label: 'Nombre', required: true, maxLength: 100 },
]

const columns: Column[] = [
  { header: 'Código', render: (r) => txt(r.codigo) },
  { header: 'Nombre', render: (r) => txt(r.nombre) },
]

export default function DepartamentosPage() {
  return (
    <CrudPage title="Departamentos" singular="Departamento" endpoint={PATHS.departamentos}
      readPermission={PERM.departamentosLeer} writePermission={PERM.departamentoAdmin}
      fields={fields} columns={columns}
      filters={[{ queryParam: 'codigo', label: 'Código exacto', placeholder: 'Código' }]} />
  )
}
