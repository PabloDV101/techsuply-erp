# Sprint 7 — integración frontend/backend

**Estado:** cerrado con límites documentados. **Rama revisada:** `integracion`.

## Alcance entregado

Se conectó la aplicación React/TypeScript a las rutas REST disponibles para Gestión
Maestra, Compras, Inventario, Ventas, Contabilidad y Reportería. Se añadieron las pantallas
administrativas de usuarios/roles/permisos, consulta de auditoría y bandeja personal de
notificaciones. La autorización real se conserva en backend; el frontend además filtra
menú/rutas por los permisos de `/auth/me` y oculta operaciones fuera de los contratos.

La interfaz respeta las limitaciones de las API: no presenta crear notificaciones, CFDI,
nómina, reabrir periodos, reversas no disponibles ni otros controles sin endpoint. El
detalle funcional por módulo y las brechas de navegador están en la [ruta frontend](SPRINT_7_RUTA_INTEGRACION_FRONTEND.md).

## Verificación de cierre — 25 nov. 2026

- Backend: `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o verify` terminó
  en **BUILD SUCCESS**. Surefire: 114 pruebas; Failsafe: 230; 0 fallos, errores u omitidas.
  PostgreSQL 17/Testcontainers validó y aplicó las 26 migraciones en bases desechables.
- Frontend: `npm run build` pasó; `npm run lint` pasó sin errores. Lint emite 15 avisos
  Fast Refresh en componentes compartidos. Vite avisa que el bundle principal minificado
  es de aproximadamente 1.095 kB.
- Navegador: `NODE_PATH=/tmp/techsupply-browser/node_modules python3 -B
  scripts/auth-integration.py` terminó con código 0 desde `frontend/`. Chromium recorrió
  autenticación, permisos, catálogos, compras/recepción, inventario, ventas/factura,
  contabilidad, reportes, seguridad, auditoría, notificaciones, stock/kardex y revocación
  de sesión con backend empaquetado, Vite y PostgreSQL temporal.
- Contratos: los nueve documentos `openapi-*.yaml` pasaron `validar-openapi.cjs`. La
  validación encontró y llevó a corregir en el contrato contable la referencia `Page` de
  los listados de CxP/facturas proveedor. Swagger Parser se instaló temporalmente fuera
  del proyecto; no se añadieron dependencias al repositorio.
- Rendimiento local: la prueba sintética de 1/50/200 sesiones hizo 32/1.600/6.400
  solicitudes válidas. Con 200 sesiones, p95: categorías 1.401 ms, productos 1.390 ms,
  stock bajo 1.521 ms y dashboard 1.449 ms. Hubo 3/1.600 respuestas de categorías y
  2/1.600 de productos sobre 2 s, aunque los p95 quedaron debajo de ese umbral. El archivo
  verificable está en [evidencia](evidencias/rendimiento-local-2026-11-25-sprint7.json).
  El JAR medido fue generado por la ejecución `verify` desde el árbol de trabajo de esta
  rama; el registro captura HEAD `4dbb220` y el SHA-256 del JAR, pero el árbol contenía
  modificaciones locales.
- `git diff --check`: limpio.

## Documentación, datos y arquitectura

Se actualizó el README para reflejar los módulos frontend conectados, se añadió una [guía
de ejecución y demo](SPRINT_7_GUIA_EJECUCION_Y_DEMO.md), se corrigieron las referencias de
integración frontend en la documentación API/autenticación y se actualizaron la [ruta por
partes](SPRINT_7_RUTA_INTEGRACION_FRONTEND.md), la [bitácora](SPRINT_7_INTEGRACION_TESTING_POR_PARTES.md)
y la [matriz](SPRINT_7_MATRIZ_TRAZABILIDAD_PRUEBAS.md).

No se cambiaron migraciones, DBML ni diagramas en este sprint de integración: los módulos
del sprint consumen el esquema y contratos existentes. La actualización de OpenAPI fue
documental y corrige una referencia inválida sin cambiar rutas o DTO backend.

## Límites pendientes

- La cobertura exclusivamente unitaria permanece por debajo de la meta de 70 % registrada
  en la trazabilidad; la cobertura de integración o combinada no la sustituye.
- Chromium no cubre cada filtro/página/formato y no ejecuta todas las mutaciones de
  Contabilidad o Seguridad ni marcar notificaciones leídas. La prueba incluye roles
  distintos en flujos de autorización, pero no valida cada combinación de permisos.
- La medición de rendimiento usa datos sintéticos, solo cuatro rutas, sin histórico de
  movimientos/facturas ni escritores. No demuestra la capacidad sostenida ni todos los
  objetivos RNF. CORS, TLS, despliegue y carga de producción no fueron comprobados.
- El guion de demo es técnico; no constituye manual operativo validado por usuarios.
- Se mantienen fuera del sprint nómina, presupuesto, CFDI/timbrado, crédito operativo y
  reversión/conciliación bancaria donde no existen operaciones aprobadas.

Las pruebas no muestran un fallo que por sí mismo impida integrar este incremento. Antes
de hacer commit, revisar el diff total de la rama: incluye los cambios locales acumulados
de las partes previas del sprint además de la evidencia de cierre. No se creó commit.
