import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, MenuItem, Paper, TextField, Typography } from '@mui/material'
import { http, errorMessage } from '../../api/http'
import { PATHS, PERM } from '../../config/contract'
import type { Row } from '../../shared/api'
import { useNotify } from '../../shared/Notify'
import { useAuth } from '../auth/AuthContext'
import { accountLabel, allPages } from './accountingApi'

type Field = { key: string; label: string }
type EditorProps = { title: string; path: string; readPermission: string; writePermission: string; fields: Field[]; read(): Promise<Row>; encode(data: Row): Record<string, unknown> }

const salesFields = [
  { key: 'clientesPorCobrarCuentaId', label: 'Clientes por cobrar' },
  { key: 'ventasCuentaId', label: 'Ventas' },
  { key: 'impuestosTrasladadosCuentaId', label: 'Impuestos trasladados' },
]
const inventoryFields = [
  { key: 'inventarioCuentaId', label: 'Inventario' },
  { key: 'mercanciaRecibidaNoFacturadaCuentaId', label: 'Mercancía recibida no facturada' },
  { key: 'costoDeVentasCuentaId', label: 'Costo de ventas' },
  { key: 'ajustePositivoCuentaId', label: 'Ganancia por ajuste' },
  { key: 'ajusteNegativoCuentaId', label: 'Pérdida por ajuste' },
]
const supplierInvoiceFields = [
  { key: 'ivaAcreditableCuentaId', label: 'IVA acreditable' },
  { key: 'cuentasPorPagarCuentaId', label: 'Cuentas por pagar' },
]
const paymentFields = [{ key: 'cuentaFondosId', label: 'Cuenta de fondos para pagos' }]
const byConcept = (rows: Row[], concepts: Record<string, string>) => Object.fromEntries(Object.entries(concepts).map(([concept, field]) => [field, rows.find((row) => row.concepto === concept)?.cuentaId ?? '']))

