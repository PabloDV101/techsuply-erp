# Sprint 4 — Contabilidad backend, por partes

**Estado:** partes 0–16 cerradas con límites. V20 integra recepción de Compras, despacho de Ventas y ajustes de Inventario con asientos atómicos; V21 incorpora factura de proveedor y CxP; V22–V23 incorporan pagos a proveedores; V24 habilita clasificación explícita y reportes financieros parciales, incluido flujo indirecto condicionado por evidencia. Facturas históricas y reportes fiscales siguen pendientes. Las partes son puntos de control y no garantizan que el alcance completo quepa en una semana. El frontend lo desarrolla otro integrante.

## Cómo continuar

Al recibir «siguiente parte», revisar primero esta ruta, [AGENTS.md](../../AGENTS.md), la [ruta backend](../FASE_3_RUTA_BACKEND.md), el [SRS](../SRS_TechSupply_Solutions.md), el [DBML canónico](../../database/modelo.dbml), los diagramas, Git y el código afectado. Ejecutar solo la siguiente parte pendiente, preservar cambios ajenos y registrar comandos y resultados reales. No implementar reglas económicas aún no acordadas ni hacer commit, push o merge sin solicitud.

## Punto de partida y dependencias

- `contabilidad` se creó desde `develop` en `c3b08b8`; antes del preflight el árbol estaba limpio. El backend declara Java 21 y Spring Boot 4.1.1. Al iniciar contenía V1–V13; V14 incorpora la estructura contable acotada descrita en [migraciones](SPRINT_4_MIGRACIONES.md). No se ha consultado el historial de ninguna base compartida.
- Existe flujo backend de Compras y recepción con Inventario, y de cliente a factura interna de Ventas con reserva y despacho. La factura no acredita pago ni CFDI; la modalidad de contado no implica cobro. V18 crea el auxiliar y V19 registra cobros en una operación independiente.
- V14 implementa la estructura SQL básica de cuentas, periodos, asientos y partidas; V17 añade mapeo de cuentas de venta y outbox; V18 crea el auxiliar por cliente al contabilizar facturas nuevas. La parte 9 crea asiento balanceado desde la factura con permiso y reintento explícito; la parte 10 agrega consulta del auxiliar sin registrar cobro ni costo de ventas. Los pagos a proveedores y las aplicaciones de cobro están implementados dentro de los límites documentados. El contrato OpenAPI de Reportería es parcial: el balance de prueba está implementado y las demás rutas siguen siendo diseño. El DBML incluye diseño futuro y no reemplaza las migraciones.
- La revisión de `RecepcionCompraService`, `DespachoService` y `FacturaService` identifica operaciones transaccionales con permisos y auditoría; las dos primeras coordinan `InventarioService`, que exige la transacción del llamador para movimientos. La integración contable conserva esos límites: factura y outbox son atómicos; cobro/aplicación y asiento son atómicos; fallos revierten únicamente la operación contable en curso.
- V21 incorpora facturas de proveedor validadas y CxP; V22–V23 agregan pagos a proveedores. Requisiciones, devoluciones y recuperación de recepciones siguen pendientes. Nómina, crédito operativo, integración frontend y reportes financieros no se presuponen implementados.
- La rúbrica pide siete componentes con tres contextos para el nivel superior; esto y la portabilidad a otro proyecto no están demostrados. La medición histórica de la parte 13 de Ventas fue 12,78 % unitaria, 89,50 % integración y 90,12 % combinada; el 70 % unitario sigue pendiente. No confundir cobertura con cobertura funcional.

La revisión de `RecepcionCompraService`, `DespachoService`, `AjusteInventarioService` e `InventarioService` estableció los límites transaccionales de la parte 12: origen, stock, kardex, auditoría y asiento se confirman juntos; ante una validación contable fallida toda la operación vuelve al estado previo para reintento.

La revisión de [la narrativa](../rubrica/proyecto%20final%20dise%C3%B1o.pdf) y la [rúbrica](../rubrica/rubrica_final.pdf) no certifica los siete módulos ni calcula un porcentaje funcional: Gestión Maestra, Compras, Inventario y Ventas tienen alcances parciales; Seguridad tiene autenticación, permisos y auditoría backend; Contabilidad y Reportería carecen de sus funcionalidades centrales. RR. HH. aparece en la narrativa y el SRS, pero no tiene implementación operativa demostrada. Tampoco se han acreditado carga, despliegue, navegador o integración real de frontend.

## Partes y puertas de salida

Cada historia incluye autorización backend, auditoría, validaciones, errores coherentes y pruebas según riesgo. Cambios de esquema usan migraciones nuevas y actualizan ambas copias DBML, restricciones, índices y documentación. Cambios de comportamiento actualizan SRS, OpenAPI, ejemplos, UML PlantUML y SVG afectados, con trazabilidad y validación. Los nombres y números definitivos se fijan después de revisar el estado real.

