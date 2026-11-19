/**
 * CONTRATO CON EL BACKEND.
 * Login/logout/me y categorías tienen endpoints implementados. Los demás módulos
 * operativos siguen pendientes en el backend; no tratar sus rutas como activas.
 */
export const API = '/api/v1'

export const PATHS = {
  login: `${API}/auth/login`,
  register: `${API}/auth/register`,
  logout: `${API}/auth/logout`,
  me: `${API}/auth/me`,
  changePassword: `${API}/auth/password`,
  localAccountRecovery: `${API}/auth/local-recovery`,
  proveedores: `${API}/proveedores`,
  productos: `${API}/productos`,
  categorias: `${API}/categories`,
  ordenes: `${API}/ordenes-compra`,
  recepciones: `${API}/recepciones`,
  almacenes: `${API}/almacenes`,
  existencias: `${API}/existencias`,
  movimientos: `${API}/movimientos-inventario`,
  ajustes: `${API}/ajustes-inventario`,
  conteos: `${API}/conteos-inventario`,
  alertas: `${API}/alertas-stock`,
  rotacion: `${API}/reportes/rotacion`,
  clientes: `${API}/clientes`,
  cotizaciones: `${API}/cotizaciones`,
  pedidos: `${API}/pedidos-venta`,
  despachos: `${API}/despachos`,
  facturas: `${API}/facturas`,
  credito: `${API}/credito`,
  cuentasContables: `${API}/cuentas-contables`,
  periodosContables: `${API}/periodos-contables`,
  asientosContables: `${API}/asientos-contables`,
  balancePrueba: `${API}/reportes/contabilidad/balance-prueba`,
  mayorContable: `${API}/reportes/contabilidad/mayor`,
  estadoResultados: `${API}/reportes/contabilidad/estado-resultados`,
  balanceGeneral: `${API}/reportes/contabilidad/balance-general`,
  flujoEfectivo: `${API}/reportes/contabilidad/flujo-efectivo`,
}

/** Nombre del parámetro de búsqueda de texto en los listados. */
export const SEARCH_PARAM = 'search'

/** Permisos. Cambia los textos por los que defina tu tabla de permisos. */
export const PERM = {
  proveedorWrite: 'PROVEEDOR_WRITE',
  productoWrite: 'PRODUCTO_WRITE',
  ocWrite: 'OC_WRITE',
  ocApprove: 'OC_APPROVE',
  recepcionWrite: 'RECEPCION_WRITE',
  recepcionConfirm: 'RECEPCION_CONFIRM',
  inventarioMover: 'INVENTARIO_MOVE',
  inventarioAjustar: 'INVENTARIO_ADJUST',
  inventarioContar: 'INVENTARIO_COUNT',
  inventarioAdmin: 'INVENTARIO_ADMIN',
  clienteWrite: 'CLIENTE_WRITE',
  cotizacionWrite: 'COTIZACION_WRITE',
  pedidoWrite: 'PEDIDO_WRITE',
  pedidoConfirm: 'PEDIDO_CONFIRM',
  despachoWrite: 'DESPACHO_WRITE',
  despachoConfirm: 'DESPACHO_CONFIRM',
  facturaCobrar: 'FACTURA_COBRAR',
  facturaAnular: 'FACTURA_ANULAR',
  creditoAdmin: 'CREDITO_ADMIN',
}

/** Roles que lo pueden todo sin revisar permisos individuales. */
export const SUPER_ROLES = ['ADMIN']

export interface SessionUser {
  identificador: string
  nombre: string
  roles: string[]
  permisos: string[]
}

export const buildLoginBody = (identificador: string, password: string) => ({ identificador, password })

const names = (list: unknown, keys: string[]): string[] =>
  (Array.isArray(list) ? list : []).map((x: any) => {
    if (typeof x === 'string') return x
    for (const k of keys) if (x?.[k]) return String(x[k])
    return ''
  }).filter(Boolean).map((s) => s.replace(/^ROLE_/, ''))

/** Traduce UsuarioResponse (login.usuario o /auth/me) a SessionUser. */
export function mapUser(u: any): SessionUser {
  return {
    identificador: u?.identificador ?? u?.username ?? u?.email ?? '',
    nombre: u?.nombreCompleto ?? u?.nombre ?? u?.identificador ?? u?.email ?? '',
    roles: names(u?.roles, ['codigo', 'nombre', 'authority']),
    permisos: names(u?.permisos ?? u?.authorities, ['codigo', 'nombre', 'authority']),
  }
}
