import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './features/auth/LoginPage'
import ProtectedRoute from './features/auth/ProtectedRoute'
import AppLayout from './layout/AppLayout'
import DashboardPage from './features/dashboard/DashboardPage'
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
import ChangePasswordPage from './features/auth/ChangePasswordPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route path="cambiar-contrasena" element={<ChangePasswordPage />} />
          <Route index element={<DashboardPage />} />
          <Route path="proveedores" element={<ProveedoresPage />} />
          <Route path="productos" element={<ProductosPage />} />
          <Route path="ordenes-compra" element={<OrdenesPage />} />
          <Route path="recepciones" element={<RecepcionesPage />} />
          <Route path="inventario">
            <Route index element={<Navigate to="existencias" replace />} />
            <Route path="existencias" element={<ExistenciasPage />} />
            <Route path="movimientos" element={<MovimientosPage />} />
            <Route path="ajustes" element={<AjustesPage />} />
            <Route path="conteos" element={<ConteosPage />} />
            <Route path="alertas" element={<AlertasPage />} />
            <Route path="rotacion" element={<RotacionPage />} />
          </Route>
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