| Parte | Alcance | Evidencia y dependencias |
|---|---|---|
| 0. Preflight | Verificar rama, código, documentación, migraciones, entorno y línea base; registrar ruta. | Regresión y validadores disponibles; anotar anomalías y límites. |
| 1. Diseño y políticas | Definir alcance, estados, cuentas configurables, origen, fechas, cierres, reversión, idempotencia, integración, errores y contratos propuestos; revisar datos históricos. | Matriz RF-CON-01–08, reglas y pruebas. Distinguir decisiones aprobadas y propuestas; consultar las decisiones imprescindibles antes de habilitar operaciones dependientes. |
| 2. Migraciones y modelo del núcleo | Diseñar cuentas, periodos, asientos y partidas; migraciones nuevas, permisos, restricciones e índices; actualizar DBML y UML. | PostgreSQL desde cero y actualización desde V13 con datos; FK, unicidad, restricciones y conservación de historia. |
| 3. Dominio y persistencia | Modelos, repositorios y reglas de jerarquía, periodos, partidas y equilibrio. | Unitarias de reglas y persistencia real con PostgreSQL y validación JPA. |
| 4. Plan de cuentas | API protegida para cuentas jerárquicas, consultas y administración, con auditoría. | Unicidad, ciclos, cuentas agrupadoras/inactivas, historia, permisos y concurrencia. |
| 5. Periodos | Administración y cierre, fechas y solapamiento; política de reapertura si se acuerda. | Carrera entre cierre y contabilización, límites de fechas, permisos y auditoría. |
| 6. Asientos manuales | Crear y consultar borradores y partidas; conservar referencias y validar entradas. | HTTP, cuentas, periodo, precisión, autorización y exclusión de borradores de reportes definitivos. Cerrada en el workspace; evidencia abajo. |
| 7. Contabilización y reversión | Contabilizar asientos equilibrados en periodo abierto y corregir mediante contraasiento auditado; editar/cancelar borradores con motivo para permitir cierres. | CA-05, idempotencia de reversión, concurrencia, rollback y protección de historia; al menos dos partidas. Fecha explícita de contraasiento en cualquier periodo abierto; no se reabre el original. |
| 8. Mayor y balance de prueba | Consultas protegidas y documentadas sobre asientos contabilizados y contraasientos, con filtros y paginación. | Cerrada: balance de prueba y mayor, apertura, movimientos y cierre, permiso propio y conciliación verificada en PostgreSQL. |
| 9. Integración de factura de Ventas | Cerrada con límites: V17 crea outbox con emisión; cuentas configurables; reintento protegido crea y contabiliza asiento con origen VENTAS/FACTURA_VENTA. | Fecha de emisión si está abierta; fecha explícita si no. Importe por cobrar, venta e impuestos; factura permanece emitida ante fallo posterior. No efectivo ni costo de ventas. Históricas excluidas. |
| 10. Cuentas por cobrar | Cerrada con límites: V18 crea un auxiliar inmutable y consultable por factura solo al publicar el asiento VENTAS/FACTURA_VENTA. El saldo se deriva; no se persiste duplicado. | Unicidad por factura, cliente/importe/fechas conciliados, atomicidad con asiento, permiso, paginación y reintentos. No se procesan históricos ni se registran cobros. |
| 11. Cobros y aplicaciones | Implementada con V19: registrar, confirmar, y aplicar parcial o totalmente al mismo cliente; contabilizar fondos/anticipos y reclasificaciones. | CA-04, exceso de saldo/disponible, atomicidad, idempotencia, periodo abierto, configuración, permisos y auditoría. Sin crédito operativo ni conciliación bancaria externa. |
| 12. Compras e Inventario | Cerrada con V20: recepción aceptada, despacho confirmado y ajustes aplicados generan asientos en la misma transacción que el movimiento operativo. | Costos persistidos, centavos exactos, periodo abierto y rollback conjunto; regresión y contratos, DBML, PlantUML, enlaces y diff validados. |
| 13. Factura proveedor y cuentas por pagar | V21 registra y corrige una factura por orden; la validación exacta con orden, recepciones y kardex crea asiento y auxiliar CxP atómicos. | Pruebas HTTP/PostgreSQL, contratos, DBML, UML y regresión final; pagos y cancelación fuera de alcance. |
| 14. Pagos a proveedores | Cerrada con límites: registro y confirmación con aplicaciones al mismo proveedor y asiento de CxP/fondos. | Regresión, migración V21→V23, concurrencia, idempotencia y conciliación verificadas; sin reversión de pagos ni conciliación bancaria. |
| 15. Estados financieros | Estado de resultados, balance general y flujo indirecto basado en clasificación explícita y fuentes reales. | Cerrada con límites: reportes HTTP protegidos; flujo `NO_DISPONIBLE` cuando falta clasificación, cobertura o conciliación; pruebas de saldos, reversión, periodos y clasificación. |
| 16. Integración y cierre | Revisar entrega acumulada del sprint, documentación, trazabilidad, calidad y límites. | Cerrada con límites en el [cierre](SPRINT_4_CIERRE.md): regresión, JaCoCo, contratos, DBML, UML, enlaces, Git, migraciones y riesgos. RF-CON-01–08 no están completos; la cobertura unitaria sigue debajo de meta. |

## Decisiones y riesgos abiertos

Quedan pendientes el procesamiento explícito de facturas históricas, clasificación automática e informes de cumplimiento fiscal. Las reglas aprobadas para nuevas facturas de Ventas, cobros, Compras/Inventario, facturas de proveedor y pagos ya están implementadas dentro de sus límites; la recuperación es mediante reintento explícito, sin worker periódico. CFDI, presupuestos, nómina, crédito, reportes de antigüedad CxP/CxC, conciliación bancaria externa y carga requieren alcance y evidencia propios. Flujo indirecto exige clasificación manual revisada y conciliación; no reemplaza una conciliación bancaria.

## Bitácora

