# Cierre de sprint 5 — Reportería y dashboards

**Fecha:** 24 de noviembre de 2026  
**Rama revisada:** `dash-reports`, HEAD `dc89bcb` (`feat: Contabilidad`)  
**Estado:** alcance backend aprobado implementado y verificado con límites. No se creó commit.

## Entregado

- Dashboard protegido por permisos de fuente con facturación de Compras y Ventas, alerta global de Inventario y resultado contable; lectura coherente `REPEATABLE_READ` y CSV dedicado auditado.
- Consultas analíticas para mínimos explícitos de stock por producto/almacén, inactividad, rotación, ABC, rentabilidad bruta por cliente y margen operativo. Filtros, paginación y exportación CSV/PDF/XLSX por página, con permiso y auditoría.
- V25 crea mínimos por almacén y clasificaciones de margen con vigencia, restricciones contra solapamientos y anulación auditada. No copia mínimos globales ni clasifica historia automáticamente.
- OpenAPI, guía de integración, SRS, ruta general, diseño y cierre del sprint, documentación del componente, DBML canónico y copia, validador y diagramas PlantUML/SVG actualizados.

## Reglas y límites funcionales

- Rotación: unidades despachadas / existencia física media diaria. Sin cobertura de kardex conciliada, ante saldo negativo o denominador cero, el resultado es `NO_DISPONIBLE`.
- ABC: costo de despachos confirmados; orden descendente por costo con desempate por producto; cortes acumulados 80/95 %.
- Rentabilidad: subtotal de factura emitida (descuentos incluidos, impuestos excluidos) menos costos conciliados de los despachos del pedido. No es rentabilidad neta.
- Margen: ingresos operativos menos costos y gastos operativos sobre ingresos operativos, con clasificación contable explícita por vigencia; no se infiere el histórico.
- Los productos nunca registrados se distinguen de los que tuvieron movimiento; no se los denomina obsoletos. Los mínimos por almacén solo existen para pares configurados.
- Nómina y RF-REP-05 quedaron fuera del sprint por decisión del usuario. No se implementó RR. HH. como dependencia.

## Evidencia ejecutada

Desde `erp-backend/`:

```bash
MAVEN_OPTS='-XX:+UseSerialGC' ./mvnw -o verify
```

Resultado final: **BUILD SUCCESS**; Surefire: 91 pruebas, Failsafe: 227 pruebas; cero fallos, errores u omisiones. Se usó PostgreSQL 17.11 con Testcontainers. El primer recorrido completo encontró cinco aserciones antiguas que esperaban exactamente 24 migraciones; se actualizaron a 25 y el recorrido completo posterior pasó. La prueba específica de upgrade V24→V25 siguió comprobando que se ejecuta una migración.

Desde la raíz:

```bash
SWAGGER_PARSER_PATH=/tmp/ts-report-validate/node_modules/@apidevtools/swagger-parser \
  node docs/api/validar-openapi.cjs
DBML_CORE_PATH=/tmp/ts-report-validate/node_modules/@dbml/core \
  node docs/diagrams/validar-modelo.cjs
java -Djava.awt.headless=true \
  -jar /home/pablodev/.local/bin/plantuml-1.2026.7.jar \
  -charset UTF-8 -tsvg -o /tmp/techsupply-sprint5-close-uml \
  docs/diagrams/clases/06-reporteria-auditoria-integracion.puml \
  docs/diagrams/casos-uso/reporteria.puml \
  docs/diagrams/procesos/08-proceso-reporteria.puml
git diff --check
```

Resultados: nueve contratos OpenAPI válidos (Reportería: 21 rutas); DBML válido, 82 tablas y 186 relaciones, copias idénticas y comprobación V25; los tres diagramas compilaron/renderizaron y sus SVG están versionados; `git diff --check` sin errores.

## Límites que permanecen

- No se midió `EXPLAIN (ANALYZE, BUFFERS)`, latencia con datos representativos, p95 de reportes ni 200 usuarios concurrentes; no se afirma cumplimiento de esos RNF.
- No se probó la integración del compañero frontend en navegador.
- No hay devoluciones/reversas de despacho ni una reconstrucción fiable de toda la historia anterior al sistema; las consultas afectadas reportan falta de disponibilidad cuando detectan evidencia insuficiente.
- No se validó ni migró una base compartida; V25 se ejecutó en Testcontainers. Antes de desplegar, revisar historial Flyway y procedimiento del entorno destino.
- No se volvió a medir JaCoCo en este cierre. La cobertura unitaria bajo 70 %, reutilización de siete componentes en tres contextos y portabilidad de la rúbrica continúan sin demostración.
- CSV/PDF/XLSX exporta la página solicitada, no todas las páginas en una descarga única. No se creó un motor libre de definición de reportes ni se materializan ejecuciones/indicadores genéricos.

La planificación y bitácora por parte están en [SPRINT_5_REPORTERIA_POR_PARTES.md](SPRINT_5_REPORTERIA_POR_PARTES.md); las fórmulas y contratos se detallan en [SPRINT_5_DISENO_REPORTERIA.md](SPRINT_5_DISENO_REPORTERIA.md). La rama queda con cambios locales sin confirmar, lista para revisión; no se hizo commit.
