# Sprint 7 — Integración y testing por partes

**Estado:** partes 0–5 cerradas con límites; este plan se basa en el repositorio integrado el 24 nov. 2026. Rama `integracion`, alcance del sprint sujeto a evidencia y límites registrados. No hacer commits, merges ni cambiar de rama sin solicitud expresa.

## Objetivo y alcance

Validar los contratos y recorridos integrados de la versión entregable, corregir defectos reproducibles, dejar una medición reproducible de cobertura y rendimiento y preparar la evidencia técnica para revisión/demostración. El sprint corresponde al objetivo de sprint 7 de la [ruta de Fase 3](../FASE_3_RUTA_BACKEND.md) y a RNF-02/03/04/12/17 del [SRS](../SRS_TechSupply_Solutions.md). No iniciar módulos funcionales nuevos ni cambiar reglas de negocio para mejorar métricas.

En el preflight el alcance autorizado era backend y `frontend/src/App.tsx` era la plantilla de Vite. Posteriormente el usuario proporcionó cambios locales del compañero y autorizó integrar el frontend. Se conectó autenticación y se verificó en navegador; las demás pantallas conservan sus contratos propuestos y requieren la [ruta de integración detallada](SPRINT_7_RUTA_INTEGRACION_FRONTEND.md).

## Partes

