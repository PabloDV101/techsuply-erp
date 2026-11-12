# Sprint 2 — cierre de Inventario backend

**Estado:** incremento backend listo para revisión y commit en la rama `inventario`; no equivale a la entrega completa del ERP ni a una validación de despliegue. Este documento registra evidencia y límites para retomar el trabajo. Los cambios no han sido confirmados por el agente.

## Alcance entregado

- Consulta paginada y protegida de existencias y movimientos; el kardex identifica la fuente de recepción o ajuste.
- Conteos por almacén con captura de cantidad y versión, cierre/cancelación y auditoría; el conteo no modifica stock.
- Solicitud de ajuste con idempotencia, autorización por usuario distinto, aplicación por actor autorizado, control de reserva, validación de conteo vigente y registro transaccional de stock, costo, movimiento y auditoría. Los reintentos de aplicación no duplican movimientos.
- Alerta global calculada de stock bajo, sin persistencia ni creación de órdenes. El umbral cero la deshabilita y se suman las existencias disponibles de almacenes activos.
- V9 crea tablas y permisos de conteos y ajustes y habilita la fuente de ajuste; V10 protege el historial mediante triggers y verifica atributos de nuevas fuentes. No se editaron V1–V8. La actualización V8→V9→V10 con datos se comprobó en PostgreSQL desechable.
- Contrato y guía de consumo para el compañero: [OpenAPI de Inventario](../api/openapi-sprint-2-inventario.yaml) y [guía de integración](../api/guia-sprint-2-inventario.md). El contrato independiente de Reportería sigue siendo diseño.

La [bitácora por partes](SPRINT_2_INVENTARIO_POR_PARTES.md), el [diseño y trazabilidad](SPRINT_2_DISENO_INVENTARIO.md), el [SRS](../SRS_TechSupply_Solutions.md), las dos copias del DBML, UML y el documento de [patrones y programación funcional](../PATRONES_Y_PROGRAMACION_FUNCIONAL.md) reflejan el alcance y sus límites.

## Verificaciones y seguridad

- Regresión de la parte 12: **57 pruebas Surefire y 101 Failsafe**, sin fallos. La prueba vertical se reforzó después y volvió a pasar junto con tres pruebas de contrato. Usa MockMvc y PostgreSQL de Testcontainers; no es una prueba de navegador ni de red real.
- DBML: 71 tablas y 141 relaciones, copias idénticas y comparación de esquema documentado con migraciones. Esta validación no sustituye las pruebas PostgreSQL de CHECK y triggers.
- Siete contratos OpenAPI, 42 diagramas PlantUML y enlaces locales a archivos de los documentos del sprint validados. No se validaron automáticamente todos los fragmentos o anclas de Markdown.
- `git diff --check` sin hallazgos; archivos nuevos revisados para formato y artefactos de compilación. `target/`, reportes JaCoCo y `.env` locales están ignorados; `.env.example` es el archivo de ejemplo rastreado.
- `detect-secrets` 1.5.0 recibió 338 rutas rastreadas y nuevas no ignoradas, sin verificar valores contra servicios externos ni imprimir posibles secretos. Sus filtros omiten binarios u otros archivos no analizables. Dos coincidencias se revisaron como falsos positivos: un ejemplo de contraseña incorrecta en una prueba HTTP y el digest conocido de una prueba SHA-256. Un control sintético en archivo no rastreado confirmó su inclusión. Es un análisis heurístico de la instantánea actual, no una garantía absoluta ni un análisis del historial Git.
- La rama actual estaba al mismo commit que la referencia local `origin/develop` al cierre; no se actualizó la referencia remota mediante `fetch`, por lo que no se afirma que refleje cambios remotos posteriores.

Desde `erp-backend/`, con Java 21, Docker y dependencias disponibles:

```bash
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o \
  -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

Para repetir el escaneo de la instantánea desde la raíz, con Python y acceso a paquetes la primera vez, se puede usar un entorno temporal. El JSON se mantiene fuera del repositorio y debe revisarse sin publicar sus contenidos:

```bash
python3 -m venv /tmp/techsupply-secrets-venv
/tmp/techsupply-secrets-venv/bin/pip install detect-secrets==1.5.0
git ls-files --cached --others --exclude-standard -z | \
  xargs -0 /tmp/techsupply-secrets-venv/bin/detect-secrets scan --no-verify > /tmp/techsupply-secrets-result.json
```

Los comandos de OpenAPI, DBML y PlantUML están en [API](../api/README.md) y [diagramas](../diagrams/README.md). La última medición de cobertura y el perfil reproducible están en el [diseño del sprint](SPRINT_2_DISENO_INVENTARIO.md#parte-11--informes-y-medición-de-cobertura) y el [README](../../README.md#ejecutar-y-verificar-el-backend).

## Límites y decisiones pendientes

- La medición Surefire fue **13,96 % de líneas**, incluyendo pruebas de contrato; no acredita el objetivo del 70 % de cobertura unitaria. El 95,38 % combinado es una medición de la ejecución Java, no una garantía de calidad de aserciones, de SQL ni de cumplimiento funcional. La medición corresponde a la parte 11, antes de añadir la prueba vertical de la parte 12.
- RF-INV-02 y RF-INV-07 siguen parciales: faltan despachos de Ventas, devoluciones, reversas, rotación y obsolescencia; estos informes requieren fuentes y reglas aprobadas. También siguen pendientes requisiciones, facturas, devoluciones y corrección/cancelación de recepciones pendientes de Compras.
- No se ha integrado ni probado el frontend React, CORS, HTTP de red, TLS ni un despliegue compartido. Tampoco se han medido el objetivo de 200 usuarios, los tiempos de respuesta o la disponibilidad.
- Los triggers protegen DML ordinario, pero el propietario/administrador puede alterarlos. No protegen frente a cambios SQL directos posteriores en documentos de Compras y no corrigen inconsistencias históricas. Separar la identidad Flyway/propietario de la identidad operativa y aplicar privilegios mínimos sigue pendiente de configuración del entorno.
- V9/V10 adquieren bloqueos de tabla; antes de aplicarlas a una base compartida, comprobar historial Flyway, ventana y respaldo conforme al entorno. No reinicializar ni modificar migraciones ya aplicadas. Una versión con el mismo número aplicada por otro trabajo requiere coordinación antes de desplegar.
- La denegación se audita con política de mejor esfuerzo. La consulta de alertas materializa coincidencias y no se ha probado con volúmenes representativos.

El siguiente trabajo debe planificarse con el equipo: integrar el frontend y mejorar pruebas unitarias significativas, y decidir la prioridad de Ventas o de los pendientes de Compras antes de asignar un nuevo sprint. No se debe presentar el incremento como siete módulos completos ni asignarle una calificación de rúbrica no verificada.

## Preparación de commit

La revisión corresponde al árbol de trabajo y los archivos nuevos actuales. Antes de confirmar, inspeccionar `git status --short`, preparar únicamente los cambios del sprint y revisar `git diff --cached --stat` y `git diff --cached --check`. El escaneo no cubre cambios añadidos después de esta revisión; repetirlo si se incorporan archivos. No se creó commit, push ni merge.
