# Medición local del backend

`medir_backend.py` genera carga sintética por HTTP contra un proceso Java y un contenedor PostgreSQL desechables creados por el propio script. No usa `DB_URL` de la sesión ni toca el contenedor de desarrollo. Requiere Linux, Python 3.9+ con biblioteca estándar y zona horaria `America/Mexico_City`, Java 21, Docker operativo e imagen `postgres:17-alpine` disponible. Empaquetar primero el código que se desea medir; este paso omite las pruebas y no sustituye `verify`.

Desde la raíz del repositorio:

```bash
(cd erp-backend && MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DskipTests package)
python3 tools/performance/medir_backend.py --rounds 8 --output /tmp/techsupply-resultado.json
python3 tools/performance/verificar_evidencia.py /tmp/techsupply-resultado.json
```

El archivo de salida debe ser nuevo. Se generan puertos aleatorios, contraseña de base y 200 tokens de sesión aleatorios en memoria. El script introduce únicamente sus hashes en la base temporal; no registra credenciales ni tokens en el JSON. Las cuentas tienen permisos de lectura para las rutas medidas y no se realiza el inicio de sesión durante la carga. El contenedor sin volúmenes y el proceso Java se detienen al terminar; una interrupción forzada del sistema puede requerir revisar recursos con prefijo `techsupply-perf-`.

## Escenario

El script crea con SQL 1.000 categorías, 10.000 productos, tres almacenes, 30.000 existencias y 30.000 mínimos explícitos. Los importes y cantidades son sintéticos; se comprueban los totales devueltos y los dos indicadores accesibles del dashboard. No se generan movimientos, facturas ni asientos. La ausencia de históricos limita la representatividad del dashboard y excluye reportes financieros, ABC, rotación y rentabilidad de los resultados. No se ejercitan escrituras, login, exportaciones ni frontend.

En cada etapa de 1, 50 y 200 usuarios, cada usuario tiene una sesión propia y realiza `4 × rounds` solicitudes, dos consultas paginadas (categorías y productos) y dos reportes (stock bajo y dashboard). Se usa una conexión persistente por usuario, orden rotado entre usuarios, barrera de arranque y ninguna pausa; cada usuario espera su respuesta antes de enviar la siguiente. Antes se precalientan las rutas y se verifica un 401 sin sesión. Los valores predeterminados de `--rounds` son 2; se permiten 1–20.

Las latencias incluyen red local, colas y lectura/validación del cuerpo; el p95 es nearest rank calculado solo sobre respuestas HTTP 200 cuyo contenido coincide con el dataset. Los errores, respuestas inválidas, el total de solicitudes y la cantidad bajo el umbral aparecen por separado; **no se excluyen errores del denominador de cumplimiento**. Cada ruta también guarda muestras anónimas `[milisegundos, estado, válida]` para recalcular los percentiles; las ejecuciones anteriores a esta adición contienen solo agregados. El máximo en vuelo lo mide el cliente e incluye solicitudes esperando atención: no es una medida de hilos que ejecutan lógica al mismo tiempo dentro del servidor. La duración por etapa y las solicitudes por segundo describen esta carga cerrada y corta; no demuestran capacidad sostenida, disponibilidad, SLAs ni comportamiento en red real. Los 200 usuarios no validan todos los módulos ni accesos de escritura.

`verificar_evidencia.py` recalcula estados, p50/p95/p99, máximos y conteos bajo el umbral desde las muestras. Este control detecta agregados incoherentes; no acredita que el escenario sea representativo.

Los resultados documentados para este sprint están en [la evidencia del sprint 7](../../docs/fase-3/SPRINT_7_INTEGRACION_TESTING_POR_PARTES.md). Los objetivos del SRS requieren dataset y ambiente representativos acordados: no se puede extrapolar automáticamente desde una sola máquina compartida y una base sintética.