| Parte | Estado | Evidencia | Siguiente paso |
|---|---|---|---|
| 0 | Cerrada | 12 nov. 2026, `contabilidad` en `c3b08b8`: Java 21, PostgreSQL 17/Testcontainers; regresión 72 Surefire + 183 Failsafe sin fallos ni omisiones. DBML, ocho contratos, ejemplos, 50 fuentes PlantUML y enlaces de documentos afectados validados. | Parte 1: diseño, políticas y contratos; resolver las decisiones imprescindibles antes de implementar sus efectos. |
| 1 | Cerrada como diseño con decisiones pendientes | 12 nov. 2026, `contabilidad`: [diseño y trazabilidad](SPRINT_4_DISENO_CONTABILIDAD.md), [OpenAPI propuesto](../api/openapi-sprint-4-contabilidad.yaml) y dos diagramas actualizados. Nueve contratos y 50 fuentes PlantUML validados. No hay API contable implementada. | Parte 2: revisar migraciones y modelo; no implementar efectos que dependan de políticas o fuentes aún no acordadas. |
| 2 | Cerrada como estructura SQL acotada | 12 nov. 2026: [V14 y límites](SPRINT_4_MIGRACIONES.md), DBML y UML. Regresión 72 Surefire + 185 Failsafe; prueba focalizada ampliada posterior 3 + 3, sin fallos. | Parte 3: dominio y persistencia, sin habilitar contabilización ni reglas pendientes. |
| 3 | Cerrada con alcance de dominio y persistencia | 12 nov. 2026: [mapeos JPA y reglas aisladas](SPRINT_4_DOMINIO_PERSISTENCIA.md). Focalizada 5 Surefire + 4 Failsafe y regresión 77 + 187, sin fallos ni omisiones. DBML y 50 fuentes UML validados. | Parte 4: API de plan de cuentas con validación transaccional, permisos, auditoría y pruebas; no habilitar contabilización. |
| 4 | Cerrada con límites | 13 nov. 2026: [API de cuentas](SPRINT_4_CUENTAS.md), contrato parcial y diagrama actualizados. Focalizada 6 Surefire + 5 Failsafe; regresión 78 + 192, sin fallos ni omisiones. | Parte 5: periodos; conservar el protocolo de bloqueo de cuentas y coordinarlo con futuros escritores de partidas. |
| 5 | Cerrada con límites | 13 nov. 2026: [API de periodos](SPRINT_4_PERIODOS.md); regresión 80 Surefire + 199 Failsafe, y focalizada ampliada posterior 8 + 11, sin fallos. Contratos, DBML y 50 PlantUML validados. | Parte 6: borradores; coordinar bloqueo de periodo y cuentas, estado, permisos y auditoría. |
| 6 | Cerrada con límites | 13 nov. 2026: [asientos manuales en borrador](SPRINT_4_ASIENTOS_BORRADOR.md), API, contrato, pruebas HTTP/PostgreSQL y UML. Sin migración nueva. | Parte 7: contabilización/reversión; antes definir cómo se resuelven los borradores que impiden el cierre. |
| 7 | Cerrada con límites | 13 nov. 2026: V15, edición/cancelación de borradores con motivo, contabilización balanceada y reversión idempotente con contraasiento fechado en periodo abierto; pruebas HTTP/PostgreSQL, contratos, DBML y UML validados. | Parte 8: mayor y balance de prueba. |
| 8 | Cerrada con límites | 13 nov. 2026: V16 agrega permiso de reportes; API paginada de mayor y balance de prueba concilia apertura, movimientos y cierre, incluidos contraasientos; pruebas HTTP/PostgreSQL y contratos. | Parte 9: integración durable de facturas nuevas, sin convertirlas en cobro ni procesar históricas. |
| 9 | Cerrada con límites | 13 nov. 2026: V17, mapeo de cuentas, outbox atómico y operación protegida de reintento/estado; regresión focalizada PostgreSQL, contrato OpenAPI, DBML y UML. Evidencia detallada abajo. | Parte 10: auxiliar CxC. |
| 10 | Cerrada con límites | 16 nov. 2026: V18 crea el auxiliar solo al contabilizar factura nueva; consulta protegida y saldo derivado. | Parte 11: cobros y aplicaciones. |
| 11 | Cerrada con límites | V19 y API para registro idempotente, confirmación con asiento fondos/anticipos, aplicaciones al mismo cliente con asientos de reclasificación y saldos derivados. Regresión limpia 85 + 210; prueba focalizada de concurrencia, DBML, OpenAPI y UML validados. | Parte 12: determinar fuentes de Compras/Inventario y reglas de costo/cuentas. No incluye conciliación externa, contracargos ni pagos a proveedores. |
| 12 | Cerrada con límites | 16 nov. 2026: V20 y servicios transaccionales para recepción, despacho y ajustes; mapeos de cinco cuentas y rollback por configuración/periodo/precisión. Regresión `clean verify`: 86 Surefire + 211 Failsafe, sin fallos ni omisiones. PostgreSQL 17/Testcontainers; migraciones, DBML (76 tablas/163 relaciones), nueve contratos OpenAPI (25 rutas Sprint 4), 56 PlantUML, 117 enlaces locales de cinco documentos y `git diff --check` validados. | Parte 13: facturas de proveedor/CxP solo después de definir y validar su documento fuente. No incluye pagos a proveedores, CFDI ni estados financieros. |
| 13 | Cerrada con límites | 18 nov. 2026: V21, factura de proveedor registrada y corregible, validación exacta contra orden/recepción/costo, asiento y CxP atómicos. Regresión 86 Surefire + 213 Failsafe, DBML (77/170), nueve OpenAPI, 57 UML y 209 enlaces validados. | Parte 14: pagos a proveedores; fuente bancaria y reglas de aplicación pendientes. |
| 14 | Cerrada con límites | 19 nov. 2026: V22–V23, aplicaciones al mismo proveedor y asiento atómico; regresión 86 Surefire + 213 Failsafe, focalizadas de pagos y migración, DBML, OpenAPI y UML validados. | Parte 15: estados financieros; conciliación bancaria y reversión de pagos quedan pendientes. |
| 15 | Cerrada con límites | 19 nov. 2026: V24, clasificación explícita auditable; estados HTTP de resultados, balance general y flujo indirecto. `clean verify`: 86 Surefire + 216 Failsafe, 0 fallos/errores/omisiones. V23→V24 validada en PostgreSQL/Testcontainers. OpenAPI: 9 contratos, 38 rutas contables; DBML: 80 tablas/182 relaciones, copias idénticas y V24 contrastada; PlantUML: 59 fuentes; `git diff --check` limpio. | Parte 16: revisión integrada del sprint y brechas; la cobertura unitaria sigue bajo la meta. |
| 16 | Cerrada con límites | 19 nov. 2026: [cierre](SPRINT_4_CIERRE.md), `clean verify -Pcoverage`: 86 Surefire + 216 Failsafe sin fallos; JaCoCo líneas Surefire 10,93 %, integración 94,56 %, combinadas 95,03 %. Nueve OpenAPI, DBML 80/182, 59 PlantUML, 224 enlaces locales, migraciones V1–V24 consecutivas y revisión heurística de secretos completados. | Preparar revisión de diff/commit por el equipo; quedan históricos, cobertura unitaria, frontend, carga y alcance funcional futuro. |

### Parte 13 — análisis de fuente y conciliación (18 nov. 2026)

Preflight: la rama sigue siendo `contabilidad` en `c3b08b8`; las modificaciones acumuladas de las partes 0–12 permanecen sin confirmar. La última migración es V20. El SRS RF-COM-06 exige registrar una factura de proveedor, validarla contra la orden y las recepciones y permitir como máximo una factura por orden en el MVP; diferencias o documentos ausentes impiden completar la validación. RF-CON-04 exige que la CxP provenga de esa factura validada y conserve proveedor, origen, importe, vencimiento y saldo. El DBML contiene `facturas_proveedor`, `detalles_factura_proveedor` y `cuentas_por_pagar` como diseño objetivo, sin tablas Flyway ni código operativo. No se ejecutó una nueva regresión en este preflight porque no cambió código desde la evidencia final de la parte 12.

La recepción confirmada ya crea el asiento débito Inventario / crédito Mercancía recibida no facturada por la extensión exacta de los movimientos de kardex. La orden conserva precio, descuento e impuesto; la recepción guarda precio unitario recibido, que puede diferir. El usuario aprobó conciliación exacta: orden completamente recibida, cantidades facturadas iguales a las cantidades aceptadas por línea, valores pactados coincidentes y subtotal recibido igual al pasivo transitorio. Al validar, el asiento debe debitar ese pasivo y el impuesto acreditable configurado, y acreditar CxP por el total; no registra pago. También aprobó fecha contable del día de validación en `America/Mexico_City` dentro de un periodo abierto y rollback de la validación si falta mapeo o periodo, conservando la factura registrada para reintento.

