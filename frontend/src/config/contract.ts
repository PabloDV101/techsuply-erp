/**
 * Rutas base del backend y permisos que se usan para presentar la navegación.
 * La existencia de una ruta no implica que una pantalla esté integrada: cada
 * módulo se habilita después de conciliar también sus métodos y DTO con OpenAPI.
 */
export const API = '/api/v1'

export const PATHS = {
  login: `${API}/auth/login`,
  register: `${API}/auth/register`,
  logout: `${API}/auth/logout`,
  me: `${API}/auth/me`,
  changePassword: `${API}/auth/password`,
  localAccountRecovery: `${API}/auth/local-recovery`,
  departamentos: `${API}/departments`,
  proveedores: `${API}/suppliers`,
  almacenes: `${API}/warehouses`,
  productos: `${API}/products`,
  categorias: `${API}/categories`,
  ordenes: `${API}/purchase-orders`,
  recepciones: `${API}/purchase-receipts`,
  existencias: `${API}/inventory/stock`,
  movimientos: `${API}/inventory/movements`,
  ajustes: `${API}/inventory/adjustments`,
  conteos: `${API}/inventory/counts`,
  alertas: `${API}/inventory/low-stock-alerts`,
  alertasResumen: `${API}/reports/inventory/low-stock-summary`,
  dashboard: `${API}/reports/dashboard`,
  dashboardExportCsv: `${API}/reports/dashboard/export.csv`,
  reportExport: `${API}/reports/export`,
  facturadoCompras: `${API}/reports/purchases/invoiced`,
  facturadoVentas: `${API}/reports/sales/invoiced`,
  minimosBajos: `${API}/reports/inventory/low-stock`,
  productosSinMovimiento: `${API}/reports/inventory/inactive-products`,
  abcInventario: `${API}/reports/inventory/abc`,
  rentabilidadClientes: `${API}/reports/sales/customer-profitability`,
  margenOperativo: `${API}/reports/finance/operating-margin`,
  rotacion: `${API}/reports/inventory/turnover`,
  clientes: `${API}/customers`,
  cotizaciones: `${API}/sales/quotes`,
  pedidos: `${API}/sales/orders`,
  despachos: `${API}/sales/dispatches`,
  facturas: `${API}/sales/invoices`,
  // Conservado para el módulo de crédito aún no integrado con el flujo activo de Ventas.
  credito: `${API}/credito`,
  cuentasContables: `${API}/accounting/accounts`,
  periodosContables: `${API}/accounting/periods`,
  asientosContables: `${API}/accounting/entries`,
  balancePrueba: `${API}/reports/finance/trial-balance`,
  estadoResultados: `${API}/reports/finance/income-statement`,
  balanceGeneral: `${API}/reports/finance/balance-sheet`,
  flujoEfectivo: `${API}/reports/finance/cash-flow`,
  resultadoFinanciero: `${API}/reports/finance/result-summary`,
  integracionesFacturasVenta: `${API}/accounting/integrations/sales-invoices`,
  cuentasPorCobrar: `${API}/accounting/accounts-receivable`,
  cobrosClientes: `${API}/accounting/customer-receipts`,
  cuentasCobro: `${API}/accounting/customer-receipts/accounts`,
  mapeoCuentasVenta: `${API}/accounting/integrations/sales-invoices/accounts`,
  mapeoCuentasInventario: `${API}/accounting/inventory-account-mappings`,
  facturasProveedor: `${API}/supplier-invoices`,
  cuentasPorPagar: `${API}/accounting/payables`,
  mapeoFacturaProveedor: `${API}/accounting/supplier-invoice-accounts`,
  pagosProveedor: `${API}/accounting/supplier-payments`,
  fondosPagosProveedor: `${API}/accounting/supplier-payments/accounts`,
  clasificacionesFlujoCuenta: `${API}/accounting/cash-flow-classifications/accounts`,
  clasificacionesFlujoPartida: `${API}/accounting/cash-flow-classifications/lines`,
  adminUsers: `${API}/admin/users`,
  adminRoles: `${API}/admin/roles`,
  adminPermissions: `${API}/admin/permissions`,
  auditEvents: `${API}/audit/events`,
  notifications: `${API}/notifications`,
}

