import CrudPage, { type Column, type Field } from '../../shared/CrudPage'
import { PATHS, PERM } from '../../config/contract'
import { txt } from '../../shared/format'

const fields: Field[] = [
  { name: 'codigo', label: 'Código', required: true, maxLength: 20, half: true },
  { name: 'nombre', label: 'Nombre', required: true, maxLength: 100 },
  { name: 'sede', label: 'Sede', maxLength: 120, half: true },
  { name: 'ubicacion', label: 'Ubicación', maxLength: 200 },
]

const columns: Column[] = [
  { header: 'Código', render: (r) => txt(r.codigo) },
  { header: 'Nombre', render: (r) => txt(r.nombre) },
  { header: 'Sede', render: (r) => txt(r.sede) },
  { header: 'Ubicación', render: (r) => txt(r.ubicacion) },
]

export default function AlmacenesPage() {
  return (
    <CrudPage title="Almacenes" singular="Almacén" endpoint={PATHS.almacenes}
      readPermission={PERM.almacenesLeer} writePermission={PERM.almacenAdmin}
      fields={fields} columns={columns}
      filters={[{ queryParam: 'codigo', label: 'Código exacto', placeholder: 'Código' }]} />
  )
}
