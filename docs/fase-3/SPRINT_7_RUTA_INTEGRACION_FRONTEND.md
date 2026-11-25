# Ruta de integración frontend–backend

**Situación revisada:** rama `integracion`, HEAD `3403948`, con el frontend del compañero como cambios locales. La autorización del usuario amplía el trabajo al frontend para integrar primero autenticación. La implementación del resto de pantallas se conserva y sigue pendiente de conciliación con los contratos. La ruta se ejecutará por partes: inspeccionar código y cambios actuales, implementar una parte, documentar, verificar en navegador con backend real y registrar evidencia antes de continuar.

## Estado inicial y diferencias observadas

- React/TypeScript con Vite, Material UI, React Router, Axios y TanStack Query. Las pantallas de maestros, compras, inventario, ventas y contabilidad existen como trabajo de interfaz; su existencia no demuestra integración funcional.
- El backend expone `/api/v1`; la fuente por operación es el [índice de contratos OpenAPI](../api/README.md) y el código vigente. El archivo `frontend/src/config/contract.ts` contiene rutas y permisos propuestos que en muchos casos no coinciden con el backend: por ejemplo `/proveedores` frente a `/suppliers`, `/ordenes-compra` frente a `/purchase-orders`, `/pedidos-venta` frente a `/sales/orders`; nombres como `PROVEEDOR_WRITE` tampoco son permisos reales.
- `useLookup` solicita `size=200`, mientras `PageRequests` admite como máximo 100. También hay diferencias de nombres de campos, filtros, estados, sufijos de transición y forma de paginación; cambiar solamente las rutas no resuelve los flujos.
- El backend no ofrece registro público, recuperación local ni cambio de contraseña mediante las rutas propuestas en el frontend. Tampoco hay crédito operativo, CFDI, nómina ni reversión de cobros/pagos. No presentar esas pantallas como operativas ni crear contratos ficticios para ellas.
- Para evitar solicitudes a rutas aún no conciliadas al entrar, el inicio autenticado muestra una portada sencilla; `DashboardPage` y las demás pantallas se conservan para su integración. Las secciones del menú siguen siendo trabajo en curso.

## Contrato transversal

1. Usar `/api/v1` y los nombres, métodos, validaciones y respuestas de cada contrato; preparar tipos y adaptadores explícitos por dominio, evitando `any` y las equivalencias automáticas que puedan ocultar datos ausentes. Comprobar `content`, `totalElements`, página desde cero, tamaño y orden permitido por cada operación.
2. Enviar Bearer únicamente con sesión válida. Refrescar identidad por `/auth/me` al recargar, no inferir permisos por nombre de rol; respetar 401, 403, 400, 404, 409 y 503. El backend sigue aplicando autorización. Limpiar caché al cambiar de usuario o cerrar sesión y no reintentar automáticamente mutaciones no idempotentes.
3. Tratar fechas de negocio como fechas, instantes con zona y dinero/cantidades con las precisiones del contrato. Mostrar cálculos y estados retornados por el backend; no convertir una falta de datos en cero. Las claves de idempotencia van donde las define cada endpoint y se conservan en el reintento de la misma operación.
4. Diseñar cada sección con estados de carga, vacío, error, sin permiso y resultado desactualizado; invalidar las consultas afectadas tras mutaciones. Probar por rol y también con autorización denegada por el servidor.
5. Para desarrollo local, Vite reenvía `/api` al backend: por defecto `http://127.0.0.1:8080`; `BACKEND_PROXY_TARGET` cambia el destino del proxy de desarrollo. El proxy no se incluye en el build ni en `vite preview`. Para despliegue se necesita un reverse proxy del mismo origen o una política CORS explícita acordada, además de TLS y configuración de operación; no se han implementado ni validado aquí.

## Partes propuestas

