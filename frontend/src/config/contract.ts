/**
 * CONTRATO CON EL BACKEND.
 * Login/logout/me siguen el diagrama 08 (implementado). Proveedores, productos,
 * órdenes y recepciones son "objetivo" en los diagramas: sus rutas y campos
 * son una propuesta; ajústalos aquí cuando existan los controllers.
 */
export const API = '/api/v1'

export const PATHS = {
  login: `${API}/auth/login`,
  logout: `${API}/auth/logout`,
  me: `${API}/auth/me`,
  changePassword: `${API}/auth/password`,
  proveedores: `${API}/proveedores`,
  productos: `${API}/productos`,
  categorias: `${API}/categorias-producto`,
  ordenes: `${API}/ordenes-compra`,
  recepciones: `${API}/recepciones`,
  almacenes: `${API}/almacenes`,
  existencias: `${API}/existencias`,
  movimientos: `${API}/movimientos-inventario`,
  ajustes: `${API}/ajustes-inventario`,
  conteos: `${API}/conteos-inventario`,
  alertas: `${API}/alertas-stock`,
  rotacion: `${API}/reportes/rotacion`,
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