/** Nombre del parámetro de búsqueda de texto en los listados. */
export const SEARCH_PARAM = 'search'

/** Autoridades vigentes del backend; no derivar permisos a partir del rol. */
export const PERM = {
  departamentosLeer: 'MAESTROS_DEPARTAMENTOS_LEER',
  departamentoAdmin: 'MAESTROS_DEPARTAMENTOS_ADMIN',
  proveedoresLeer: 'MAESTROS_PROVEEDORES_LEER',
  proveedorWrite: 'MAESTROS_PROVEEDORES_ADMIN',
  almacenesLeer: 'MAESTROS_ALMACENES_LEER',
  almacenAdmin: 'MAESTROS_ALMACENES_ADMIN',
  productosLeer: 'MAESTROS_PRODUCTOS_LEER',
  productoWrite: 'MAESTROS_PRODUCTOS_ADMIN',
  categoriasLeer: 'MAESTROS_CATEGORIAS_LEER',
  categoriaAdmin: 'MAESTROS_CATEGORIAS_ADMIN',
  ordenesLeer: 'COMPRAS_ORDENES_LEER',
  ocWrite: 'COMPRAS_ORDENES_ADMIN',
  ocApprove: 'COMPRAS_ORDENES_APROBAR',
  recepcionesLeer: 'COMPRAS_RECEPCIONES_LEER',
  recepcionWrite: 'COMPRAS_RECEPCIONES_REGISTRAR',
  recepcionConfirm: 'COMPRAS_RECEPCIONES_CONFIRMAR',
  existenciasLeer: 'INVENTARIO_EXISTENCIAS_LEER',
  movimientosLeer: 'INVENTARIO_MOVIMIENTOS_LEER',
  ajustesLeer: 'INVENTARIO_AJUSTES_LEER',
  inventarioAjustar: 'INVENTARIO_AJUSTES_REGISTRAR',
  ajustesAprobar: 'INVENTARIO_AJUSTES_APROBAR',
  ajustesAplicar: 'INVENTARIO_AJUSTES_APLICAR',
  conteosLeer: 'INVENTARIO_CONTEOS_LEER',
  inventarioContar: 'INVENTARIO_CONTEOS_ADMIN',
  alertasLeer: 'INVENTARIO_ALERTAS_LEER',
  clientesLeer: 'MAESTROS_CLIENTES_LEER',
  clienteWrite: 'MAESTROS_CLIENTES_ADMIN',
  clientesCreditoAdmin: 'MAESTROS_CLIENTES_CREDITO_ADMIN',
  cotizacionesLeer: 'VENTAS_COTIZACIONES_LEER',
  cotizacionWrite: 'VENTAS_COTIZACIONES_REGISTRAR',
  cotizacionAdmin: 'VENTAS_COTIZACIONES_ADMIN',
  pedidosLeer: 'VENTAS_PEDIDOS_LEER',
  pedidoWrite: 'VENTAS_PEDIDOS_REGISTRAR',
  pedidoConfirm: 'VENTAS_PEDIDOS_CONFIRMAR',
  pedidoCancel: 'VENTAS_PEDIDOS_CANCELAR',
  despachoLeer: 'VENTAS_DESPACHOS_LEER',
  despachoWrite: 'VENTAS_DESPACHOS_REGISTRAR',
  despachoConfirm: 'VENTAS_DESPACHOS_CONFIRMAR',
  facturasLeer: 'VENTAS_FACTURAS_LEER',
  facturaEmitir: 'VENTAS_FACTURAS_EMITIR',
  contabilidadLeer: 'CONTABILIDAD_CUENTAS_LEER',
  reportesContablesLeer: 'CONTABILIDAD_REPORTES_LEER',
  cuentaAdmin: 'CONTABILIDAD_CUENTAS_ADMIN',
  periodosLeer: 'CONTABILIDAD_PERIODOS_LEER',
  periodoAdmin: 'CONTABILIDAD_PERIODOS_ADMIN',
  periodoCerrar: 'CONTABILIDAD_PERIODOS_CERRAR',
  asientosLeer: 'CONTABILIDAD_ASIENTOS_LEER',
  asientosRegistrar: 'CONTABILIDAD_ASIENTOS_REGISTRAR',
  asientosContabilizar: 'CONTABILIDAD_ASIENTOS_CONTABILIZAR',
  asientosRevertir: 'CONTABILIDAD_ASIENTOS_REVERTIR',
  integracionesLeer: 'CONTABILIDAD_INTEGRACIONES_LEER',
  integracionesAdmin: 'CONTABILIDAD_INTEGRACIONES_ADMIN',
  integracionesReintentar: 'CONTABILIDAD_INTEGRACIONES_REINTENTAR',
  cxcLeer: 'CONTABILIDAD_CXC_LEER',
  cobrosRegistrar: 'CONTABILIDAD_COBROS_REGISTRAR',
  cobrosLeer: 'CONTABILIDAD_COBROS_LEER',
  cobrosConfirmar: 'CONTABILIDAD_COBROS_CONFIRMAR',
  cobrosConfigurar: 'CONTABILIDAD_COBROS_CONFIGURAR',
  inventarioCuentasLeer: 'CONTABILIDAD_INVENTARIO_LEER',
  inventarioCuentasConfigurar: 'CONTABILIDAD_INVENTARIO_CONFIGURAR',
  cxpLeer: 'CONTABILIDAD_CXP_LEER',
  cxpConfigurar: 'CONTABILIDAD_CXP_CONFIGURAR',
  facturaProveedorLeer: 'COMPRAS_FACTURAS_PROVEEDOR_LEER',
  facturaProveedorRegistrar: 'COMPRAS_FACTURAS_PROVEEDOR_REGISTRAR',
  facturaProveedorValidar: 'COMPRAS_FACTURAS_PROVEEDOR_VALIDAR',
  pagosProveedorRegistrar: 'CONTABILIDAD_PAGOS_PROVEEDOR_REGISTRAR',
  pagosProveedorLeer: 'CONTABILIDAD_PAGOS_PROVEEDOR_LEER',
  pagosProveedorConfirmar: 'CONTABILIDAD_PAGOS_PROVEEDOR_CONFIRMAR',
  pagosProveedorConfigurar: 'CONTABILIDAD_PAGOS_PROVEEDOR_CONFIGURAR',
  flujoClasificar: 'CONTABILIDAD_FLUJO_CLASIFICAR',
  reportesInventarioLeer: 'REPORTERIA_INVENTARIO_LEER',
  reportesMinimosConfigurar: 'REPORTERIA_MINIMOS_CONFIGURAR',
  seguridadUsuariosAdmin: 'SEGURIDAD_USUARIOS_ADMIN',
  seguridadRolesAdmin: 'SEGURIDAD_ROLES_ADMIN',
  seguridadPermisosAdmin: 'SEGURIDAD_PERMISOS_ADMIN',
  seguridadAuditoriaLeer: 'SEGURIDAD_AUDITORIA_LEER',
}