La implementación, una vez fijadas esas reglas, requiere una migración posterior a V20 para documento, detalles, auxiliar, permisos, unicidad y protecciones de origen; API de Compras para registrar/editar/validar/consultar; servicio contable con asiento y CxP atómicos; pruebas PostgreSQL de conciliación, duplicados, permisos, auditoría, concurrencia y rollback; actualización de OpenAPI, SRS, ambas copias DBML, PlantUML y SVG. La corrección de una factura `REGISTRADA` conserva su orden y proveedor; la cancelación y sustitución por otra factura quedan pendientes. V21 y la API implementan registro, edición, validación y consulta; las verificaciones y límites se registran abajo.

### Evidencia de la parte 12

Política aprobada: recepción aceptada debita Inventario y acredita Mercancía recibida no facturada; despacho confirmado debita Costo de ventas y acredita Inventario; ajuste positivo debita Inventario y acredita Ganancia por ajuste; ajuste negativo debita Pérdida por ajuste y acredita Inventario. Se toman importes de movimientos de Inventario persistidos; no se crea CxP ni factura de proveedor. El asiento exige cuenta configurada y fecha dentro de periodo abierto. La falta de mapeo, periodo cerrado o importe que no quepa exactamente en centavos revierte también la operación de origen; queda pendiente/aprobada para corregir y reintentar. No se reabren periodos.

Una regresión inicial detectó que el lector JDBC omitía algunos mapeos; se corrigió con `RowMapper`. También se ajustaron los fixtures de fuentes operativas y la prueba de despacho para verificar rollback ante importes que exceden centavos. Una primera repetición completa alcanzó 210 Failsafe y expuso una expectativa frágil en el test de balance: las demás integraciones dejan saldos de apertura en el esquema compartido. El periodo del caso ahora se sitúa antes de esos movimientos. La prueba focalizada `-Dit.test=ReportesContablesApiIT clean verify` pasó con 86 Surefire y 1 Failsafe.

La regresión final se ejecutó desde cero en PostgreSQL 17/Testcontainers con el comando siguiente y terminó con `BUILD SUCCESS`: 86 Surefire y 211 Failsafe (297), sin fallos, errores ni pruebas omitidas:

```sh
cd erp-backend
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o \
  -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' clean verify
```

El validador DBML verificó 76 tablas, 163 relaciones, V20 y copias idénticas. OpenAPI validó nueve contratos y 25 rutas Sprint 4. PlantUML validó/renderizó 56 fuentes; se comprobaron 117 enlaces locales en cinco documentos afectados y `git diff --check` terminó sin errores. Las migraciones solo se aplicaron a contenedores PostgreSQL desechables, no a una base compartida.

### Evidencia de la parte 11

V19 incorpora la configuración de cuenta de fondos por método de pago, cobros de clientes y aplicaciones idempotentes. El registro no produce un asiento; la confirmación explícita exige configuración activa y fecha en periodo abierto, y contabiliza débito a fondos/crédito a anticipos por el total recibido. Cada aplicación posterior, limitada por el saldo disponible del recibo y el saldo pendiente de una CxC del mismo cliente, debita anticipos y acredita CxC. En una sola transacción se conserva la aplicación inmutable con su asiento y auditoría. La base valida también los límites y el vínculo contable, protegiendo la historia. La confirmación representa evidencia operativa ingresada por usuario autorizado, no conciliación con banco ni verificación de movimiento externo.

La suite completa se ejecutó con `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' clean verify` y terminó con `BUILD SUCCESS`: 85 pruebas Surefire y 210 Failsafe (295), sin fallos, errores ni omisiones. Después el comando focalizado `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' -Dtest=FacturaApiIT#confirmaCobroYAplicaParcialmenteConLimitesSaldoIdempotenciaYAsientos -Dit.test=FacturaApiIT clean verify` terminó con `BUILD SUCCESS`: 1 Surefire y 11 Failsafe, sin fallos. Compiló y ejecutó el escenario HTTP/PostgreSQL ampliado, que prueba aplicaciones concurrentes contra saldo limitado, respuesta conflictiva de la operación perdedora, reintento idempotente, exceso y aplicación del saldo residual.

El validador DBML comprobó 75 tablas y 161 relaciones, V19 y las dos copias idénticas. El validador OpenAPI comprobó los nueve contratos, con 24 rutas contables. PlantUML validó y renderizó 55 diagramas. Se actualizaron además SRS, contratos, ambas copias DBML, fuentes UML/SVG, README de API y de diagramas. Las migraciones se ejercitaron solo en PostgreSQL 17/Testcontainers desechable; no se aplicaron a una base compartida. `git diff --check` pasó al cierre; no se hizo una validación exhaustiva de enlaces Markdown en esta parte.

No incluye conciliación bancaria, evidencia externa de cobro, contracargos/reversiones, pagos a proveedores, crédito operativo ni cobros automáticos. Las facturas emitidas antes de la integración explícita siguen sin procesarse automáticamente; la API no agrega una operación de migración histórica. La cuenta bancaria/caja es configurable, pero el sistema no confirma que el movimiento haya ocurrido fuera del ERP.

### Evidencia de la parte 9

V17 crea `configuracion_cuentas_factura_venta` y `integracion_factura_venta_contabilidad`, junto con tres permisos separados: consultar pendientes, administrar cuentas y reintentar. La emisión inserta la señal `PENDIENTE` y su auditoría dentro de la misma transacción de Ventas. Si no puede escribirse la señal durable, se revierte la emisión; una vez emitida y registrada, un fallo contable posterior conserva la factura y persiste el intento y un código de error seguro. No se ejecutan workers periódicos: la operación la inicia un usuario autorizado.

Se configuran tres cuentas distintas y activas de movimiento: Clientes por cobrar (ACTIVO/DEUDORA), Ventas (INGRESO/ACREEDORA) e Impuestos trasladados (PASIVO/ACREEDORA). El asiento carga el total a Clientes por cobrar y abona subtotal e impuestos; no registra efectivo sin un cobro ni costo de ventas en esta parte. Se usa la fecha de emisión si existe periodo abierto; si no, se requiere fecha explícita dentro de uno abierto. Los reintentos son idempotentes por origen `VENTAS/FACTURA_VENTA/{id}` y un asiento contabilizado no se duplica. Facturas emitidas antes de V17 quedan fuera y no se contabilizan automáticamente.

La prueba focalizada `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' -Dtest=AccountingMigrationIT,IntegracionFacturaVentaOpenApiContractTest -Dit.test=FacturaApiIT clean verify` pasó: 3 pruebas unitarias, 10 de integración HTTP/PostgreSQL y el contrato OpenAPI, sin fallos. La regresión completa `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' clean verify` también pasó: 83 pruebas Surefire y 209 Failsafe (292 en total), sin fallos, errores ni omisiones. Cubre migración desde V13 hasta V17, configuración incompleta, fallo con factura emitida preservada, contabilización exacta, reintento idempotente, concurrencia de reintentos y fecha alternativa cuando el periodo original está cerrado.

