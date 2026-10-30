import CrudPage, { type Column, type Field } from '../../shared/CrudPage'
import { PATHS, PERM } from '../../config/contract'
import { money, txt } from '../../shared/format'

const unidades = ['UND', 'CAJA', 'KG', 'LT', 'MT', 'PAR'].map((u) => ({ value: u, label: u }))

const fields: Field[] = [
  { name: 'codigo', label: 'Código', required: true, half: true },
  {
    name: 'categoriaId', label: 'Categoría', type: 'select', half: true, required: true,
    remote: { endpoint: PATHS.categorias, rowKey: 'categoria', label: (r) => String(r.nombre ?? r.descripcion ?? r.codigo ?? r.id) },
  },
  { name: 'descripcion', label: 'Descripción', required: true },
  { name: 'unidadMedida', label: 'Unidad de medida', type: 'select', options: unidades, required: true, half: true },
  { name: 'precioReferencia', label: 'Precio de referencia', type: 'number', half: true },
]

const columns: Column[] = [
  { header: 'Código', render: (r) => txt(r.codigo) },
  { header: 'Descripción', render: (r) => txt(r.descripcion) },
  { header: 'Categoría', render: (r) => txt(r.categoria ?? r.categoriaNombre) },
  { header: 'Unidad', render: (r) => txt(r.unidadMedida) },
  { header: 'Precio ref.', align: 'right', render: (r) => money(r.precioReferencia) },
]

export default function ProductosPage() {
  return (
    <CrudPage title="Productos" singular="Producto" endpoint={PATHS.productos} writePermission={PERM.productoWrite}
      fields={fields} columns={columns} searchHint="Buscar por código o descripción" />
  )
}
