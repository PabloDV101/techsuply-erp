# Entrega de sprints 7–12

Rama de trabajo: `feature/sprints-7-12-final`.

## Alcance disponible

- **Sprints 7–8:** maestros básicos y contabilidad con cuentas, periodos, asientos y reportes financieros iniciales.
- **Sprints 9–10:** panel con conteos de maestros y saldos contables por tipo de cuenta; estados de resultados y balance general consultables por periodo.
- **Sprints 11–12:** pruebas PostgreSQL de integración, pruebas Playwright para flujos del navegador y carga diferida de páginas React.

Esta entrega no completa todos los módulos del ERP. Compras, movimientos/existencias de inventario, facturación, nómina, rentabilidad por cliente y margen operativo carecen de fuentes o fórmulas aprobadas. El panel los identifica como **No disponible** en vez de calcular ceros o estimaciones. El flujo de efectivo también responde `NO_DISPONIBLE`. El mayor actual es un resumen por cuenta, no un libro cronológico con saldo corrido.

La migración V9 corrige el tamaño de la columna de estado de asientos para permitir `CONTABILIZADO`; no edita V8 y es compatible con bases donde V8 ya fue aplicada.

## Arranque local en PowerShell

Inicia primero PostgreSQL con la configuración de Docker Compose del repositorio. Usa los valores y credenciales locales de tu entorno; no guardes contraseñas en Git.

Terminal 1 — backend, JDK 25:

```powershell
Set-Location 'C:\ruta\al\repositorio\erp-backend'
$env:JAVA_HOME = 'C:\ruta\a\jdk-25'
$env:Path = "$env:JAVA_HOME\bin;$env:Path"
$env:SERVER_PORT = '8081'
$env:DB_URL = 'jdbc:postgresql://localhost:5434/techsupply_dev'
$env:DB_USERNAME = 'techsupply'
$env:DB_PASSWORD = '<contraseña local configurada para PostgreSQL>'
.\mvnw.cmd spring-boot:run
```

Terminal 2 — frontend:

```powershell
Set-Location 'C:\ruta\al\repositorio\frontend'
$env:VITE_API_URL = 'http://localhost:8081'
npm install
npm run dev
```

## Verificación

```powershell
# Backend: integración contra PostgreSQL desechable (Docker debe estar disponible)
Set-Location 'C:\ruta\al\repositorio\erp-backend'
.\mvnw.cmd -o verify

# Frontend
Set-Location 'C:\ruta\al\repositorio\frontend'
npm run lint
npm run build

# Playwright: navegador Chromium ya instalado con `npx playwright install chromium`
$env:E2E_API_URL = 'http://localhost:8081'
npm run test:e2e
```

Los flujos de dashboard requieren `E2E_USERNAME` y `E2E_PASSWORD` de un usuario de prueba autorizado. La prueba que registra una cuenta escribe datos y se omite por defecto; habilítala con `E2E_ALLOW_USER_CREATION=true` solo contra una base desechable. Para detalles de contratos y pendientes de reportería, consulta [COMPONENTE_REPORTERIA.md](./fase-2/reporteria/COMPONENTE_REPORTERIA.md).

## Rendimiento y límites verificados

La división de código redujo el chunk de entrada reportado por Vite de 1,001.94 kB (300.88 kB gzip) a 306.71 kB (97.39 kB gzip): aproximadamente 69 % menos sin comprimir y 68 % menos en gzip. Esto mide artefactos de compilación, no tiempos de carga percibidos.

No se ha medido todavía p95 de reportes, 200 usuarios concurrentes ni cumplimiento de los objetivos de disponibilidad. No se afirma que esos objetivos estén alcanzados.

## Evidencia ejecutada

En Windows con Java 25.0.5, Docker 29.9.3, Node 24.11.0 y Chromium de Playwright:

- `.\mvnw.cmd -o verify`: correcto; 40 pruebas unitarias y 60 de integración, sin fallos ni omisiones. PostgreSQL Testcontainers aplicó y validó V1–V9.
- `npm run lint`: correcto, con advertencias de Oxlint preexistentes en otros componentes.
- `npm run build`: correcto; chunk de entrada 306.71 kB (97.40 kB gzip).
- `npm run typecheck:e2e`: correcto.
- `npm run test:e2e`: código 0; 1 prueba de navegación pasó y 3 flujos dependientes de backend/credenciales se omitieron. La prueba de salud en `localhost:8081` no encontró un backend activo; no se verificó un arranque HTTP real en esta ejecución.

El informe detallado está en [runtime-validation-report.md](./runtime-validation-report.md). Para cerrar la validación del navegador se requiere iniciar Spring en `8081` y configurar un usuario de prueba autorizado; habilita el alta E2E solo con una base desechable.
