import CrudPage, { type Column, type Field } from '../../shared/CrudPage'
import { PATHS, PERM } from '../../config/contract'
import { money, num, txt } from '../../shared/format'

const fields: Field[] = [
  { name: 'sku', label: 'SKU', required: true, maxLength: 30, half: true },
  { name: 'nombre', label: 'Nombre', required: true, maxLength: 160, half: true },
  { name: 'descripcion', label: 'Descripción', type: 'multiline' },
  { name: 'unidadMedida', label: 'Unidad de medida', required: true, maxLength: 20, half: true },
  {
    name: 'categoriaId', label: 'Categoría', type: 'select', half: true, required: true,
    remote: {
      endpoint: PATHS.categorias, rowKey: 'categoriaId',
      activeFilter: { param: 'activa', value: true },
      label: (r) => `${r.codigo} — ${r.nombre}`,
    },
  },
  {
    name: 'proveedorPrincipalId', label: 'Proveedor principal', type: 'select', half: true,
    remote: { endpoint: PATHS.proveedores, rowKey: 'proveedorPrincipalId', label: (r) => `${r.codigo} — ${r.nombreRazonSocial}` },
  },
  { name: 'precioLista', label: 'Precio de lista', type: 'number', required: true, min: 0, max: 999999999999.99, step: '0.01', integerDigits: 12, fractionDigits: 2, half: true },
  { name: 'costoReferencia', label: 'Costo de referencia', type: 'number', required: true, min: 0, max: 999999999999.99, step: '0.01', integerDigits: 12, fractionDigits: 2, half: true },
  { name: 'tasaImpuesto', label: 'Impuesto (%)', type: 'number', required: true, min: 0, max: 100, step: '0.01', integerDigits: 3, fractionDigits: 2, half: true },
  { name: 'puntoReorden', label: 'Punto de reorden', type: 'number', required: true, min: 0, max: 99999999999.999, step: '0.001', integerDigits: 11, fractionDigits: 3, half: true },
  { name: 'cantidadReorden', label: 'Cantidad de reorden', type: 'number', required: true, min: 0, max: 99999999999.999, step: '0.001', integerDigits: 11, fractionDigits: 3, half: true },
]

const columns: Column[] = [
  { header: 'SKU', render: (r) => txt(r.sku) },
  { header: 'Nombre', render: (r) => txt(r.nombre) },
  { header: 'Categoría ID', render: (r) => txt(r.categoriaId) },
  { header: 'Unidad', render: (r) => txt(r.unidadMedida) },
  { header: 'Precio de lista', align: 'right', render: (r) => money(r.precioLista) },
  { header: 'Costo referencia', align: 'right', render: (r) => money(r.costoReferencia) },
  { header: 'Punto / cantidad reorden', align: 'right', render: (r) => `${num(r.puntoReorden)} / ${num(r.cantidadReorden)}` },
]

export default function ProductosPage() {
  return (
    <CrudPage title="Productos" singular="Producto" endpoint={PATHS.productos}
      readPermission={PERM.productosLeer} writePermission={PERM.productoWrite}
      fields={fields} columns={columns}
      filters={[{ queryParam: 'sku', label: 'SKU exacto', placeholder: 'SKU' }]} />
  )
}