export default function ConfiguracionContableTab() {
  const { can } = useAuth()
  const [medium, setMedium] = useState('TRANSFERENCIA')
  const showSales = can(PERM.integracionesAdmin)
  const showInventory = can(PERM.inventarioCuentasLeer) || can(PERM.inventarioCuentasConfigurar)
  const showSupplierInvoice = can(PERM.cxpLeer) || can(PERM.cxpConfigurar)
  const showCustomerReceipt = can(PERM.cobrosConfigurar)
  const showSupplierPayment = can(PERM.pagosProveedorConfigurar)
  const showCashflow = can(PERM.flujoClasificar)
  return <Box sx={{ display: 'grid', gap: 2 }}>
    <Alert severity="info">Las validaciones de tipo, naturaleza, actividad y compatibilidad de cuentas las aplica el backend. Configurar aquí no contabiliza operaciones históricas.</Alert>
    {showSales && <MappingEditor title="Integración de facturas de venta" path={PATHS.mapeoCuentasVenta} readPermission={PERM.integracionesAdmin} writePermission={PERM.integracionesAdmin} fields={salesFields} read={async () => (await http.get(PATHS.mapeoCuentasVenta)).data} encode={(data) => byConcept(data.cuentas ?? [], { CLIENTES_POR_COBRAR: 'clientesPorCobrarCuentaId', VENTAS: 'ventasCuentaId', IMPUESTOS_TRASLADADOS: 'impuestosTrasladadosCuentaId' })}/>}
    {showInventory && <MappingEditor title="Integración de inventario" path={PATHS.mapeoCuentasInventario} readPermission={PERM.inventarioCuentasLeer} writePermission={PERM.inventarioCuentasConfigurar} fields={inventoryFields} read={async () => (await http.get(PATHS.mapeoCuentasInventario)).data} encode={(data) => byConcept(data.cuentas ?? [], { INVENTARIO: 'inventarioCuentaId', MERCANCIA_RECIBIDA_NO_FACTURADA: 'mercanciaRecibidaNoFacturadaCuentaId', COSTO_DE_VENTAS: 'costoDeVentasCuentaId', AJUSTE_INVENTARIO_POSITIVO: 'ajustePositivoCuentaId', AJUSTE_INVENTARIO_NEGATIVO: 'ajusteNegativoCuentaId' })}/>}
    {showSupplierInvoice && <MappingEditor title="Factura de proveedor" path={PATHS.mapeoFacturaProveedor} readPermission={PERM.cxpLeer} writePermission={PERM.cxpConfigurar} fields={supplierInvoiceFields} read={async () => (await http.get(PATHS.mapeoFacturaProveedor)).data} encode={(data) => ({ ivaAcreditableCuentaId: data.ivaAcreditableCuentaId ?? '', cuentasPorPagarCuentaId: data.cuentasPorPagarCuentaId ?? '' })}/>}
    {showSupplierPayment && <MappingEditor title="Pagos a proveedores" path={PATHS.fondosPagosProveedor} readPermission={PERM.pagosProveedorConfigurar} writePermission={PERM.pagosProveedorConfigurar} fields={paymentFields} read={async () => (await http.get(PATHS.fondosPagosProveedor)).data} encode={(data) => ({ cuentaFondosId: data.cuentaFondosId ?? '' })}/>}
    {showCustomerReceipt && <Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h6" sx={{ mb: 2 }}>Cobros de clientes</Typography><TextField select size="small" label="Medio de cobro" value={medium} onChange={(event) => setMedium(event.target.value)} sx={{ mb: 2 }}>{['EFECTIVO','TRANSFERENCIA','CHEQUE','TARJETA','OTRO'].map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField><ReceiptAccounts medium={medium}/></Paper>}
    {showCashflow && <CashflowClassification/>}
    {!showSales && !showInventory && !showSupplierInvoice && !showCustomerReceipt && !showSupplierPayment && !showCashflow && <Alert severity="info">No tienes permisos para consultar o configurar mapeos contables.</Alert>}
  </Box>
}

function MappingEditor({ title, path, readPermission, writePermission, fields, read, encode }: EditorProps) {
  const { can } = useAuth(); const qc = useQueryClient(); const notify = useNotify()
  const canRead = can(readPermission); const canWrite = can(writePermission)
  const config = useQuery({ queryKey: [path, 'config'], enabled: canRead, queryFn: read })
  const accounts = useQuery({ queryKey: [PATHS.cuentasContables, 'mapping-options'], enabled: can(PERM.contabilidadLeer), queryFn: () => allPages(PATHS.cuentasContables, { sort: 'codigo', direction: 'asc' }) })
  const [draft, setDraft] = useState<{ updatedAt: number; values: Record<string, string> }>({ updatedAt: 0, values: {} })
  const initialValues = config.data ? Object.fromEntries(Object.entries(encode(config.data)).map(([key, value]) => [key, value == null ? '' : String(value)])) : {}
  const values = draft.updatedAt === config.dataUpdatedAt ? { ...initialValues, ...draft.values } : initialValues
  const save = useMutation({ mutationFn: (body: Record<string, unknown>) => http.put(path, body), onSuccess: async () => { await qc.invalidateQueries({ queryKey: [path, 'config'] }); notify(`${title}: configuración guardada.`) }, onError: (error) => notify(errorMessage(error), 'error') })
  return <Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h6" sx={{ mb: 2 }}>{title}</Typography>{config.error && <Alert severity="error" sx={{ mb: 2 }}>{errorMessage(config.error)}</Alert>}{accounts.error && <Alert severity="warning" sx={{ mb: 2 }}>No se cargó el plan de cuentas. Puedes capturar los IDs directamente. {errorMessage(accounts.error)}</Alert>}<Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { md: '1fr 1fr' } }}>{fields.map((field) => <TextField key={field.key} select={!!accounts.data?.length} label={field.label} value={values[field.key] ?? ''} onChange={(event) => setDraft({ updatedAt: config.dataUpdatedAt, values: { ...values, [field.key]: event.target.value } })} disabled={!canWrite} slotProps={{ htmlInput: { inputMode: 'numeric' }}}>{accounts.data?.map((account) => <MenuItem key={account.id} value={account.id}>{accountLabel(account)}</MenuItem>)}</TextField>)}</Box>{canWrite && <Button sx={{ mt: 2 }} variant="contained" disabled={save.isPending || fields.some((field) => !values[field.key])} onClick={() => save.mutate(Object.fromEntries(fields.map((field) => [field.key, Number(values[field.key])])))}>Guardar configuración</Button>}</Paper>
}

function ReceiptAccounts({ medium }: { medium: string }) {
  const path = PATHS.cuentasCobro; const { can } = useAuth(); const qc = useQueryClient(); const notify = useNotify()
  const config = useQuery({ queryKey: [path, 'config'], enabled: can(PERM.cobrosConfigurar), queryFn: async () => (await http.get(path)).data })
  const accounts = useQuery({ queryKey: [PATHS.cuentasContables, 'mapping-options'], enabled: can(PERM.contabilidadLeer), queryFn: () => allPages(PATHS.cuentasContables, { sort: 'codigo', direction: 'asc' }) })
  const current = config.data?.medios?.find((item: Row) => item.medio === medium)
  const [draft, setDraft] = useState<{ medium: string; updatedAt: number; funds: string; advances: string } | null>(null)
  const draftActive = draft?.medium === medium && draft.updatedAt === config.dataUpdatedAt
  const funds = draftActive ? draft.funds : current?.cuentaFondosId ? String(current.cuentaFondosId) : ''
  const advances = draftActive ? draft.advances : current?.cuentaAnticiposId ? String(current.cuentaAnticiposId) : ''
  const save = useMutation({ mutationFn: () => http.put(path, { medio: medium, cuentaFondosId: Number(funds), cuentaAnticiposId: Number(advances) }), onSuccess: async () => { await qc.invalidateQueries({ queryKey: [path, 'config'] }); notify(`Configuración de ${medium} guardada.`) }, onError: (error) => notify(errorMessage(error), 'error') })
  if (config.error) return <Alert severity="error">{errorMessage(config.error)}</Alert>
  return <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { md: '1fr 1fr' } }}>{[{ label: 'Fondos (activo)', value: funds, key: 'funds' as const }, { label: 'Anticipos de clientes (pasivo)', value: advances, key: 'advances' as const }].map((field) => <TextField key={field.label} select={!!accounts.data?.length} label={field.label} value={field.value} onChange={(event) => setDraft({ medium, updatedAt: config.dataUpdatedAt, funds: field.key === 'funds' ? event.target.value : funds, advances: field.key === 'advances' ? event.target.value : advances })} slotProps={{ htmlInput: { inputMode: 'numeric' }}}>{accounts.data?.map((account) => <MenuItem key={account.id} value={account.id}>{accountLabel(account)}</MenuItem>)}</TextField>)}<Button variant="contained" disabled={save.isPending || !funds || !advances} onClick={() => save.mutate()}>Guardar medio</Button></Box>
}

