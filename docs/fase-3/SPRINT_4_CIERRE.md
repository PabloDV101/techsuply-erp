# Sprint 4 — cierre del backend de Contabilidad

**Alcance:** partes 0–16 de la [ruta de Contabilidad](SPRINT_4_CONTABILIDAD_POR_PARTES.md). Este cierre registra el incremento backend comprobado y sus límites; no certifica todos los módulos del ERP ni una operación en una base compartida.

## Estado de Git y migraciones

La revisión de la parte 16 se hizo en `contabilidad`, HEAD `c3b08b8`, creado desde `develop`. El árbol contiene los cambios acumulados sin confirmar del sprint. `main` sigue separada y no se modificó. Flyway contiene V1–V24 consecutivas; V14–V24 pertenecen al incremento contable. Las pruebas de migración cubren instalación desde cero y actualizaciones aisladas V13→V14, V21→V23 y V23→V24 en PostgreSQL/Testcontainers. No se consultó ni migró una base compartida; antes de desplegar se debe revisar su historial Flyway y respaldos.

## Trazabilidad y evidencia funcional

| Requisito | Estado del incremento | Evidencia principal y límite |
|---|---|---|
| RF-CON-01 | Parcial | API de cuentas y periodos, jerarquía, solapamiento y cierre; `CuentaContableApiIT`, `PeriodoContableApiIT`. Sin reapertura aprobada. |
| RF-CON-02, CA-05 | Implementado en asientos manuales | Borradores, partidas, balance, contabilización y rechazo en periodo cerrado; `AsientoContableApiIT`. |
| RF-CON-03 | Parcial | Contraasiento, idempotencia y origen; V17 factura de venta con outbox/reintento; V20 fuentes valorizadas atómicas. `AsientoContableApiIT`, `FacturaApiIT`, `ConfirmarRecepcionIT`, `ConfirmarDespachoIT`, `AplicarAjusteIT`. Facturas históricas y fuentes futuras fuera de alcance. |
| RF-CON-04 | Parcial | V21 factura de proveedor conciliada, asiento y CxP; `ConfirmarRecepcionIT`. Cancelación y diferencias negociadas pendientes. |
| RF-CON-05 | Parcial | V18 CxC desde factura nueva contabilizada, saldo derivado por aplicaciones V19; `FacturaApiIT`. Históricas pendientes. |
| RF-CON-06, CA-04 | Parcial | V19 cobros/aplicaciones y V22–V23 pagos/aplicaciones con control de proveedor/cliente, saldo, concurrencia y asientos; `FacturaApiIT` y pruebas de pagos en `erp-backend/src/test/java/com/techsupply/erp_backend/contabilidad`. Sin conciliación bancaria ni reversión operativa de cobros/pagos. |
| RF-CON-07 | Parcial | Mayor y balance de prueba V16, resultados/balance general y flujo indirecto condicionado por clasificación V24; `ReportesContablesApiIT`. Flujo `NO_DISPONIBLE` ante evidencia incompleta; no hay clasificación automática ni desglose detallado del ajuste operativo. |
| RF-CON-08 | Futuro | Presupuestos sin reglas ni fuente aprobadas. Sin implementación operativa. |

La suite HTTP/PostgreSQL cubre el recorrido compra→recepción→factura de proveedor/CxP, el recorrido cotización→pedido→despacho→factura de venta y, en pruebas separadas, la integración factura→asiento/CxC, cobro/aplicación, pago de proveedor y estados financieros. No hay una única prueba de extremo a extremo que atraviese todos los módulos y reportes, ni evidencia de frontend en navegador o sistemas externos.

## Verificaciones de la entrega

El 19 nov. 2026, `clean verify` con perfil `coverage` terminó con `BUILD SUCCESS`: **86 Surefire + 216 Failsafe (302 pruebas)**, 0 fallos, 0 errores y 0 omitidas. JaCoCo midió líneas Java: Surefire **466/4263 (10,93 %)**, integración **4031/4263 (94,56 %)** y unión **4051/4263 (95,03 %)**. Surefire incluye pruebas de contrato, así que 10,93 % no representa cobertura unitaria pura y tampoco acredita el objetivo de 70 % unitario.

Se validaron nueve contratos OpenAPI (38 rutas del contrato contable), las dos copias DBML idénticas (80 tablas/182 relaciones), las 59 fuentes PlantUML y 224 enlaces locales en los nueve documentos de orientación y cierre revisados. `git diff --check` terminó sin errores. Los SVG de clase y proceso de Contabilidad se regeneraron desde sus fuentes. El validador DBML contrasta V24 en columnas, tipos, nulidad y relaciones; exclusiones temporales y triggers los verifica PostgreSQL en la suite, no el parser DBML.

El comando de regresión y cobertura para reproducir desde `erp-backend/` es:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -Pcoverage \
  -Dcoverage.jvm.args='-XX:+UseSerialGC -Xms128m -Xmx768m' clean verify
```

La validación de contratos, modelo y diagramas se reproduce desde la raíz del repositorio:

```sh
SWAGGER_PARSER_PATH=/tmp/techsupply-openapi-tools/node_modules/@apidevtools/swagger-parser node docs/api/validar-openapi.cjs
DBML_CORE_PATH=/tmp/techsupply-diagram-tools/node_modules/@dbml/core node docs/diagrams/validar-modelo.cjs
python3 docs/diagrams/renderizar.py --jar /home/pablodev/.local/bin/plantuml-1.2026.7.jar --salida /tmp/techsupply-contabilidad-cierre-uml
git diff --check
```

Los paquetes temporales de Node y el JAR de PlantUML se describen en los README de [API](../api/README.md) y [diagramas](../diagrams/README.md); esas rutas temporales pueden no existir en otro equipo.

## Seguridad, integridad y límites de entrega

Los endpoints implementados verifican permisos en backend y las pruebas HTTP comprueban autenticación/autorización en los flujos principales. Las operaciones contables usan transacciones y auditoría; los tests prueban fallos con rollback, reintentos y carreras seleccionadas. Una búsqueda heurística de claves privadas y asignaciones literales de credenciales en los 146 archivos de texto cambiados no encontró candidatos. Esa búsqueda no sustituye una revisión especializada de secretos ni un análisis estático completo.

El incremento aún no acredita la meta de cobertura unitaria del proyecto, la rúbrica de siete componentes reutilizables con tres contextos, portabilidad a otro proyecto, 200 usuarios concurrentes, disponibilidad de 99,5 %, latencia de consultas/reportes ni integración real con React, CORS, TLS/red o despliegue. Los SVG y contratos son documentación, y las pruebas con Testcontainers no prueban migraciones en un entorno compartido. La confirmación de cobros/pagos es operativa; no demuestra movimiento bancario externo.

**Entrega:** revisar el diff completo y los 131 archivos sin seguimiento antes de preparar un commit; corresponden al trabajo acumulado del sprint y no se confirmó ninguno automáticamente. El incremento backend puede revisarse como tal con estas limitaciones expresas. No recomendar desplegar ni presentar RF-CON-01–08 como completos sin resolver los puntos anteriores.