| Parte | Integración y dependencia | Entregable y comprobación mínima |
|---|---|---|
| 0. Identidad y sesión | **Realizada en esta entrega:** `POST /auth/login`, `GET /auth/me`, `POST /auth/logout`. | Inicio con usuario/correo, expiración, 401, 403, recarga, fallo de red, revocación y pestañas; prueba en Chromium contra backend y PostgreSQL temporales. La revocación no se confirma cuando falla la red. |
| 1. Base de API y acceso | Depende de 0; revisar `http.ts`, `contract.ts`, `shared/api.ts` y `CrudPage.tsx`. | Inventario de operaciones y permisos; tipos/adaptadores y paginación real (máximo 100 donde aplica), gestión de errores e invalidación, pruebas de contrato y aislamiento de caché entre usuarios. Resolver qué secciones mostrar según permisos concretos, manteniendo la autorización en el backend. |
| 2. Catálogos y clientes | [Maestros y compras](../api/openapi-sprint-1-maestros-compras.yaml), [categorías](../api/openapi-crud-categorias.yaml), [clientes](../api/openapi-sprint-3-ventas.yaml). | Integrar `/categories`, `/departments`, `/suppliers`, `/warehouses`, `/products` y `/customers`. Ajustar crear/editar/estado, filtros y campos reales; añadir las pantallas que falten. Probar usuario de lectura, administración, 403 y referencias inactivas. |
| 3. Compras | Parte 2 y [guía de compras](../api/guia-sprint-1.md). | `/purchase-orders` y `/purchase-receipts`, con `submit`, `approve`, `send`, `confirm`, detalle y estados reales. Cubrir aprobación por persona distinta, recepción 20/18/2, reintento y actualización de stock/kardex. Integrar factura de proveedor y CxP en parte 6. |
| 4. Inventario | Partes 2–3 y [contrato de Inventario](../api/openapi-sprint-2-inventario.yaml). | `/inventory/stock`, `/inventory/movements`, `/inventory/counts`, `/inventory/adjustments`, alertas. Implementar conteo y ajuste aprobable con motivo, permisos, reintentos y conflicto de fotografía obsoleta; no ofrecer movimientos manuales que no existan. Comprobar existencias y kardex tras cada flujo. |
| 5. Ventas | Partes 2 y 4; [contrato](../api/openapi-sprint-3-ventas.yaml) y [guía](../api/guia-sprint-3-ventas.md). | `/sales/quotes`, `/sales/orders`, `/sales/dispatches`, `/sales/invoices` y sus transiciones. Probar cotización, reserva, despacho parcial, factura y cancelaciones admitidas. Diferenciar emisión, integración contable pendiente y cobro; no llamar a un endpoint de pago o crédito inventado. |
| 6. Contabilidad, CxC y CxP | Partes 3–5; [contrato contable](../api/openapi-sprint-4-contabilidad.yaml). | `/accounting/accounts`, `periods`, `entries`, mayor/balance de prueba; mapeos, outbox y reintento, auxiliares CxC/CxP, cobros/aplicaciones, factura proveedor y pagos. Probar precisión, permisos, periodo cerrado, bloqueo por borradores, conciliación y asientos; no ofrecer reapertura o reversión de pagos inexistentes. |
| 7. Dashboard y reportes | Partes 1–6 según fuente; [contrato](../api/openapi-reporteria.yaml) y [guía](../api/guia-sprint-5-reporteria.md). | Integrar `/reports/dashboard`, facturación, stock, analíticas y estados financieros. Solicitar fechas y periodo donde proceda, filtrar por permisos y mostrar `NO_DISPONIBLE` con motivo. Exportar CSV/PDF/XLSX con filtros, manejo de errores y sin asumir descarga completa de todas las páginas. Nómina permanece fuera de alcance. |
| 8. Seguridad, auditoría y notificaciones | Parte 1; [autenticación y administración](../api/openapi-auth.yaml), [auditoría](../api/openapi-audit.yaml), [notificaciones](../api/openapi-notifications.yaml). | Pantallas para usuarios, roles y permisos administrativos, consulta de auditoría y bandeja APP, con controles por permiso y pruebas de revocación. Mantener fuera del menú funcionalidades que no existan. |
| 9. Integración final y entrega | Partes anteriores terminadas; entorno coordinado con el compañero. | Flujos de compra→inventario→venta→contabilidad→reportes y proveedor→CxP→pago con datos coherentes; navegador y backend reales, dos roles, errores y recarga. Validar guía de arranque, revisión de contratos, manual y demostración; medir nuevamente rendimiento representativo y registrar límites. |