export interface SessionUser {
  id: number
  identificador: string
  nombre: string
  roles: string[]
  permisos: string[]
}

export const buildLoginBody = (identificador: string, password: string) => ({ identificador, password })

type AuthorityValue = string | { codigo?: string; nombre?: string; authority?: string }
interface BackendSessionUser {
  id?: number
  identificador?: string
  username?: string
  email?: string
  nombreCompleto?: string
  nombre?: string
  correo?: string
  roles?: AuthorityValue[]
  permisos?: AuthorityValue[]
  authorities?: AuthorityValue[]
}

const names = (list: AuthorityValue[] | undefined, keys: string[]): string[] =>
  (Array.isArray(list) ? list : []).map((x) => {
    if (typeof x === 'string') return x
    for (const k of keys) {
      const value = x[k as keyof typeof x]
      if (value) return String(value)
    }
    return ''
  }).filter(Boolean).map((s) => s.replace(/^ROLE_/, ''))

/** Traduce UsuarioResponse (login.usuario o /auth/me) a SessionUser. */
export function mapUser(u: BackendSessionUser): SessionUser {
  return {
    id: u?.id ?? 0,
    identificador: u?.identificador ?? u?.username ?? u?.email ?? '',
    nombre: u?.nombreCompleto ?? u?.nombre ?? u?.username ?? u?.identificador ?? u?.correo ?? '',
    roles: names(u?.roles, ['codigo', 'nombre', 'authority']),
    permisos: names(u?.permisos ?? u?.authorities, ['codigo', 'nombre', 'authority']),
  }
}