Para la parte 9, el DBML comparó 74 tablas y 153 relaciones, OpenAPI registró 17 rutas contables y se renderizaron 54 diagramas PlantUML. La auxiliar de la parte 10 se documenta en la evidencia siguiente; las facturas históricas siguen sin procesarse automáticamente.

### Evidencia de la parte 10

V18 crea `cuentas_por_cobrar` con relación única a la factura, cliente, importe original y fechas de emisión/vencimiento. Un trigger solo acepta insertar el auxiliar cuando existe una factura emitida, su integración está `CONTABILIZADA` y el asiento de origen `VENTAS/FACTURA_VENTA` también quedó contabilizado con el mismo snapshot. El registro, el cambio de outbox y el asiento se confirman o revierten en una sola transacción; un reintento idempotente devuelve el mismo asiento y no duplica el auxiliar. El auxiliar es append-only.

El saldo no se almacena: se deriva del importe original menos aplicaciones de cobro confirmadas. Como aplicaciones y cobros pertenecen a la parte 11, hoy el saldo expuesto coincide con el total original. La API protegida permite consultar la página ordenada por emisión descendente y filtrar por cliente, o consultar un auxiliar por ID. Las facturas de antes de activar el outbox no se rellenan por migración; su revisión sigue siendo explícita. No se habilita cobro, cancelación/reversión de factura, crédito operativo ni CFDI.

La prueba focalizada `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' -Dtest=AccountingMigrationIT,CuentasPorCobrarOpenApiContractTest -Dit.test=FacturaApiIT clean verify` pasó con 4 Surefire y 10 Failsafe. La regresión completa `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' clean verify` terminó con `BUILD SUCCESS`: 84 Surefire + 209 Failsafe (293), sin fallos, errores ni pruebas omitidas. Una primera ejecución completa reveló que el contrato previo de cuentas seleccionaba también la nueva ruta `/accounting/accounts-receivable`; se hizo preciso su filtro de rutas y la repetición completa pasó. El test HTTP valida permisos, filtro por cliente, snapshot, importe/fechas, ausencia de auxiliar mientras la factura está pendiente, rollback de asiento/auxiliar al fallar auditoría, creación auditada, inmutabilidad e idempotencia.

El DBML y su copia idéntica marcan implementada la tabla de diseño CxC y agregan la FK de cliente (74 tablas/154 relaciones; antes 74/153). OpenAPI validó 19 rutas contables; PlantUML validó/renderizó 54 diagramas; los enlaces locales de 14 Markdown y `git diff --check` pasaron. Las migraciones se probaron únicamente en PostgreSQL 17/Testcontainers desechable, no en una base compartida.

### Evidencia de la parte 8

Se añadieron consultas de solo lectura para mayor por cuenta y balance de prueba por periodo. Ambas incluyen el neto previo al inicio del periodo como saldo de apertura y agregan movimientos entre sus fechas inclusivas para obtener el cierre. Se incluyen asientos `CONTABILIZADO` y `REVERSADO`: las partidas del original reversado permanecen y las del contraasiento contabilizado las compensan. `BORRADOR` y `CANCELADO` se excluyen. Los totales del balance se calculan sobre todas las cuentas que tienen saldo o movimientos, independientemente de la página. El mayor conserva orden estable por fecha, asiento y número de partida y ofrece saldo acumulado en cada línea. `REPEATABLE_READ` mantiene una fotografía consistente entre totales, páginas y acumulados ante contabilizaciones concurrentes.

V16 añade `CONTABILIDAD_REPORTES_LEER` con asignación al rol `ADMINISTRADOR`; no agrega tablas. El balance de prueba usa el contrato de Reportería en `/reports/finance/trial-balance`; el mayor queda en el contrato contable bajo `/accounting/periods/{periodId}/accounts/{accountId}/ledger`. Se actualizaron ambos contratos OpenAPI, SRS, README de API, DBML (nota de alcance, sin cambios estructurales), UML y SVG.

La verificación focalizada `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' -Dtest=ReportesContablesOpenApiContractTest -Dit.test=AccountingMigrationIT,ReportesContablesApiIT clean verify` terminó con `BUILD SUCCESS`: 1 Surefire y 4 Failsafe, sin fallos. Incluye migración V13→V16, permisos, protección HTTP, apertura, movimientos, contraasiento, exclusión de borrador, paginación y conciliación de las columnas de balance. La regresión limpia final `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' clean verify` terminó con `BUILD SUCCESS`: 82 Surefire y 208 Failsafe, sin fallos, errores ni omisiones.

También pasaron `docs/diagrams/validar-modelo.cjs` (72 tablas, 148 relaciones y copias DBML idénticas), `docs/api/validar-openapi.cjs` (nueve contratos; Reportería conserva estado parcial y el balance de prueba implementado) y el renderizador PlantUML (53 diagramas). `git diff --check` pasó. Las migraciones se probaron con PostgreSQL 17/Testcontainers desechable; no se aplicaron a una base compartida.

No se integraron documentos automáticos ni se agregaron saldos precalculados. La apertura es derivada de todas las partidas contabilizadas anteriores a la fecha inicial; el reporte no es estado financiero formal ni verifica reglas fiscales. No se midió carga y las pruebas usan PostgreSQL 17/Testcontainers, no una base compartida.

### Evidencia de la parte 7

Se implementaron en V15 el historial de asientos y los metadatos de edición, cancelación y reversión. La API permite editar y cancelar borradores con motivo, contabilizar solo asientos con al menos dos partidas balanceadas exactamente y revertir un asiento contabilizado mediante un contraasiento con fecha explícita dentro de un periodo abierto. El original queda `REVERSADO`; ningún periodo se reabre. La repetición de la misma clave y solicitud de reversión devuelve el contraasiento existente. Los cambios y auditoría son transaccionales; las partidas contabilizadas quedan protegidas. Se actualizaron el contrato OpenAPI, SRS, ambas copias DBML, fuentes PlantUML y SVG afectados.

La prueba focalizada `./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' -Dit.test=AsientoContableApiIT verify` pasó con 81 Surefire y 7 Failsafe. `AccountingMigrationIT` (3 casos) validó V13→V14→V15 en PostgreSQL 17/Testcontainers. La regresión limpia `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' clean verify` terminó en `BUILD SUCCESS`: 81 Surefire y 207 Failsafe, sin fallos, errores ni omisiones. Una ejecución previa sin limpieza usó bytecode antiguo y falló cinco expectativas de 14 migraciones; las fuentes ya esperaban 15 y la compilación limpia confirmó el estado actualizado.

