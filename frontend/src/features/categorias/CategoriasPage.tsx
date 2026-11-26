import CrudPage, { type Column, type Field } from '../../shared/CrudPage'
import { PATHS, PERM } from '../../config/contract'
import { txt } from '../../shared/format'

const fields: Field[] = [
  { name: 'codigo', label: 'Código', required: true, maxLength: 20, half: true },
  { name: 'nombre', label: 'Nombre', required: true, maxLength: 100 },
  { name: 'descripcion', label: 'Descripción', type: 'multiline', maxLength: 250 },
  {
    name: 'categoriaPadreId', label: 'Categoría padre', type: 'select',
    remote: {
      endpoint: PATHS.categorias, rowKey: 'categoriaPadreId',
      activeFilter: { param: 'activa', value: true },
      label: (r) => `${r.codigo} — ${r.nombre}`,
    },
  },
]

const columns: Column[] = [
  { header: 'Código', render: (r) => txt(r.codigo) },
  { header: 'Nombre', render: (r) => txt(r.nombre) },
  { header: 'Categoría padre ID', render: (r) => txt(r.categoriaPadreId) },
  { header: 'Descripción', render: (r) => txt(r.descripcion) },
]

export default function CategoriasPage() {
  return (
    <CrudPage title="Categorías" singular="Categoría" endpoint={PATHS.categorias}
      readPermission={PERM.categoriasLeer} writePermission={PERM.categoriaAdmin}
      fields={fields} columns={columns} activeField="activa"
      statusFilter={{ queryParam: 'activa', bodyField: 'activa' }}
      filters={[
        { queryParam: 'codigo', label: 'Código exacto', placeholder: 'Código' },
        { queryParam: 'nombre', label: 'Nombre contiene', placeholder: 'Nombre' },
      ]} />
  )
}