Cada parte requiere acordar cambios del compañero que se superpongan, pruebas funcionales focalizadas y evidencia de los comandos reales. No presupone que todas las funcionalidades pendientes del SRS estén implementadas.

## Autenticación entregada y reproducción

El formulario utiliza `identificador` y `password`; el backend devuelve token opaco, vencimiento e identidad. El frontend conserva token y vencimiento en `localStorage` para recuperar la sesión tras recarga y entre pestañas, y valida `/auth/me` antes de mostrar rutas protegidas. Los permisos proceden de la respuesta del servidor. Al cerrar sesión intenta revocar el token, limpia la caché y el almacenamiento local; si no puede confirmar la revocación informa que el cierre fue solo local. Los tokens en `localStorage` son accesibles a JavaScript del mismo origen: proteger el frontend frente a XSS y evaluar una estrategia de sesión para producción queda pendiente de la fase de despliegue. No se incorpora refresh token ni se cambia la duración del backend.

1. Preparar una base de desarrollo autorizada y el backend siguiendo [configuración y bootstrap](../fase-2/autenticacion/COMPONENTE_AUTENTICACION.md); Flyway modifica la base configurada, por lo que debe revisarse antes de arrancar. Las credenciales son externas a Git.
2. En otra terminal, desde `frontend/`, ejecutar `npm install` si faltan dependencias y `npm run dev`. Entrar a la URL indicada por Vite. Con backend en otra dirección local, definir por ejemplo `BACKEND_PROXY_TARGET=http://127.0.0.1:8081 npm run dev`. Mantener `VITE_API_URL` sin definir para utilizar el proxy; si se define un origen diferente, el navegador requerirá CORS compatible que el backend aún no configura.
3. La prueba automatizada usa únicamente un PostgreSQL Docker nuevo, puertos temporales, un backend separado y credenciales aleatorias; no usa la base de desarrollo. Requiere JAR actual, Node, Python, Docker y Playwright con Chromium. Ejemplo desde la raíz:

```bash
(cd erp-backend && MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DskipTests package)
npm install --prefix /tmp/techsupply-browser --no-audit --no-fund playwright@1.57.0
/tmp/techsupply-browser/node_modules/.bin/playwright install chromium
NODE_PATH=/tmp/techsupply-browser/node_modules python3 -B frontend/scripts/auth-integration.py
```

El contenedor y los procesos temporales se detienen al finalizar. La prueba crea cuentas de prueba y hace bootstrap solo en la base desechable; no demuestra CORS, TLS, despliegue, integración de los demás módulos ni persistencia de usuarios reales.

### Evidencia del 24 nov. 2026

- `npm run build`: éxito; Vite avisó de un paquete superior a 500 kB. `npm run lint`: éxito con 28 avisos y sin errores.
- El comando de navegador anterior terminó correctamente con Chromium headless, Vite, backend y PostgreSQL 17 temporales: login válido e inválido, fallo de conexión al iniciar, recarga y `/me`, reconexión, logout y revocación, sincronización entre pestañas, 403, 401 tras revocación, expiración y fallo de logout. En este último caso se verificó que el token seguía activo en el servidor y que la interfaz avisaba que solo se había cerrado localmente.
- Una ejecución intermedia no encontró el mensaje de credenciales inválidas mientras se estaban editando archivos con Vite en ejecución; no se determinó la causa. Las ejecuciones posteriores, con los archivos estabilizados, terminaron correctamente. Se añadieron la comprobación explícita de estado 401 y el fallo de red durante login.
- Los procesos y contenedores temporales se cerraron; no se ejecutaron pruebas de integración de las otras pantallas ni se verificó la configuración productiva.
