import { useEffect, useState } from 'react'
import { Controller, useFieldArray, useForm, useWatch } from 'react-hook-form'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  Alert, Autocomplete, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, Table, TableBody, TableCell, TableHead,
  TableRow, TextField, Typography,
} from '@mui/material'
import { http, errorMessage } from '../../../api/http'
import { PATHS, PERM } from '../../../config/contract'
import { idValue, useLookup, type Row } from '../../../shared/api'
import { num } from '../../../shared/format'
import { useNotify } from '../../../shared/Notify'
import { useAuth } from '../../auth/AuthContext'

interface Line { detalleId: string; productoId: string; pendiente: number; recibida: string; aceptada: string; rechazada: string; precio: string; motivo: string }
interface Values { ordenId: string; almacenId: string; referenciaExterna: string; lineas: Line[] }
type Snapshot = Record<string, unknown>
const amount = (value: string) => Number(value) || 0
const precision = (value: string, integer: number, decimals: number) => new RegExp(`^\\d{1,${integer}}(?:\\.\\d{1,${decimals}})?$`).test(value)

export default function RecepcionFormDialog({ onCreated, onClose }: { onCreated: (id: number | string) => void; onClose: () => void }) {
  const qc = useQueryClient()
  const notify = useNotify()
  const { can } = useAuth()
  const [idempotencyKey] = useState(() => crypto.randomUUID())
  const [snapshot, setSnapshot] = useState<Snapshot | null>(null)
  const [locked, setLocked] = useState(false)

  const sentQuery = useLookup(PATHS.ordenes, { estado: 'ENVIADA' }, can(PERM.ordenesLeer), null)
  const partialQuery = useLookup(PATHS.ordenes, { estado: 'PARCIALMENTE_RECIBIDA' }, can(PERM.ordenesLeer), null)
  const warehousesQuery = useLookup(PATHS.almacenes, {}, can(PERM.almacenesLeer))
  const productsQuery = useLookup(PATHS.productos, {}, can(PERM.productosLeer))
  const suppliersQuery = useLookup(PATHS.proveedores, {}, can(PERM.proveedoresLeer))
  const products = productsQuery.data ?? []
  const suppliers = suppliersQuery.data ?? []
  const orders = [...(sentQuery.data ?? []), ...(partialQuery.data ?? [])]
  const warehouses = warehousesQuery.data ?? []
  const supplierById = new Map(suppliers.map((supplier) => [String(supplier.id), supplier]))
  const productById = new Map(products.map((product) => [String(product.id), product]))

  const { control, register, handleSubmit, reset } = useForm<Values>({
    defaultValues: { ordenId: '', almacenId: '', referenciaExterna: '', lineas: [] },
  })
  const { fields } = useFieldArray({ control, name: 'lineas' })
  const ordenId = useWatch({ control, name: 'ordenId' })
  const lines = useWatch({ control, name: 'lineas' }) ?? []
  const orderQuery = useQuery({
    queryKey: ['orden', idValue(ordenId || '0')],
    enabled: !!ordenId,
    queryFn: async () => (await http.get(`${PATHS.ordenes}/${ordenId}`)).data,
  })

  useEffect(() => {
    if (!orderQuery.data) return
    reset({
      ordenId,
      almacenId: '',
      referenciaExterna: '',
      lineas: (orderQuery.data.detalles ?? [])
        .filter((detail: Row) => Number(detail.cantidadPendiente) > 0)
        .map((detail: Row) => ({
          detalleId: String(detail.id), productoId: String(detail.productoId), pendiente: Number(detail.cantidadPendiente),
          recibida: String(detail.cantidadPendiente), aceptada: String(detail.cantidadPendiente), rechazada: '0',
          precio: String(detail.precioUnitario), motivo: '',
        })),
    })
  }, [orderQuery.data, ordenId, reset])

  const lineErrors = lines.map((line) => {
    const received = amount(line.recibida)
    const accepted = amount(line.aceptada)
    const rejected = amount(line.rechazada)
    if (!precision(line.recibida, 11, 3) || received <= 0) return 'La cantidad recibida debe ser positiva y tener hasta 3 decimales.'
    if (received > line.pendiente) return `No puede superar el saldo pendiente (${num(line.pendiente)}).`
    if (!precision(line.aceptada, 11, 3) || !precision(line.rechazada, 11, 3)) return 'Aceptado y rechazado admiten hasta 3 decimales.'
    if (Math.round((accepted + rejected) * 1000) !== Math.round(received * 1000)) return 'Aceptado + rechazado debe ser igual a recibido.'
    if (!precision(line.precio, 12, 2)) return 'El precio recibido admite hasta 2 decimales.'
    return ''
  })
  const lookupsFailed = !!sentQuery.error || !!partialQuery.error || !!warehousesQuery.error || !!productsQuery.error
    || !!suppliersQuery.error || !!orderQuery.error
  const valid = !!ordenId && !!lines.length && !lookupsFailed && lines.every((_line, index) => !lineErrors[index]) && !!lines.some((line) => amount(line.recibida) > 0)

  const save = useMutation({
    mutationFn: (body: Snapshot) => http.post(PATHS.recepciones, body),
    onSuccess: (response) => {
      const id = response.data?.id
      qc.invalidateQueries({ queryKey: [PATHS.recepciones] })
      qc.invalidateQueries({ queryKey: [PATHS.ordenes] })
      notify(response.status === 200 ? 'Se recuperó la recepción creada con la misma clave.' : 'Recepción registrada como pendiente de confirmación.')
      if (id != null) onCreated(id)
      else onClose()
    },
    onError: (error) => notify(errorMessage(error), 'error'),
  })

  const makeBody = (values: Values): Snapshot => ({
    ordenCompraId: idValue(values.ordenId),
    almacenId: idValue(values.almacenId),
    claveIdempotencia: idempotencyKey,
    referenciaExterna: values.referenciaExterna.trim() || null,
    detalles: values.lineas.map((line) => ({
      detalleOrdenCompraId: idValue(line.detalleId),
      cantidadRecibida: amount(line.recibida),
      cantidadAceptada: amount(line.aceptada),
      cantidadRechazada: amount(line.rechazada),
      precioUnitarioRecibido: amount(line.precio),
      motivoRechazo: line.motivo.trim() || null,
    })),
  })

  const submit = (values: Values) => {
    if (locked && snapshot) {
      save.mutate(snapshot)
      return
    }
    const body = makeBody(values)
    setSnapshot(body)
    setLocked(true)
    save.mutate(body)
  }

  const optionError = (query: { error: Error | null }) => query.error ? errorMessage(query.error) : undefined

  return (
    <Dialog open onClose={onClose} fullWidth maxWidth="lg">
      <form onSubmit={handleSubmit(submit)} noValidate>
        <DialogTitle>Registrar recepción de compra</DialogTitle>
        <DialogContent>
          <Alert severity="info" sx={{ mb: 2 }}>Se registra como pendiente. El inventario solo cambia al confirmarla; se suman únicamente las unidades aceptadas.</Alert>
          {locked && save.isError && <Alert severity="error" sx={{ mb: 2 }}>
            La solicitud conserva la misma clave y contenido. Reintenta para recuperar un registro si el servidor lo guardó; para corregir los datos, cierra y crea una nueva recepción.
          </Alert>}
          {lookupsFailed && <Alert severity="error" sx={{ mb: 2 }}>No se pudieron cargar las órdenes o catálogos necesarios. Verifica tus permisos de lectura e inténtalo de nuevo.</Alert>}
          <Box sx={{ display: 'grid', gridTemplateColumns: { sm: '1fr 1fr' }, gap: 2, pt: 1 }}>
            <Controller name="ordenId" control={control} rules={{ required: 'Elige una orden enviada o parcialmente recibida.' }}
              render={({ field, fieldState }) => (
                <Autocomplete disabled={locked} size="small" options={orders} loading={sentQuery.isLoading || partialQuery.isLoading}
                  getOptionLabel={(order: Row) => {
                    const supplier = supplierById.get(String(order.proveedorId))
                    return `${order.folio ?? order.id} — ${supplier?.nombreRazonSocial ?? `Proveedor #${order.proveedorId ?? '—'}`}`
                  }}
                  isOptionEqualToValue={(a, b) => a.id === b.id}
                  value={orders.find((order) => String(order.id) === field.value) ?? null}
                  onChange={(_event, value) => { field.onChange(value ? String(value.id) : ''); reset({ ordenId: value ? String(value.id) : '', almacenId: '', referenciaExterna: '', lineas: [] }) }}
                  noOptionsText="No hay órdenes enviadas con saldo pendiente"
                  renderInput={(params) => <TextField {...params} label="Orden de compra" required error={!!fieldState.error || !!sentQuery.error || !!partialQuery.error}
                    helperText={fieldState.error?.message ?? optionError(sentQuery) ?? optionError(partialQuery)} />}
                />
              )} />
            <Controller name="almacenId" control={control} rules={{ required: 'Elige un almacén activo.' }}
              render={({ field, fieldState }) => (
                <Autocomplete disabled={locked} size="small" options={warehouses} loading={warehousesQuery.isLoading}
                  getOptionLabel={(warehouse: Row) => `${warehouse.codigo ?? ''} — ${warehouse.nombre ?? ''}`}
                  isOptionEqualToValue={(a, b) => a.id === b.id}
                  value={warehouses.find((warehouse) => String(warehouse.id) === field.value) ?? null}
                  onChange={(_event, value) => field.onChange(value ? String(value.id) : '')}
                  renderInput={(params) => <TextField {...params} label="Almacén" required error={!!fieldState.error || !!warehousesQuery.error}
                    helperText={fieldState.error?.message ?? optionError(warehousesQuery)} />}
                />
              )} />
            <TextField disabled={locked} label="Referencia externa" size="small" slotProps={{ htmlInput: { maxLength: 80 } }}
              {...register('referenciaExterna')} />
          </Box>

          {orderQuery.isLoading && ordenId && <Typography sx={{ mt: 2 }}>Cargando detalle de la orden…</Typography>}
          {orderQuery.error && <Alert severity="error" sx={{ mt: 2 }}>{errorMessage(orderQuery.error)}</Alert>}
          {!!ordenId && !orderQuery.isLoading && !orderQuery.error && !fields.length && <Alert severity="info" sx={{ mt: 2 }}>La orden no tiene cantidades pendientes por recibir.</Alert>}
          {!!fields.length && <Box sx={{ overflowX: 'auto', mt: 2 }}>
            <Table size="small">
              <TableHead><TableRow>
                {['Producto', 'Saldo pendiente', 'Recibido', 'Aceptado', 'Rechazado', 'Precio recibido', 'Motivo de rechazo'].map((heading) =>
                  <TableCell key={heading}>{heading}</TableCell>)}
              </TableRow></TableHead>
              <TableBody>{fields.map((field, index) => {
                const product = productById.get(field.productoId)
                return <TableRow key={field.id}>
                  <TableCell>{product ? `${product.sku} — ${product.nombre}` : `Producto #${field.productoId}`}</TableCell>
                  <TableCell>{num(field.pendiente)}</TableCell>
                  {(['recibida', 'aceptada', 'rechazada', 'precio'] as const).map((name) => (
                    <TableCell key={name}>
                      <TextField disabled={locked} size="small" type="number"
                        slotProps={{ htmlInput: { 'aria-label': `${name}-${index}`, min: 0, step: name === 'precio' ? '0.01' : '0.001' } }}
                        error={!!lineErrors[index]} helperText={name === 'recibida' ? lineErrors[index] : undefined}
                        {...register(`lineas.${index}.${name}`)} />
                    </TableCell>
                  ))}
                  <TableCell><TextField disabled={locked} size="small" slotProps={{ htmlInput: { 'aria-label': `motivo-${index}`, maxLength: 500 } }}
                    {...register(`lineas.${index}.motivo`)} /></TableCell>
                </TableRow>
              })}</TableBody>
            </Table>
          </Box>}
        </DialogContent>
        <DialogActions sx={{ px: 3, pb: 2 }}>
          <Button onClick={onClose}>Cerrar</Button>
          <Button type="submit" variant="contained" disabled={save.isPending || (!locked && !valid)}>
            {save.isPending ? 'Guardando…' : locked ? 'Reintentar con la misma clave' : 'Guardar recepción pendiente'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  )
}