`DBML_CORE_PATH=/tmp/techsupply-diagram-tools/node_modules/@dbml/core node docs/diagrams/validar-modelo.cjs` validó 72 tablas, 148 relaciones, copias idénticas y correspondencia contable V14–V15. `SWAGGER_PARSER_PATH=/tmp/techsupply-openapi-tools/node_modules/@apidevtools/swagger-parser node docs/api/validar-openapi.cjs` validó nueve contratos. `python3 docs/diagrams/renderizar.py --jar /home/pablodev/.local/bin/plantuml-1.2026.7.jar --salida /tmp/techsupply-contabilidad-parte7-uml` validó y renderizó 52 diagramas. `git diff --check` pasó. Las pruebas aplicaron migraciones en PostgreSQL 17/Testcontainers desechable.

La parte 7 no habilita asientos automáticos desde facturas de Venta o Compra, ni mayor, balance de prueba, auxiliares, cobros o reportes. No se probó una carrera HTTP simultánea entre contabilizar y cerrar periodo, no se consultó una base compartida y no se midieron cobertura, carga ni integración frontend. El motivo se persiste en historial contable y no se añadió una ruta para consultar ese historial.

### Evidencia de la parte 6

**Verificación final:**

La [API de borradores](SPRINT_4_ASIENTOS_BORRADOR.md) usa V14 sin alterar esquema. Implementa creación, consulta individual y listado paginado, autorización, idempotencia y auditoría transaccional. Cada asiento requiere periodo abierto y fecha incluida, cuentas activas de movimiento y 2–32,767 partidas válidas. Los borradores pueden estar desequilibrados y no afectan saldos definitivos. La parte 7 posterior habilitó contabilización y reversión manual en V15. Se actualizaron OpenAPI, SRS y fuentes UML. El DBML no cambió en la parte 6. La prueba focalizada pasó; la regresión de entonces `./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify` reportó 99 Surefire y 205 Failsafe sin fallos, errores ni omisiones. Se validaron los 10 contratos OpenAPI, DBML (71 tablas/143 relaciones, incluyendo V14), 51 diagramas PlantUML y 203 enlaces locales; `git diff --check` pasó. Las pruebas de migración usaron PostgreSQL 17/Testcontainers y no accedieron a una base compartida.

### Evidencia de la parte 5

El usuario aprobó periodos pasados y futuros sin solapamiento, rechazar cierre con borradores e idempotencia del cierre sin auditoría duplicada. Se implementaron alta, consulta y cierre con permisos y auditoría; no hay reapertura ni contabilización. Las garantías y sus límites se describen en [periodos](SPRINT_4_PERIODOS.md).

La primera prueba focalizada detectó interbloqueo entre altas concurrentes y diferencias de precisión del instante en reintentos. Se serializaron las altas cooperantes y se truncó el instante a microsegundos; la repetición pasó. La primera regresión completa falló en Surefire porque el OpenAPI de auditoría no incluía el nuevo estado `ABIERTO`; se actualizó el contrato y la repetición pasó con **80 Surefire y 199 Failsafe**, sin fallos ni omisiones. Se añadió después un caso de rollback al fallar la auditoría de creación y comprobaciones de actor/estados; la prueba focalizada final pasó con **8 Surefire y 11 Failsafe**.

```sh
cd erp-backend
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o \
  -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

Para repetir la focalizada, añadir `-Dtest=ContabilidadDominioTest,CuentaContableOpenApiContractTest,PeriodoContableOpenApiContractTest -Dit.test=PeriodoContableApiIT,AccountingMigrationIT` antes de `verify`. Las pruebas usaron PostgreSQL 17/Testcontainers. Una carrera ejercita el cierre HTTP frente a un escritor SQL sintético que toma el bloqueo de periodo y guarda un borrador; no representa una contabilización real. Se validaron nueve OpenAPI, ambas copias DBML (71 tablas/143 relaciones) y 50 fuentes PlantUML, actualizando el SVG de clases. No se cambió ninguna migración ni se verificó una base compartida.

### Evidencia de la parte 4

El usuario acordó que, con partidas, solo se permita editar el nombre, bloqueando cambios estructurales y baja; las cuentas con hijas son agrupadoras y una cuenta con partidas no puede ser padre. La [API de cuentas](SPRINT_4_CUENTAS.md) implementa estas reglas, permisos, auditoría transaccional y bloqueo de escritores cooperantes. La prueba `CuentaContableApiIT` cubre autorización, errores, cambios, partidas, auditoría y carreras entre cambios recíprocos y altas duplicadas. `CuentaContableOpenApiContractTest` contrasta rutas, DTO y parámetros. No se cambiaron las migraciones y no se habilitaron rutas de periodos, asientos ni contabilización.

La ejecución focalizada pasó con 6 Surefire y 5 Failsafe. Una primera regresión completa quedó interrumpida por el reinicio de la sesión, sin resultado final; la repetición pasó. La revisión posterior detectó un caso de códigos históricos en minúsculas: se corrigió la edición y se amplió la prueba de una cuenta con partidas. La regresión completa sobre el código final volvió a pasar con 78 Surefire y 192 Failsafe, sin fallos ni omisiones, en PostgreSQL 17/Testcontainers:

```sh
cd erp-backend
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o \
  -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

Para la prueba focalizada, añadir `-Dtest=ContabilidadDominioTest,CuentaContableOpenApiContractTest -Dit.test=CuentaContableApiIT` antes de `verify`. Se validaron nueve contratos OpenAPI, ambas copias DBML (71 tablas/143 relaciones) y 50 fuentes PlantUML; se actualizó el SVG de clases. Estas verificaciones no prueban navegador, CORS, carga, rendimiento o una base compartida. El protocolo de bloqueo no protege escritores SQL que no participen en él; la futura API de partidas debe coordinarse con cuentas y validar estado y aceptación de movimientos.

### Evidencia de la parte 3

Las entidades y repositorios mapean cuentas, periodos, asientos y partidas de V14; los cálculos aislados validan importes, balance y jerarquía sobre una fotografía de ancestros. La [documentación de dominio](SPRINT_4_DOMINIO_PERSISTENCIA.md) distingue sus límites de las garantías que necesitan servicios y concurrencia. V14 sigue bloqueando contabilización y fuentes operativas; no se crearon rutas, migraciones ni operaciones HTTP.

