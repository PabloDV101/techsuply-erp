import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './features/auth/LoginPage'
import ProtectedRoute from './features/auth/ProtectedRoute'
import AppLayout from './layout/AppLayout'
import DashboardPage from './features/dashboard/DashboardPage'
import PermissionGate from './features/auth/PermissionGate'
import { PERM } from './config/contract'
import CategoriasPage from './features/categorias/CategoriasPage'
import DepartamentosPage from './features/departamentos/DepartamentosPage'
import AlmacenesPage from './features/almacenes/AlmacenesPage'
import ProveedoresPage from './features/proveedores/ProveedoresPage'
import ProductosPage from './features/productos/ProductosPage'
import OrdenesPage from './features/compras/ordenes/OrdenesPage'
import RecepcionesPage from './features/compras/recepciones/RecepcionesPage'
import ExistenciasPage from './features/inventario/existencias/ExistenciasPage'
import MovimientosPage from './features/inventario/movimientos/MovimientosPage'
import AjustesPage from './features/inventario/ajustes/AjustesPage'
import ConteosPage from './features/inventario/conteos/ConteosPage'
import AlertasPage from './features/inventario/alertas/AlertasPage'
import RotacionPage from './features/inventario/reportes/RotacionPage'
import ClientesPage from './features/clientes/ClientesPage'
import CotizacionesPage from './features/ventas/documentos/CotizacionesPage'
import PedidosPage from './features/ventas/documentos/PedidosPage'
import DespachosPage from './features/ventas/despachos/DespachosPage'
import FacturasPage from './features/ventas/facturas/FacturasPage'
import ContabilidadPage from './features/contabilidad/ContabilidadPage'
import ReportesPage from './features/reporteria/ReportesPage'
import SeguridadPage from './features/seguridad/SeguridadPage'
import AuditoriaPage from './features/seguridad/AuditoriaPage'
import NotificacionesPage from './features/notificaciones/NotificacionesPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/registro" element={<Navigate to="/login" replace />} />
      <Route path="/recuperar-cuenta" element={<Navigate to="/login" replace />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="seguridad" element={<PermissionGate permissions={[PERM.seguridadUsuariosAdmin, PERM.seguridadRolesAdmin, PERM.seguridadPermisosAdmin]}><SeguridadPage /></PermissionGate>} />
          <Route path="auditoria" element={<PermissionGate permissions={[PERM.seguridadAuditoriaLeer]}><AuditoriaPage /></PermissionGate>} />
          <Route path="notificaciones" element={<NotificacionesPage />} />
          <Route path="reportes" element={<PermissionGate permissions={[PERM.facturaProveedorLeer, PERM.facturasLeer, PERM.alertasLeer, PERM.reportesInventarioLeer, PERM.reportesContablesLeer]}><ReportesPage /></PermissionGate>} />
          <Route path="categorias" element={<PermissionGate permissions={[PERM.categoriasLeer]}><CategoriasPage /></PermissionGate>} />
          <Route path="departamentos" element={<PermissionGate permissions={[PERM.departamentosLeer]}><DepartamentosPage /></PermissionGate>} />
          <Route path="almacenes" element={<PermissionGate permissions={[PERM.almacenesLeer]}><AlmacenesPage /></PermissionGate>} />
          <Route path="proveedores" element={<PermissionGate permissions={[PERM.proveedoresLeer]}><ProveedoresPage /></PermissionGate>} />
          <Route path="productos" element={<PermissionGate permissions={[PERM.productosLeer]}><ProductosPage /></PermissionGate>} />
          <Route path="ordenes-compra" element={<PermissionGate permissions={[PERM.ordenesLeer]}><OrdenesPage /></PermissionGate>} />
          <Route path="recepciones" element={<PermissionGate permissions={[PERM.recepcionesLeer]}><RecepcionesPage /></PermissionGate>} />
          <Route path="inventario">
            <Route index element={<Navigate to="existencias" replace />} />
            <Route path="existencias" element={<PermissionGate permissions={[PERM.existenciasLeer]}><ExistenciasPage /></PermissionGate>} />
            <Route path="movimientos" element={<PermissionGate permissions={[PERM.movimientosLeer]}><MovimientosPage /></PermissionGate>} />
            <Route path="ajustes" element={<PermissionGate permissions={[PERM.ajustesLeer]}><AjustesPage /></PermissionGate>} />
            <Route path="conteos" element={<PermissionGate permissions={[PERM.conteosLeer]}><ConteosPage /></PermissionGate>} />
            <Route path="alertas" element={<PermissionGate permissions={[PERM.alertasLeer]}><AlertasPage /></PermissionGate>} />
            <Route path="rotacion" element={<PermissionGate permissions={[PERM.reportesInventarioLeer]}><RotacionPage /></PermissionGate>} />
          </Route>
          <Route path="ventas">
            <Route index element={<Navigate to="clientes" replace />} />
            <Route path="clientes" element={<PermissionGate permissions={[PERM.clientesLeer]}><ClientesPage /></PermissionGate>} />
            <Route path="cotizaciones" element={<PermissionGate permissions={[PERM.cotizacionesLeer]}><CotizacionesPage /></PermissionGate>} />
            <Route path="pedidos" element={<PermissionGate permissions={[PERM.pedidosLeer]}><PedidosPage /></PermissionGate>} />
            <Route path="despachos" element={<PermissionGate permissions={[PERM.despachoLeer]}><DespachosPage /></PermissionGate>} />
            <Route path="facturas" element={<PermissionGate permissions={[PERM.facturasLeer]}><FacturasPage /></PermissionGate>} />
          </Route>
          <Route path="contabilidad" element={<PermissionGate permissions={Object.values(PERM).filter((permission) => permission.startsWith('CONTABILIDAD_'))}><ContabilidadPage /></PermissionGate>} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