| Parte | Estado | Resultado para cerrar |
|---|---|---|
| 0. Preflight y línea base | Cerrada con límites | Verificar merge/rama/árbol; revisar instrucciones, alcance SRS, migraciones, OpenAPI, Compose, suites reales y scripts. Ejecutar `verify` con JaCoCo y `npm run build`/`npm run lint`; registrar salida, conteos y línea base sin asumir que cobertura combinada es cobertura unitaria. |
| 1. Matriz de pruebas y plan de ejecución | Cerrada | Matriz RF/RNF/CA y suites existentes en [matriz de trazabilidad](SPRINT_7_MATRIZ_TRAZABILIDAD_PRUEBAS.md). Distingue evidencia existente de brechas y fija las decisiones para diseñar la parte 2 sin duplicar recorridos cubiertos. |
| 2. Recorridos backend de extremo a extremo | Cerrada con límites | `RecorridoHttpRealIT` ejecuta ambos recorridos con los mismos datos a través de Tomcat en puerto aleatorio y PostgreSQL/Testcontainers; comprueba saldos, kardex, asientos, auditoría, permisos y dashboard. Catálogos, usuarios y cuentas iniciales son fixtures SQL; no cubre navegador, proxy ni despliegue. |
| 3. Consistencia, seguridad y migraciones | Cerrada con límites | [Matriz](SPRINT_7_MATRIZ_TRAZABILIDAD_PRUEBAS.md#parte-3--evidencia-de-consistencia-seguridad-y-migraciones) de rollback, carreras, permisos y contratos; upgrade V24→V25 con datos y permisos previos, esquema limpio y regresión ejecutados en Testcontainers. La base compartida y consumidores externos no se probaron. |
| 4. Cobertura unitaria | Cerrada con límites | 13 pruebas aisladas nuevas y medición reproducible; 16,88 % de líneas unitarias frente al 70 % exigido. Perfil completo y brecha registrados en la parte 4. |
| 5. Rendimiento y concurrencia | Cerrada con límites | Escenario local reproducible, 200 sesiones y evidencia cruda. En el escenario extendido dos consultas superaron p95 de 2 s; la representatividad y carga sostenida aún requieren evaluación. Ver parte 5. |
| 6. Integración frontend y demostración | En curso | Login, sesión y logout conectados y probados en navegador; faltan integración de módulos, permisos por acción, dashboard y exportación. Seguir la [ruta detallada](SPRINT_7_RUTA_INTEGRACION_FRONTEND.md). |
| 7. Documentación y cierre | Pendiente | Actualizar manual técnico, guía de ejecución, matriz de trazabilidad, evidencia de pruebas y guion de demostración. Ejecutar regresión final, revisar diff/secretos/archivos generados y cerrar con límites; no crear commit automáticamente. |

## Hallazgos de preflight

- Git estaba limpio en `integracion`; HEAD `aeb661a` (`feat: dashboards y reportes`) coincide con `develop` y `origin/develop`. No se observó commit de merge adicional: el movimiento publicado fue fast-forward al commit de Reportería. La rama `dash-reports` no está en la lista local consultada.
- Flyway llega a V25. La línea base de este preflight, ejecutada sobre `integracion`, pasó con 91 Surefire + 227 Failsafe; sin fallos, errores ni omitidas, en PostgreSQL 17.11/Testcontainers.
- Backend: Java 21, Spring Boot 4.1.1, Maven Wrapper, Surefire/Failsafe, PostgreSQL 17/Testcontainers, JaCoCo perfil `coverage`.
- Frontend: Node 22.23.3/npm 10.9.9, React/TypeScript/Vite; scripts actuales `build`, `lint` y `dev`, sin script de test ni herramienta e2e.
- `deploy/docker-compose.yml` solo declara PostgreSQL. No hay pipeline en `.github/workflows` ni herramienta/escenario de carga versionados.
- Existe guía de bootstrap y un archivo HTTP manual histórico; revisar vigencia en parte 1. README actualmente enlaza una guía Sprint 1 con un nombre que debe validarse.

## Parte 0 — resultado de preflight (24 nov. 2026)

Comandos ejecutados:

```bash
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -Pcoverage \
  -Dcoverage.jvm.args='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
npm run build
npm run lint
```

Los tres terminaron correctamente. JaCoCo por líneas: Surefire 466/4534 (**10,28 %**), Failsafe 4269/4534 (**94,16 %**) y combinado 4289/4534 (**94,60 %**). Surefire mezcla unitarias y contratos; por tanto no permite afirmar cobertura unitaria exacta. La meta de 70 % sigue pendiente. El frontend compila y pasa lint, pero `App.tsx` es la pantalla demo; no hay prueba frontend ni integración UI con API. Las 1.500 filas/20 solicitudes secuenciales de `CategoriaProductoIntegrationIT` reportan p95 local, pero no representan la meta de 200 usuarios concurrentes.

## Criterios de cierre

- No quedan fallos reproducibles de las suites acordadas ni diferencias de contrato sin explicar.
- Cobertura, rendimiento y concurrencia se informan por separado con sus comandos, entorno, datos y límites. Los objetivos no se declaran cumplidos sin medición representativa.
- Todo flujo marcado integrado conserva evidencia de estado final de PostgreSQL y comprobaciones HTTP/autorización; mocks aislados no sustituyen estas pruebas.
- Documentos, matriz de trazabilidad y procedimiento de demostración coinciden con lo que realmente puede ejecutarse.
- La integración del navegador y metas académicas que dependan de la entrega del frontend se identifican expresamente si no están disponibles.

## Bitácora

| Fecha | Parte | Evidencia | Pendiente |
|---|---|---|---|
| 24 nov. 2026 | Parte 0 cerrada con límites | `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -Pcoverage -Dcoverage.jvm.args='-XX:+UseSerialGC -Xms128m -Xmx768m' verify`: BUILD SUCCESS, 91 Surefire + 227 Failsafe, 0 fallos/errores/omitidas. Cobertura de líneas: 10,28 % Surefire, 94,16 % integración, 94,60 % combinada. `npm run build` y `npm run lint`: éxito; cliente sigue siendo template. `git diff --check`: sin errores. | Parte 1: matriz RF/RNF/CA y huecos reales. No hay integración UI, herramientas e2e frontend ni prueba representativa de carga. |
| 24 nov. 2026 | Parte 1 cerrada | Revisión estática de SRS, cierres y suites; matriz [SPRINT_7_MATRIZ_TRAZABILIDAD_PRUEBAS.md](SPRINT_7_MATRIZ_TRAZABILIDAD_PRUEBAS.md). Se identificaron 31 archivos con MockMvc, ninguno con `TestRestTemplate`, `WebTestClient` o `RANDOM_PORT`; la cobertura existente no se presenta como prueba de socket/red. `git diff --check` y validación de enlaces locales ejecutados al cierre. No se repitieron pruebas de producto: no hubo cambios de código. | Parte 2: diseñar recorrido integrado adicional reutilizando pruebas existentes, decidir si un smoke test con puerto aleatorio aporta valor; preservar fuera de alcance las brechas funcionales conocidas y las dependencias frontend/carga. |
| 24 nov. 2026 | Parte 2 cerrada con límites | `RecorridoHttpRealIT`: orden aprobada por otro usuario, recepción de 20 unidades, factura proveedor por 232 y pago parcial de 100; cotización/venta de tres unidades, factura por 31,32, asiento y CxC, existencias 17, dos movimientos y dashboard incrementado en 200 y 27 sin impuestos. Comprueba 401/403 y auditoría. Regresión focalizada de cuatro clases: 25 pruebas, 0 fallos, errores u omitidas. El esquema aislado evita interferencia con periodos cerrados por otras suites. | Parte 3: consistencia, seguridad y migraciones. El recorrido usa fixtures SQL para catálogos, usuarios y configuración; no demuestra UI, proxy ni rendimiento. |
| 24 nov. 2026 | Parte 3 cerrada con límites | `./mvnw -o verify` con memoria limitada: 91 Surefire + 229 Failsafe, sin fallos, errores u omitidas. Después se añadió la carrera de clasificación y se ejecutó `DashboardOpenApiContractTest` (1) + `AnalyticalReportsApiIT` (4), sin fallos. Upgrade V24→V25 conserva datos representativos y permisos previos, sin inferir políticas. Se documentan límites por regla en la matriz. | Parte 4: cobertura unitaria. No se ensayó migración sobre copia de base compartida ni contrato de todos los consumidores. |
| 24 nov. 2026 | Parte 4 cerrada con límites | Selección unitaria aislada: 61 pruebas, 765/4533 líneas (16,88 %); perfil completo: 104 Surefire + 230 Failsafe sin fallos, Surefire 766/4533 (16,90 %), integración 4267/4533 (94,13 %) y combinada 4293/4533 (94,71 %). Surefire incluye contratos. | Parte 5: rendimiento y concurrencia. La meta unitaria de 70 % sigue incumplida; diferencia de 53,12 puntos porcentuales. |
| 24 nov. 2026 | Parte 5 cerrada con límites | Cinco mediciones locales con 200 sesiones y datos sintéticos; la última registró muestras verificables de 6.400 solicitudes válidas en 44,77 s, con p95 de consultas de 2.074 y 2.049 ms y reportes de 2.270 y 2.150 ms. Detalles, repeticiones y condiciones en parte 5. | Parte 6: integración frontend, dependiente de la entrega del compañero. RNF-02 no se alcanzó en ese escenario local; RNF-03/04 no se acreditan globalmente con esta prueba. |
| 24 nov. 2026 | Parte 6 en curso | Autenticación conectada al backend; prueba de navegador con PostgreSQL y credenciales desechables cubre login, 401/403, expiración, recarga, desconexión/reintento, logout, revocación y pestañas. Véase la [ruta detallada](SPRINT_7_RUTA_INTEGRACION_FRONTEND.md). | Las pantallas de negocio, dashboard/exportación y configuración de despliegue siguen pendientes de integración y validación. |

Comando reproducible de la parte 2 desde `erp-backend/`:

```bash
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o \
  -Dtest=NoSuchTest -Dsurefire.failIfNoSpecifiedTests=false \
  -Dit.test=RecorridoHttpRealIT,ConfirmarRecepcionIT,FacturaApiIT,DashboardApiIT verify
```

Esta selección ejecuta Failsafe para las cuatro clases y omite deliberadamente la selección de pruebas Surefire; no sustituye la regresión completa prevista para el cierre del sprint. La primera ejecución conjunta falló al reutilizar un periodo cerrado por otra suite; se aisló el esquema del caso nuevo y se repitió con el resultado indicado.

Comandos de la parte 3 desde `erp-backend/`:

```bash
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o verify
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o \
  -Dtest=DashboardOpenApiContractTest -Dit.test=AnalyticalReportsApiIT verify
```

El segundo comando incorpora la prueba de concurrencia añadida después de iniciar el primero. Una ejecución preliminar de reportería esperaba 500 ante una restricción de auditoría; el manejador existente devuelve 409 por violación de integridad. Se corrigió la expectativa y se confirmó el rollback y la ausencia de descarga en las ejecuciones posteriores.

## Parte 4 — medición unitaria aislada

La selección de Surefire usa `*Test` y excluye `*OpenApiContractTest`; inspeccionadas las clases, no inicia contexto Spring ni Testcontainers. Incluye pruebas de validación y manejo de errores aisladas, además de reglas de dominio y servicios con dependencias simuladas. JaCoCo cuenta **todas las clases de producción** en el denominador, incluidos controladores e infraestructura; no se han excluido paquetes para aumentar el porcentaje. Las llamadas directas no ejercitan el interceptor de `@PreAuthorize` ni transacciones; la parte 3 contiene pruebas de integración para esas fronteras.

El contador de líneas registra ejecución, incluida la inicialización de clases que puede ocurrir al crear mocks; no demuestra por sí solo que cada línea ejecutada tenga una aserción funcional.

Antes del cambio, la selección ejecutó 48 pruebas y cubrió **465/4533 líneas (10,26 %)**. Después, 61 pruebas cubrieron **765/4533 (16,88 %)**, un aumento de 300 líneas y 6,62 puntos porcentuales. La meta RNF-12 de 70 % sigue sin cumplirse: faltan 53,12 puntos porcentuales. Las pruebas nuevas verifican transiciones y totales de compra, vigencia y estados de venta, selección de fuentes autorizadas del dashboard, fechas y permisos, formato CSV/auditoría, y resultado contable disponible o ausente. Los dobles aíslan servicios; las pruebas de PostgreSQL no se incluyen en esta cifra.

Para reproducir desde `erp-backend/` y consultar `target/site/jacoco-unit/jacoco.xml`:

```bash
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -Pcoverage \
  -Dcoverage.jvm.args='-XX:+UseSerialGC -Xms128m -Xmx768m' \
  '-Dtest=*Test,!*OpenApiContractTest' -DskipITs package
```

El perfil completo usa la misma ruta `jacoco-unit` para el informe Surefire, que también incluye contratos; por ello sobrescribe el informe aislado. Con `-DskipITs package` solo se interpreta el informe unitario: no se genera evidencia nueva de integración ni combinada.

El perfil completo terminó con **BUILD SUCCESS**, 104 pruebas Surefire y 230 Failsafe, sin fallos, errores ni omitidas. Líneas: Surefire 766/4533 (16,90 %, incluye contratos), Failsafe 4267/4533 (94,13 %) y combinada 4293/4533 (94,71 %). Se ejecutó desde `erp-backend/`:

```bash
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -Pcoverage \
  -Dcoverage.jvm.args='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

## Parte 5 — rendimiento y concurrencia (24 nov. 2026)

Se utilizó el [generador reproducible](../../tools/performance/README.md), con HTTP local, servidor Java y PostgreSQL 17.11 desechable, en la misma máquina Linux compartida: Ryzen 7 7730U, 16 CPU lógicas visibles, 15.740.740 KiB de memoria total, Java 21.0.12.1, SerialGC, heap máximo 768 MiB, pool JDBC predeterminado y PostgreSQL `max_connections=100`. El JAR corresponde al árbol local sobre HEAD `aeb661a`; su SHA-256 está en las evidencias posteriores a la primera ejecución. No se modificó la base de desarrollo. Las sesiones se precargaron para 200 usuarios distintos y se verificó 401 sin token.

El dataset contiene 1.000 categorías, 10.000 productos, tres almacenes, 30.000 existencias y 30.000 mínimos; 12.114 pares están bajo mínimo y 2.422 productos en alerta global. No hay movimientos ni facturas. El generador comprueba totales y valores esperados de las respuestas. Cada etapa usa una barrera de inicio, 1, 50 o 200 usuarios y conexiones persistentes, sin pausas; es una carga cerrada. La cifra de solicitudes en vuelo es observada por el cliente, incluye espera y no indica procesamiento simultáneo en el servidor.

Última ejecución extendida: 32 solicitudes por usuario, **6.400 solicitudes** con 200 sesiones, **44,77 s**, pico cliente 200 en vuelo, **0 errores ni respuestas inválidas**. Por ruta:

| Ruta | p95 | Correctas bajo umbral / total | Lectura para este escenario |
|---|---:|---:|---|
| Categorías | 2.074,336 ms | 1.485/1.600 (92,81 %) bajo 2 s | No alcanza RNF-02 en esta carga local. |
| Productos | 2.049,002 ms | 1.504/1.600 (94,00 %) bajo 2 s | No alcanza RNF-02 en esta carga local. |
| Stock bajo | 2.269,992 ms | 1.600/1.600 (100 %) bajo 5 s | Cumple el umbral en esta muestra. |
| Dashboard | 2.150,192 ms | 1.600/1.600 (100 %) bajo 5 s | Cumple el umbral en esta muestra con dos indicadores. |

Con 50 usuarios, los p95 de esas rutas fueron respectivamente 491, 513, 678 y 573 ms; las 1.600 solicitudes de la etapa fueron válidas y quedaron bajo sus umbrales. Los p95 se calculan por nearest rank solo con respuestas válidas; los porcentajes de la tabla usan todas las solicitudes como denominador. Se recalcularon los p95 y conteos de cada ruta desde las [muestras anónimas](evidencias/rendimiento-local-2026-11-24-muestras.json). La ejecución extendida previa está en [agregados](evidencias/rendimiento-local-2026-11-24-extendida.json); las tres repeticiones cortas están en [inicial](evidencias/rendimiento-local-2026-11-24.json), [réplica 1](evidencias/rendimiento-local-2026-11-24-replica-1.json) y [réplica 2](evidencias/rendimiento-local-2026-11-24-replica-2.json). En las tres, los p95 de ambas consultas con 200 usuarios también excedieron 2 s. La primera evidencia precede a la adición de metadatos y las cuatro primeras contienen solo agregados.

Para reproducir la ejecución y verificar sus muestras, desde la raíz y usando un nombre de salida que no exista:

```bash
(cd erp-backend && MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DskipTests package)
python3 tools/performance/medir_backend.py --rounds 8 --output /tmp/techsupply-resultado.json
python3 tools/performance/verificar_evidencia.py /tmp/techsupply-resultado.json
```

El verificador también rechazó en una comprobación local una copia temporal con p95 alterado. El empaquetado omite pruebas; no sustituye la regresión de la parte 4. `git diff --check` y 115 enlaces locales revisados no mostraron errores. Los contenedores temporales de estas ejecuciones se eliminaron al terminar; los existentes del usuario siguieron en ejecución.

Esto **no acredita RNF-03/04 en general**: es una ejecución corta, con usuarios que solo leen, sin login durante carga, sin facturas ni kardex histórico, reportes complejos, escrituras, navegador ni red externa. Tampoco demuestra disponibilidad ni una capacidad sostenida. Antes de reclamar conformidad se necesita acordar un dataset y entorno representativos, medir la carga sostenida y perfilar las colas de aplicación y base para explicar los p95. No se atribuye una causa ni se cambian parámetros de producción con esta evidencia.