La prueba focalizada pasó 5 Surefire y 4 Failsafe con PostgreSQL 17/Testcontainers y `ddl-auto=validate`; la regresión completa pasó con 77 Surefire y 187 Failsafe, sin fallos ni omisiones:

```sh
cd erp-backend
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o \
  -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

La prueba focalizada se reproduce con el mismo comando agregando `-Dtest=ContabilidadDominioTest -Dit.test=ContabilidadPersistenceIT,AccountingMigrationIT` antes de `verify`. El validador DBML comparó las dos copias y V14; PlantUML validó y renderizó 50 fuentes, y se actualizó el SVG de clases. La ejecución usa bases desechables; no comprueba una base compartida, integración frontend, carga ni cobertura unitaria. El contrato contable continúa `design-only`.

### Evidencia de la parte 2

El usuario aprobó importes de hasta 12 enteros y dos decimales, rechazando precisión adicional. V14 usa `numeric` con CHECK de escala y rango para evitar redondeos previos de PostgreSQL, crea cuatro tablas y nueve permisos y bloquea contabilización, reversión y fuentes operativas; no existen API ni servicios contables. Los límites de protección, asignación de permisos y migración desde V13 se describen en [migraciones](SPRINT_4_MIGRACIONES.md).

La primera prueba focalizada falló por el `search_path` de los datos sintéticos del esquema aislado, no por una conclusión sobre la migración; se corrigió el montaje y la repetición pasó. La regresión completa posterior con `./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify` y `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m'` terminó con 72 Surefire y 185 Failsafe, sin fallos ni omisiones. Después se ampliaron únicamente las pruebas de V14 para verificar restricciones adicionales y la carrera de periodos solapados; la prueba focalizada final pasó con 3 Surefire y 3 Failsafe:

```sh
cd erp-backend
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o \
  -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' \
  -Dtest=CalculoOrdenTest -Dit.test=AccountingMigrationIT verify
```

Las pruebas usaron PostgreSQL 17/Testcontainers, con instalación limpia y actualización desde V13 preservando usuario, cliente y pedido sintéticos. `DBML_CORE_PATH=/tmp/techsupply-diagram-tools/node_modules/@dbml/core node docs/diagrams/validar-modelo.cjs` validó 71 tablas, 143 relaciones, copias idénticas y las cuatro tablas V14; no sustituye pruebas de CHECK o triggers. `python3 docs/diagrams/renderizar.py --jar /home/pablodev/.local/bin/plantuml-1.2026.7.jar --salida /tmp/techsupply-contabilidad-parte2-uml` validó 50 fuentes y se actualizó el SVG de clases. No se verificó ni modificó ninguna base compartida.

### Evidencia de la parte 1

El usuario acordó conservar la factura emitida si falla su contabilización, revisar facturas históricas mediante proceso explícito y no reabrir periodos por ahora. El [diseño](SPRINT_4_DISENO_CONTABILIDAD.md) separa estas decisiones de propuestas y recoge las políticas abiertas. El contrato de nueve rutas y sus permisos son `design-only`; no cubre aún integración automática, auxiliares ni reportes. Se actualizaron las vistas PlantUML de proceso y secuencia y sus SVG para no sugerir que el rollback contable elimina la factura de venta.

`SWAGGER_PARSER_PATH=/tmp/techsupply-openapi-tools/node_modules/@apidevtools/swagger-parser node docs/api/validar-openapi.cjs` validó nueve contratos; dos ejemplos del contrato propuesto pasaron Ajv con formatos desactivados, lo que no comprueba fechas, reglas contables ni precisión de Java/PostgreSQL. El primer intento de validación de ejemplos usó una opción Ajv no admitida; la repetición con `validateFormats: false` pasó. `python3 docs/diagrams/renderizar.py --jar /home/pablodev/.local/bin/plantuml-1.2026.7.jar --salida /tmp/techsupply-contabilidad-parte1-uml` validó y renderizó 50 fuentes. La primera ejecución detectó sintaxis incorrecta en anotaciones nuevas; se corrigió y la repetición pasó. Se copiaron únicamente los dos SVG afectados.

No se alteraron Java, migraciones ni DBML, ni se ejecutó de nuevo la regresión Maven: la última línea base es la registrada para la parte 0. No se ha comprobado una base compartida. La parte 2 debe validar en PostgreSQL cualquier migración que realmente se agregue y mantener bloqueadas las operaciones que dependan de decisiones pendientes.

La comprobación de enlaces de los cinco Markdown afectados revisó 118 destinos locales sin encontrar archivos faltantes. Un chequeo adicional confirmó que las nueve rutas llevan `design-only` y los `operationId` son únicos; `git diff --check` terminó sin errores. Estos controles no verifican el comportamiento HTTP de las rutas propuestas.

### Evidencia de la parte 0

El árbol estaba limpio antes de cambiar de `develop` a `contabilidad`; ambas ramas apuntaban a `c3b08b8`. Se revisaron el SRS, cierres, código de integración, esquema y rúbrica. No se modificaron código, migraciones, contratos, DBML ni diagramas. La primera ejecución de la regresión se interrumpió al perderse la sesión y su log temporal; no se le atribuye resultado final. La repetición terminó con `BUILD SUCCESS`:

