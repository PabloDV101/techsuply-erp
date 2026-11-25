import { lazy, Suspense } from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import ProtectedRoute from './features/auth/ProtectedRoute'

const LoginPage = lazy(() => import('./features/auth/LoginPage'))
const RegisterPage = lazy(() => import('./features/auth/RegisterPage'))
const LocalAccountRecoveryPage = lazy(() => import('./features/auth/LocalAccountRecoveryPage'))
const AppLayout = lazy(() => import('./layout/AppLayout'))
const DashboardPage = lazy(() => import('./features/dashboard/DashboardPage'))
const ProveedoresPage = lazy(() => import('./features/proveedores/ProveedoresPage'))
const ProductosPage = lazy(() => import('./features/productos/ProductosPage'))
const OrdenesPage = lazy(() => import('./features/compras/ordenes/OrdenesPage'))
const RecepcionesPage = lazy(() => import('./features/compras/recepciones/RecepcionesPage'))
const ExistenciasPage = lazy(() => import('./features/inventario/existencias/ExistenciasPage'))
const MovimientosPage = lazy(() => import('./features/inventario/movimientos/MovimientosPage'))
const AjustesPage = lazy(() => import('./features/inventario/ajustes/AjustesPage'))
const ConteosPage = lazy(() => import('./features/inventario/conteos/ConteosPage'))
const AlertasPage = lazy(() => import('./features/inventario/alertas/AlertasPage'))
const RotacionPage = lazy(() => import('./features/inventario/reportes/RotacionPage'))
const ClientesPage = lazy(() => import('./features/clientes/ClientesPage'))
const CotizacionesPage = lazy(() => import('./features/ventas/documentos/CotizacionesPage'))
const PedidosPage = lazy(() => import('./features/ventas/documentos/PedidosPage'))
const DespachosPage = lazy(() => import('./features/ventas/despachos/DespachosPage'))
const FacturasPage = lazy(() => import('./features/ventas/facturas/FacturasPage'))
const CreditoPage = lazy(() => import('./features/credito/CreditoPage'))
const ContabilidadPage = lazy(() => import('./features/contabilidad/ContabilidadPage'))

export default function App() {
  return (
    <Suspense fallback={<div role="status">Cargando TechSupply ERP…</div>}>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route path="/registro" element={<RegisterPage />} />
        <Route path="/recuperar-cuenta" element={<LocalAccountRecoveryPage />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<AppLayout />}>
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
            <Route path="ventas">
              <Route index element={<Navigate to="clientes" replace />} />
              <Route path="clientes" element={<ClientesPage />} />
              <Route path="cotizaciones" element={<CotizacionesPage />} />
              <Route path="pedidos" element={<PedidosPage />} />
              <Route path="despachos" element={<DespachosPage />} />
              <Route path="facturas" element={<FacturasPage />} />
              <Route path="credito" element={<CreditoPage />} />
            </Route>
            <Route path="contabilidad" element={<ContabilidadPage />} />
          </Route>
        </Route>
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </Suspense>
  )
}
