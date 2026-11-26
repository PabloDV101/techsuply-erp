# Sprint 7 — ejecución local y guion de demostración

Esta guía describe el recorrido React → API REST → PostgreSQL local y una demostración
acotada de las pantallas conectadas. No es una guía de despliegue productivo. Las
credenciales deben mantenerse fuera de Git y usarse solo con una base local autorizada.

## Requisitos

- Java 21 y Docker para PostgreSQL y las pruebas de integración.
- Node.js/npm compatibles con las versiones fijadas en `frontend/package-lock.json`.
- Repositorio en una revisión coherente: frontend y backend se compilan desde el mismo
  árbol de trabajo.

## Arranque local

1. Preparar PostgreSQL local. `deploy/docker-compose.yml` requiere `POSTGRES_DB`,
   `POSTGRES_USER` y `POSTGRES_PASSWORD`, suministrados desde un `.env` local ignorado por
   Git o desde el entorno. El contenedor conserva datos en un volumen. No ejecutar
   `down -v` si se quieren conservar.
2. Configurar el backend con `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` para esa base. El
   `.env` de Compose no configura automáticamente Spring. Revisar la base antes del
   primer arranque: Flyway aplicará las migraciones pendientes. Arrancar desde
   `erp-backend/`:

   ```bash
   ./mvnw spring-boot:run
   ```

   La API queda por defecto en `http://localhost:8080/api/v1`; salud en
   `http://localhost:8080/actuator/health`.
3. Si la base no tiene usuarios, crear una cuenta inicial solo mediante el bootstrap
   explícito documentado en [el componente de autenticación](../fase-2/autenticacion/COMPONENTE_AUTENTICACION.md).
   Usar credenciales aleatorias en el entorno local y desactivar el bootstrap tras crearla.
   El arranque normal no genera usuarios ni contraseñas.
4. En otra terminal, desde `frontend/`:

   ```bash
   npm ci
   BACKEND_PROXY_TARGET=http://127.0.0.1:8080 npm run dev
   ```

   Abrir la dirección que indique Vite (normalmente `http://localhost:5173`). El proxy
   local evita que el navegador llame a otro origen; no habilita CORS en el backend.
   Dejar `VITE_API_URL` sin definir para usar ese proxy.

Para detener el frontend, interrumpir Vite; para detener el backend, interrumpir Maven.
El apagado normal de Compose no borra el volumen. No conectar la aplicación a una base
compartida o productiva como parte de esta demostración.

## Guion sugerido

Usar una base local con catálogos y datos de demostración coherentes, y una cuenta con
permisos mínimos para las pantallas que se mostrarán. Las autoridades se verifican en el
backend; ocultar un enlace no concede acceso.

1. Iniciar sesión y observar que el menú depende de los permisos actuales.
2. En **Compras**, crear una orden, someterla y aprobarla con una segunda cuenta autorizada;
   registrar y confirmar la recepción. En **Inventario**, consultar existencias y kardex.
3. En **Ventas**, crear y aceptar una cotización, convertirla a pedido, reservar, despachar
   y emitir la factura interna. Aclarar que esto no genera CFDI ni registra un cobro.
4. En **Contabilidad**, consultar integración, CxP/CxC y reportes disponibles. Mostrar que
   el flujo contable de las operaciones depende de la configuración y periodo válidos.
5. Abrir **Panel** e **Informes**, elegir fechas y consultar los informes autorizados.
   Las exportaciones utilizan endpoints backend auditados; indicar el informe y página
   seleccionados.
6. En **Auditoría**, consultar eventos permitidos. En **Notificaciones**, mostrar la bandeja
   personal y marcar una notificación como leída si hay datos. **Seguridad** muestra solo
   las operaciones disponibles a los permisos delegables del usuario.
7. Cerrar sesión y volver a iniciar con la segunda cuenta para comprobar la diferencia de
   permisos. Una solicitud sin permiso debe ser rechazada por el backend (403).

El recorrido automatizado más amplio usa un PostgreSQL desechable, un JAR backend,
Playwright/Chromium y Vite. Desde la raíz del repositorio, tras instalar Playwright según
el procedimiento de [la ruta de integración](SPRINT_7_RUTA_INTEGRACION_FRONTEND.md):

```bash
(cd erp-backend && MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o verify)
NODE_PATH=/tmp/techsupply-browser/node_modules python3 -B frontend/scripts/auth-integration.py
```

El script crea credenciales aleatorias, una base temporal y datos de prueba; limpia sus
procesos al terminar. No usa la base local ni demuestra CORS/TLS, producción o carga.

## Límites funcionales que deben explicarse

- Nómina, presupuesto, CFDI/timbrado, portal externo y varios flujos de reversa no forman
  parte de lo implementado. `CONTADO` no demuestra que se haya recibido dinero.
- Los análisis, exportaciones, filtros y acciones disponibles dependen de permisos y de las
  rutas existentes; la interfaz no completa endpoints ausentes.
- La prueba automatizada no cubre por navegador todas las mutaciones, errores, filtros,
  páginas, formatos de exportación ni cambios de permisos. Ver detalles en la
  [matriz de trazabilidad](SPRINT_7_MATRIZ_TRAZABILIDAD_PRUEBAS.md).
- La verificación local de rendimiento de la parte 5 no acredita objetivos generales ni
  carga de todos los módulos. No se validaron CORS entre orígenes, TLS ni despliegue.