```sh
cd erp-backend
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o \
  -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

El resultado fue 72 pruebas Surefire y 183 Failsafe, sin fallos, errores ni omisiones; las pruebas aplicaron Flyway en PostgreSQL 17 aislado, incluida la actualización V12→V13. Esto no verifica una base compartida ni constituye una nueva medición de cobertura.

- `DBML_CORE_PATH=/tmp/techsupply-diagram-tools/node_modules/@dbml/core node docs/diagrams/validar-modelo.cjs`: 71 tablas, 143 relaciones, copias idénticas y comparaciones de esquema informadas por el validador; los CHECK, triggers e índices parciales requieren revisar Flyway y pruebas SQL.
- `SWAGGER_PARSER_PATH=/tmp/techsupply-openapi-tools/node_modules/@apidevtools/swagger-parser node docs/api/validar-openapi.cjs`: ocho contratos válidos, incluido Reportería como diseño. Con la misma variable, `node docs/api/validar-ejemplos-sprint3.cjs`: siete ejemplos válidos y tres inválidos rechazados; no evalúa formatos.
- `python3 docs/diagrams/renderizar.py --jar /home/pablodev/.local/bin/plantuml-1.2026.7.jar --salida /tmp/techsupply-contabilidad-parte0-uml`: 50 fuentes validadas y renderizadas fuera del repositorio. La sintaxis no demuestra que el diseño esté implementado.
- Comprobación de 86 enlaces locales en los tres documentos afectados: ninguno faltante. `git diff --check` sin errores. Los validadores temporales y sus instrucciones de instalación están en los README de [API](../api/README.md) y [diagramas](../diagrams/README.md); los archivos temporales pueden no persistir entre sesiones.

### Parte 14 — pagos a proveedores

Para que el mayor de CxP concilie con el auxiliar desde la confirmación, el pago se registrará primero y su confirmación deberá incluir aplicaciones por el importe completo, repartidas entre una o más CxP del mismo proveedor. Cada aplicación puede cubrir parcialmente una CxP. El débito a CxP y crédito a fondos se registrará una sola vez al confirmar. V22 y V23, la API y la integración contable están implementados y comprobados en el árbol de trabajo.

La primera regresión completa de esta parte terminó con 213 pruebas de integración y un fallo en la carrera de recepción y despacho: según el orden, el costo promedio tenía precisión incompatible con la regla contable de centavos de V20. Se ajustaron los datos de esa prueba para mantener un costo exacto en ambos órdenes; su ejecución focalizada pasó con una prueba de integración. La API y el asiento de pago ya existen, pero la ejecución focalizada posterior verificó las restricciones finales. Los contratos OpenAPI validaron 34 rutas contables; DBML 78 tablas/176 relaciones y PlantUML 58 fuentes.

La prueba focalizada de factura y pago pasó en PostgreSQL/Testcontainers con una prueba de integración: pago parcial de CxP, rechazo de proveedor distinto, rollback ante configuración ausente, reintento, bloqueo de reversión aislada y dos confirmaciones concurrentes sobre un saldo insuficiente. La regresión posterior pasó con 86 Surefire y 213 Failsafe; las pruebas focalizadas posteriores verificaron pago y actualización desde V21. El registro, lectura, configuración y confirmación requieren respectivamente `CONTABILIDAD_PAGOS_PROVEEDOR_REGISTRAR`, `CONTABILIDAD_PAGOS_PROVEEDOR_LEER`, `CONTABILIDAD_PAGOS_PROVEEDOR_CONFIGURAR` y `CONTABILIDAD_PAGOS_PROVEEDOR_CONFIRMAR`; confirmar exige además permisos de registrar y contabilizar asientos. Las pruebas no demuestran conciliación bancaria externa, cancelación ni reversión operativa.

### Evidencia de la parte 13

El usuario aprobó conciliación exacta y fecha del día de validación en `America/Mexico_City`. V21 y `FacturaProveedorService` implementan factura fuente, líneas, mapeo de IVA acreditable/CxP, asiento que libera Mercancía recibida no facturada, auxiliar CxP y rollback si la validación no puede completarse. La factura registrada conserva su orden y proveedor, admite corrección de datos y no genera CxP hasta validar. No se registran pagos ni se aplica una política de diferencias.

La verificación focalizada aplicó V1–V21 en PostgreSQL 17/Testcontainers y pasó con 86 Surefire y 8 Failsafe (`ConfirmarRecepcionIT`), incluidos el asiento balanceado, unicidad de CxP, diferencia de cantidad, corrección y falta de mapeo. La primera regresión completa detectó dos aserciones antiguas que esperaban 20 migraciones; se actualizaron a 21 en `AccountingMigrationIT` y `SalesMigrationIT`. La repetición de `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify` pasó con **86 Surefire y 213 Failsafe**, sin fallos, errores ni omisiones. El DBML validó 77 tablas, 170 relaciones, V21 y copias idénticas; los nueve contratos OpenAPI validaron, incluido el de Contabilidad con 30 rutas. PlantUML validó y renderizó 57 diagramas; se comprobaron 209 enlaces locales en ocho documentos afectados. `git diff --check` pasó. Las migraciones se aplicaron solo en contenedores de prueba, no en una base compartida.

La regresión `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' clean verify` terminó con 86 Surefire y 213 Failsafe, sin fallos ni omisiones. Después pasó la prueba focalizada de pagos con configuración, permisos, idempotencia, proveedor, saldo, rollback, concurrencia, reversión y asiento, y la actualización V21→V23 con preservación de proveedor sintético. Los cambios posteriores a la regresión general se comprobaron en las pruebas focalizadas. DBML validó 78 tablas/176 relaciones y copias idénticas; OpenAPI validó nueve contratos y 34 rutas contables; PlantUML validó y renderizó 58 fuentes; `git diff --check` pasó. PostgreSQL se ejecutó en Testcontainers, sin comprobar bases compartidas.

### Parte 15 — evidencia y límites (19 nov. 2026)

Los estados de resultados y balance general usan asientos `CONTABILIZADO` y `REVERSADO`, incluido el contraasiento, y excluyen borradores. Sus saldos se agregan por tipo de cuenta; el balance presenta la diferencia para revelar descuadres. V24 añade dos tablas de clasificación histórica, permiso `CONTABILIDAD_FLUJO_CLASIFICAR`, exclusión de vigencias solapadas por cuenta, unicidad de clasificación activa por partida y auditoría de anulación/sustitución. No clasifica registros históricos automáticamente.

El usuario aprobó flujo indirecto y estado `NO_DISPONIBLE` cuando falte evidencia fiable. El cálculo valida cobertura de efectivo, clasificaciones de cuentas y partidas, compensación de transacciones no monetarias, asientos con actividad y conciliación del cambio de efectivo. La respuesta presenta resultado, ajuste agregado de operación, operación, inversión, financiación y efectivo inicial/final. La implementación no desglosa el ajuste operativo en sus componentes ni infiere categorías; una transacción compleja puede resultar `NO_DISPONIBLE`. No equivale a conciliación bancaria ni a informe fiscal.

La prueba HTTP/PostgreSQL cubre importes de resultados y balance, exclusión de borradores, permisos, asientos reversados/contraasientos, clasificación, sustitución/anulación, actividades de operación/inversión/financiación, operación no monetaria y resultado `NO_DISPONIBLE`. La focalizada de reportes y V23→V24 pasó; `MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' clean verify` terminó con `BUILD SUCCESS`: **86 Surefire + 216 Failsafe, 302 pruebas**, sin fallos, errores ni omisiones. Se ejecutó en PostgreSQL 17/Testcontainers; V24 no se aplicó a ninguna base compartida. Los nueve contratos OpenAPI validaron (38 rutas contables); DBML validó 80 tablas/182 relaciones y sus copias idénticas; 59 diagramas PlantUML se validaron/renderizaron; `git diff --check` pasó. La meta unitaria de 70 % sigue pendiente, el rendimiento/carga no se midió y el cálculo no afirma cobertura de todo RF-CON-07.