function CashflowClassification() {
  const { can } = useAuth(); const notify = useNotify()
  const [mode, setMode] = useState<'cuenta'|'partida'>('cuenta'); const [account, setAccount] = useState(''); const [line, setLine] = useState(''); const [classification, setClassification] = useState('OPERACION'); const [from, setFrom] = useState(''); const [to, setTo] = useState(''); const [createdId, setCreatedId] = useState(''); const [reason, setReason] = useState('')
  const accounts = useQuery({ queryKey: [PATHS.cuentasContables, 'cashflow-classification'], enabled: can(PERM.contabilidadLeer), queryFn: () => allPages(PATHS.cuentasContables, { sort: 'codigo', direction: 'asc' }) })
  const classificationPath = mode === 'cuenta' ? PATHS.clasificacionesFlujoCuenta : PATHS.clasificacionesFlujoPartida
  const create = useMutation({ mutationFn: async () => (await http.post(classificationPath, mode === 'cuenta' ? { cuentaId: Number(account), clasificacion: classification, desde: from, hasta: to } : { partidaId: Number(line), clasificacion: classification })).data, onSuccess: (result) => { setCreatedId(String(result.id)); notify(`Clasificación creada con ID ${result.id}.`) }, onError: (error) => notify(errorMessage(error), 'error') })
  const voidMutation = useMutation({ mutationFn: () => http.post(`${classificationPath}/${createdId}/void`, { motivo: reason }), onSuccess: () => { notify('Clasificación anulada con motivo.'); setCreatedId(''); setReason('') }, onError: (error) => notify(errorMessage(error), 'error') })
  const accountTypes = ['EFECTIVO','CAPITAL_TRABAJO','INVERSION','FINANCIACION','NO_MONETARIO','RESULTADO']
  const lineTypes = ['OPERACION','INVERSION','FINANCIACION','NO_MONETARIO']
  const valid = mode === 'cuenta' ? !!account && !!from && !!to && from <= to : /^\d+$/.test(line)
  return <Paper variant="outlined" sx={{ p: 2 }}><Typography variant="h6" sx={{ mb: 2 }}>Clasificación de flujo de efectivo</Typography><Alert severity="info" sx={{ mb: 2 }}>La API no ofrece listado de clasificaciones. El ID creado se conserva aquí para permitir su anulación durante esta sesión; no se infieren clasificaciones históricas.</Alert><Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { md: 'repeat(2, 1fr)' }}}><TextField select label="Objeto" value={mode} disabled={!!createdId} onChange={(event) => { const next = event.target.value as 'cuenta'|'partida'; setMode(next); setClassification(next === 'cuenta' ? 'EFECTIVO' : 'OPERACION') }}><MenuItem value="cuenta">Cuenta y vigencia</MenuItem><MenuItem value="partida">Partida de asiento</MenuItem></TextField>{mode === 'cuenta' ? accounts.data?.length ? <TextField select label="Cuenta" value={account} onChange={(event) => setAccount(event.target.value)}>{accounts.data.filter((item) => item.aceptaMovimientos).map((item) => <MenuItem key={item.id} value={item.id}>{accountLabel(item)}</MenuItem>)}</TextField> : <TextField label="ID de cuenta" value={account} onChange={(event) => setAccount(event.target.value)} slotProps={{ htmlInput: { inputMode: 'numeric' }}}/> : <TextField label="ID de partida" value={line} onChange={(event) => setLine(event.target.value)} slotProps={{ htmlInput: { inputMode: 'numeric' }}}/>}<TextField select label="Clasificación" value={classification} onChange={(event) => setClassification(event.target.value)}>{(mode === 'cuenta' ? accountTypes : lineTypes).map((value) => <MenuItem key={value} value={value}>{value}</MenuItem>)}</TextField>{mode === 'cuenta' && <><TextField label="Vigente desde" type="date" value={from} onChange={(event) => setFrom(event.target.value)} slotProps={{ inputLabel: { shrink: true }}}/><TextField label="Vigente hasta" type="date" value={to} onChange={(event) => setTo(event.target.value)} slotProps={{ inputLabel: { shrink: true }}}/></>}</Box><Button sx={{ mt: 2 }} variant="contained" disabled={!valid || create.isPending} onClick={() => create.mutate()}>Registrar clasificación</Button>{createdId && <Box sx={{ mt: 2, display: 'grid', gap: 2 }}><Typography>Clasificación registrada · ID {createdId}</Typography><TextField label="Motivo para anular" value={reason} onChange={(event) => setReason(event.target.value)} slotProps={{ htmlInput: { maxLength: 300 }}}/><Button color="error" disabled={!reason.trim() || voidMutation.isPending} onClick={() => voidMutation.mutate()}>Anular clasificación</Button></Box>}</Paper>
}
