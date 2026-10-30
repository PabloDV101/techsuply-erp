import { Navigate, Route, Routes } from 'react-router-dom'
import LoginPage from './features/auth/LoginPage'
import ProtectedRoute from './features/auth/ProtectedRoute'
import AppLayout from './layout/AppLayout'
import DashboardPage from './features/dashboard/DashboardPage'
import ProveedoresPage from './features/proveedores/ProveedoresPage'
import ProductosPage from './features/productos/ProductosPage'
import OrdenesPage from './features/compras/ordenes/OrdenesPage'
import RecepcionesPage from './features/compras/recepciones/RecepcionesPage'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route element={<ProtectedRoute />}>
        <Route element={<AppLayout />}>
          <Route index element={<DashboardPage />} />
          <Route path="proveedores" element={<ProveedoresPage />} />
          <Route path="productos" element={<ProductosPage />} />
          <Route path="ordenes-compra" element={<OrdenesPage />} />
          <Route path="recepciones" element={<RecepcionesPage />} />
        </Route>
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  )
}
