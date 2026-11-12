# Fase 3 — ruta semanal del backend

**Estado de este plan:** propuesta de ejecución, iniciada el 27 de octubre de 2026. **Fecha objetivo del enunciado:** 17 de diciembre de 2026. El compañero del equipo desarrolla el frontend; este plan solo compromete backend, contratos HTTP y la coordinación necesaria para que pueda consumirlos.

## Cómo retomar el trabajo en un nuevo chat

1. Leer este archivo, [patrones y programación funcional](PATRONES_Y_PROGRAMACION_FUNCIONAL.md), el [SRS](SRS_TechSupply_Solutions.md) y las [instrucciones del repositorio](../AGENTS.md).
2. Revisar rama, cambios sin confirmar, código y pruebas reales antes de escoger la siguiente tarea. No inferir que una casilla está terminada solo por este plan.
3. Confirmar el sprint activo con la tabla de avance y el último resultado de `cd erp-backend && ./mvnw -o verify`. Si hay diferencias, actualizar aquí el estado con evidencia.
   Para el sprint 3 de Ventas, seguir la primera parte pendiente en su [ejecución por partes](fase-3/SPRINT_3_VENTAS_POR_PARTES.md). Se conservan las bitácoras de [Inventario](fase-3/SPRINT_2_INVENTARIO_POR_PARTES.md) y del [sprint 1](fase-3/SPRINT_1_EJECUCION_POR_PARTES.md).
4. Implementar una historia vertical a la vez: migración Flyway, reglas de negocio, API y permisos, auditoría, OpenAPI y pruebas de integración. Mantener los cambios ajenos intactos.
5. Al cerrar una historia, anotar requisito, archivos, comando y resultado, decisión pendiente y siguiente paso. No marcarla terminada sin verificación ejecutada.

## Fuentes y alcance

- [Enunciado de proyecto](rubrica/proyecto%20final%20dise%C3%B1o.pdf): fase 3, módulos y fecha. El documento dice sprints de dos semanas pero enumera doce; por restricción de tiempo del equipo se usarán **siete sprints de una semana** y una ventana final de corrección.
- [Rúbrica final](rubrica/rubrica_final.pdf): arquitectura, componentes reutilizables, funcionalidad, integridad, pruebas, código y documentación. Es una rúbrica de entrega final, no una prueba de que el diseño ya esté implementado.
- [SRS](SRS_TechSupply_Solutions.md), [modelo de datos](../database/modelo.dbml) y [componentes de fase 2](fase-2/integracion/COMPONENTES_FASE_2.md) rigen las reglas y el estado inicial. Los diagramas UML describen el diseño del sistema; el DBML conserva notas del esquema y del modelo objetivo, y las migraciones Flyway son el esquema ejecutable.
- No se implementan requisitos futuros u opcionales por el solo hecho de aparecer en la narrativa. Cambios de alcance o reglas de negocio pendientes requieren decisión del equipo/docente.

## Punto de partida comprobado

Al iniciar el sprint, el backend tenía seguridad con Bearer opaco, usuarios/roles/permisos, validación y errores HTTP, categorías con búsqueda, auditoría y bandeja APP; Flyway llegaba a V5. La parte 1 añadió V6 con cuatro catálogos SQL y permisos; la parte 2 añadió sus entidades JPA y repositorios; la parte 3 añadió servicios y API de departamentos, proveedores y almacenes. La parte 4 añadió API de producto. V7 creó el esquema inicial de compras y existencias; la parte 6 añadió entidades, repositorios y cálculo de órdenes. La parte 7 añadió servicios y API de órdenes; la parte 8 añadió el núcleo de inventario y consulta de existencias. La parte 9 añadió registro y consulta de recepciones; la parte 10 añadió confirmación transaccional. V8 corrige la precisión de los costos de inventario. V11 agregó el esquema base de clientes y Ventas y la parte 4 sus mapeos JPA; la API de clientes está implementada; los servicios y API de Ventas, Contabilidad y reportes siguen sin implementación. El contrato de reportería es diseño. El frontend se desarrolla por separado.

La verificación comunicada antes de crear este plan fue `./mvnw -o verify` el 27 de octubre de 2026: se informaron 41 pruebas Surefire y 49 Failsafe. No se conserva una salida íntegra de esa ejecución para reconfirmar el primer número. En la ejecución de la parte 0, los reportes vigentes sumaron 40 Surefire y 49 Failsafe; el XML adicional era antiguo y seguía en `target/`. Ninguna cifra mide cobertura de código ni rendimiento concurrente.

## Calendario propuesto y entregables

| Sprint | Fechas | Entrega backend verificable | Requisitos / aceptación | Estado |
|---|---|---|---|---|
| 1 | 27 oct.–2 nov. | Gestión Maestra y Compras: proveedores, productos, departamentos, almacenes, orden y recepción parcial con inventario mínimo. [Partes verificables](fase-3/SPRINT_1_EJECUCION_POR_PARTES.md), [plan](fase-3/SPRINT_1_GESTION_MAESTRA_COMPRAS.md), [migraciones](fase-3/SPRINT_1_MIGRACIONES.md), [módulos](fase-3/SPRINT_1_MODULOS.md), [OpenAPI del sprint](api/openapi-sprint-1-maestros-compras.yaml). | RF-MAE-01/02/03 y RF-MAE-05 parcial; RF-COM-03/04/05; RF-INV-01/02 parcial; CA-01. | Partes 0–11 cerradas; ver límites y preparación de commit en [cierre](fase-3/SPRINT_1_CIERRE.md) |
| 2 | Por replanificar | Inventario: conteos, ajustes autorizados, kardex y alerta de stock; [plan por partes](fase-3/SPRINT_2_INVENTARIO_POR_PARTES.md). Informes condicionados por reglas y fuentes. | RF-INV-01/02 parcial, RF-INV-05/06 y RF-INV-07 según alcance real; CA-01/06. | Partes 0–13 cerradas; [cierre y límites](fase-3/SPRINT_2_CIERRE.md) |
| 3 | Una semana; fechas por acordar | Ventas e integración con Inventario y clientes; [plan por partes](fase-3/SPRINT_3_VENTAS_POR_PARTES.md). Factura y crédito sujetos a reglas/fuentes y capacidad. | RF-MAE-04 parcial, RF-VEN-01–04, RF-INV-03/04 y CA-02/03 según alcance realmente validado. | Parte 5 cerrada con anomalías JVM; parte 6 pendiente; políticas de parte 1 aún abiertas |
| 4 | Por replanificar | Continuación según resultados de Ventas y prioridades del equipo; considerar pendientes de Compras y facturación. | Requisitos por definir según dependencias y capacidad. | Pendiente de planificación |
| 5 | 24–30 nov. | Periodos, plan de cuentas, asientos y reversión; cuentas por pagar/cobrar y aplicaciones de pagos sobre documentos disponibles. | RF-CON-01 a 06, CA-04/05; equilibrio, periodo abierto, saldo e idempotencia. | Pendiente |
| 6 | 1–7 dic. | Reportes sobre fuentes reales, permisos de filas y primer dashboard; cerrar históricos y notificaciones de negocio que tengan productor y destinatario definidos. | RF-REP-01 a 04 según fuentes existentes, CA-08; no publicar cifras sin fórmula acordada. | Pendiente |
| 7 | 8–14 dic. | Integración de contratos con frontend del compañero, regresión, pruebas de carga y cobertura, manual técnico, trazabilidad y preparación de demostración. | RNF-02/03/04/12/17; evidencia reproducible y defectos corregidos. | Pendiente |
| Cierre | 15–17 dic. | Correcciones de hallazgos, revisión de entrega y documentación final. | No iniciar un módulo grande nuevo en esta ventana. | Pendiente |

**Replanificación (9 nov. 2026):** por decisión del usuario, el sprint 2 será de Inventario; se pospone la propuesta anterior de priorizar requisiciones. Las fechas de sprints 2 y 3 se acordarán de nuevo; las fechas restantes de la tabla son el plan original y deben revisarse según la capacidad disponible. No se da por implementado RR. HH. ni se presupone que cabrá sin una decisión de alcance.

**Replanificación (10 nov. 2026):** el usuario prioriza Ventas como sprint 3. Las requisiciones y demás pendientes de Compras no se consideran resueltos ni se asignan automáticamente a una semana; el sprint 4 y las fechas posteriores deben replanificarse. El objetivo semanal y las partes de Ventas no garantizan que todos los requisitos quepan.

**Dependencias:** por decisión del equipo, Gestión Maestra y el flujo central de Compras se adelantan al sprint 1. La recepción necesita existencias y movimientos, por eso su base se construye en esa misma semana. Facturación y contabilidad necesitan documentos operativos válidos; reportería necesita fuentes y fórmulas aprobadas. El calendario es una secuencia de prioridades, no una promesa de que todas las funcionalidades caben en siete semanas.

<a id="prioridad-propuesta-para-iniciar-el-sprint-2"></a>
## Pendientes de Compras para replanificar después de Inventario

1. Preflight: revisar RF-COM-01/02, modelo y contrato; definir estados, autorización, motivo de rechazo y tratamiento compatible de las órdenes ya existentes. El flujo actual crea órdenes directamente y todavía no asegura que provengan de una requisición aprobada: RF-COM-02 no está cumplido de extremo a extremo.
2. Implementar requisiciones y sus detalles con migración nueva, validaciones, persistencia, permisos, auditoría y pruebas PostgreSQL; después crear, consultar, aprobar y rechazar mediante API y OpenAPI.
3. Vincular la generación de órdenes a requisiciones aprobadas, con protección contra concurrencia y duplicación según las decisiones de negocio adoptadas; probar el flujo completo y la compatibilidad de documentos previos. La propuesta automática por umbral es una historia separada, no una orden automática.
4. Priorizar una solución para recepciones pendientes que no puedan confirmarse por causas corregibles, definiendo y probando edición o cancelación, permisos y auditoría sin alterar recepciones confirmadas. No está implementada todavía.
5. Incorporar medición de cobertura y coordinación con frontend de forma temprana. Facturas de proveedor requieren conciliación y una cuenta por pagar real; coordinar con contabilidad y no darlas por completadas con una API aislada. Devoluciones requieren salidas de inventario y control de saldo; planificarlas con inventario.

Inventario completo, carga, CORS/despliegue y funciones de otros módulos siguen como trabajo futuro; el sprint 2 aborda la protección del kardex dentro de los límites documentados. Los límites no se cerrarán automáticamente por cambiar de sprint: deben convertirse en historias con responsables y evidencia. El orden de esta lista es una propuesta de alcance, no una garantía de que todo quepa en una semana.

## Criterio de terminado para cada historia

- Requisito y caso de aceptación identificados; decisión de negocio no inventada.
- Migración Flyway nueva y coherente con entidad, restricciones, claves e índices. No editar migraciones ya aplicadas.
- Operación transaccional, autorización en backend, DTO y validación de entrada; errores con el formato existente.
- Auditoría de cambios críticos sin secretos; idempotencia y concurrencia probadas cuando corresponda.
- OpenAPI y consumidor frontend coordinados si cambia el contrato. No presentar rutas de diseño como activas.
- Pruebas unitarias de reglas, HTTP/autorización e integración PostgreSQL según el riesgo. Ejecutar `./mvnw -o verify` y registrar resultado real.
- Evidencia de reutilización y de patrones/programación funcional actualizada en [su documento](PATRONES_Y_PROGRAMACION_FUNCIONAL.md). Usar patrones solo si simplifican el código.

## Seguimiento y decisiones abiertas

| Tema | Estado / siguiente decisión |
|---|---|
| Alcance de RR. HH. y portal de clientes | El SRS los incluye, pero la lista de siete módulos principales de la narrativa no cuenta RR. HH. como módulo principal. Confirmar con docente/equipo si entran en la entrega funcional y ajustar esta ruta. |
| Fórmulas y reglas | [Políticas de compras e inventario del sprint 1](fase-3/SPRINT_1_POLITICAS.md): importes, autorización y valoración implementados; alerta global implementada en sprint 2 y cancelación de recepciones pendientes aún por definir. Crédito, rotación, ABC, rentabilidad, estados financieros y zona de negocio requieren definición antes de calcularlos. |
| Cobertura y CI | Perfil JaCoCo configurado; última medición registrada: Surefire 13,96 % de líneas (incluye pruebas de contrato) y combinada 95,38 %. No acredita el mínimo unitario del 70 %; mejorar pruebas significativas y volver a medir. |
| Rendimiento | Diseñar datos y carga representativos; objetivos: p95 < 2 s en consultas, p95 < 5 s en reportes y evaluación de 200 usuarios concurrentes. No inferir cumplimiento de pruebas MockMvc secuenciales. |
| Configuración local | La contraseña de base de datos se suministra mediante `DB_PASSWORD`, sin valor predeterminado en `application.yaml`; la versión anterior contenía un valor predeterminado en Git. Si correspondía a una credencial real, rotarla y evaluar la exposición del historial; quitarla del archivo actual no elimina commits anteriores. |
| Coordinación con frontend | Entregar OpenAPI y ejemplos por sprint; acordar autenticación, errores, paginación y estados con el compañero. La implementación del frontend no forma parte de este plan. |

## Bitácora de avance

Añadir una fila por historia cerrada o cambio de plan. Registrar hechos, no estimaciones.

| Fecha | Sprint / requisito | Resultado comprobado y comando | Pendiente o decisión |
|---|---|---|---|
| 27 oct. 2026 | Inicio del plan | Fase 2: `./mvnw -o verify`, se informaron 41 Surefire + 49 Failsafe; el primer número no es reconfirmable con los reportes vigentes. | Iniciar sprint 1; confirmar alcance de RR. HH. y reglas abiertas. |
| 28 oct. 2026 | Sprint 1, parte 0 | [Preflight cerrado](fase-3/SPRINT_1_EJECUCION_POR_PARTES.md): V5 última migración; `./mvnw -o verify` pasó con 40 Surefire + 49 Failsafe en PostgreSQL 17 de Testcontainers. | Siguiente: parte 1, V6. Reglas de importes, aprobación, stock mínimo y costo aún abiertas. |
| 28 oct. 2026 | Sprint 1, parte 1 | V6 creó cuatro catálogos SQL y ocho permisos; `MasterCatalogMigrationIT` 3/3; `./mvnw -o verify`: 40 Surefire + 52 Failsafe, sin fallos, PostgreSQL 17. | Siguiente: parte 2, entidades JPA/repositorios; OpenAPI de nuevos catálogos sigue `design-only`. |
| 28 oct. 2026 | Sprint 1, parte 2 | Cuatro entidades JPA/repositorios mapeados a V6; `MasterCatalogPersistenceIT` 2/2; `./mvnw -o verify`: 40 Surefire + 54 Failsafe, sin fallos, PostgreSQL 17 y `ddl-auto=validate`. | Siguiente: parte 3, API de departamentos/proveedores/almacenes. OpenAPI nuevo sigue `design-only`. |
| 28 oct. 2026 | Sprint 1, parte 3 | API de departamentos, proveedores y almacenes con permisos, auditoría y baja lógica; contrato 1/1 e integración 3/3; `./mvnw -o verify`: 41 Surefire + 57 Failsafe, sin fallos. | Siguiente: parte 4, API de producto. OpenAPI del sprint permanece `design-only` a nivel global; los tres recursos están marcados implementados. |
| 29 oct. 2026 | Sprint 1, parte 4 | API de productos con SKU único, referencias, filtros, permisos y auditoría; `./mvnw -o verify`: 41 Surefire + 58 Failsafe sin fallos. | Siguiente: parte 5, V7. El umbral de reorden se persiste sin alertas ni interpretación por almacén. |
| 29 oct. 2026 | Sprint 1, parte 5 | V7 crea seis tablas, restricciones, secuencias y siete permisos; `./mvnw -o verify`: 42 Surefire + 60 Failsafe sin fallos. | Siguiente: parte 6, dominio de órdenes; aplicar y probar la política de redondeo documentada. |
| 29 oct. 2026 | Sprint 1, parte 6 | Dominio JPA de órdenes y cálculo determinista; suite completa 45 Surefire + 61 Failsafe; prueba enfocada posterior 3 + 1 sin fallos. | Parte 7: servicios, autorización, auditoría, folios y API de orden. |
| 29 oct. 2026 | Sprint 1, parte 7 | API de órdenes con autorización, folios, bloqueo y auditoría; `./mvnw -o verify`: 46 Surefire + 62 Failsafe; prueba focalizada posterior 1 + 1 sin fallos. | Parte 8: núcleo de inventario. |
| 29 oct. 2026 | Sprint 1, parte 8 | Núcleo de inventario, lectura protegida y valoración; `./mvnw -o verify`: 49 Surefire + 64 Failsafe; prueba focalizada posterior 3 + 3 sin fallos. | Parte 9: registro de recepción. |
| 29 oct. 2026 | Sprint 1, parte 9 | Registro/consulta de recepciones pendientes con idempotencia, permisos y auditoría; `./mvnw -o verify`: 50 Surefire + 66 Failsafe sin fallos. | Parte 10: confirmación de recepción e inventario. |
| 29 oct. 2026 | Sprint 1, parte 10 | Confirmación transaccional de recepción con idempotencia, inventario y auditoría; `./mvnw -o verify`: 50 Surefire + 70 Failsafe sin fallos. | Parte 11: cierre integral, documentación, contratos y revisión de seguridad. |
| 29 oct. 2026 | Sprint 1, parte 11 | [Cierre backend](fase-3/SPRINT_1_CIERRE.md): 50 Surefire + 70 Failsafe; contratos OpenAPI, DBML y UML validados. | Preparar commit excluyendo configuración local sensible; siguiente sprint según prioridades y límites documentados. |
| 9 nov. 2026 | Revisión posterior sprint 1 | V8 amplía precisión de costos; `./mvnw -o verify`: 50 Surefire + 72 Failsafe sin fallos. DBML, seis contratos y 42 diagramas validados. | Sprint 2 pendiente: requisiciones, compatibilidad de órdenes anteriores y planificación semanal actualizada. |
| 9 nov. 2026 | Replanificación sprint 2 | Por decisión del usuario, Inventario pasa al sprint 2; [ruta por partes](fase-3/SPRINT_2_INVENTARIO_POR_PARTES.md) creada. Sin implementación ni pruebas nuevas en esta planificación. | Iniciar parte 0; replanificar fechas y pendientes de Compras. |
| 9 nov. 2026 | Sprint 2, parte 0 | Preflight en `inventario`: `./mvnw -o verify` pasó con 50 Surefire + 72 Failsafe; DBML, seis contratos y 42 diagramas validados. | Parte 1: definir políticas y contratos; el kardex actual solo admite recepción y reportería es diseño. |
| 9 nov. 2026 | Sprint 2, parte 1 | [Diseño de Inventario](fase-3/SPRINT_2_DISENO_INVENTARIO.md) y OpenAPI propuesto; siete contratos validados. Sin implementación. | Parte 2: migraciones y DBML; demostrar compatibilidad con V8 y concurrencia con recepción. |
| 9 nov. 2026 | Sprint 2, parte 2 | [V9 de Inventario](fase-3/SPRINT_2_MIGRACIONES.md): cuatro tablas y fuente de ajuste; `./mvnw -o verify`: 50 Surefire + 73 Failsafe sin fallos. DBML y 42 diagramas validados. | Parte 3: dominio y repositorios; API, transacciones y control concurrente aún pendientes. |
| 9 nov. 2026 | Sprint 2, parte 3 | JPA/dominio de conteos y ajustes; `MAVEN_OPTS='-XX:+UseSerialGC' ./mvnw -o verify`: 53 Surefire + 74 Failsafe sin fallos. Primera compilación falló por SIGSEGV de la JVM; repetición exitosa. | Parte 4: lectura de kardex. Servicios, API de documentos y concurrencia pendientes. |
| 9 nov. 2026 | Sprint 2, parte 4 | Lectura protegida del kardex; `MAVEN_OPTS='-XX:+UseSerialGC' ./mvnw -o verify`: 54 Surefire + 75 Failsafe sin fallos. Prueba focalizada posterior 1 + 1 valida fecha ISO. | Parte 5: conteos; movimientos de ajuste aún no se producen desde aplicación. |
| 9 nov. 2026 | Sprint 2, parte 5 | API de conteos, auditoría y bloqueos; `MAVEN_OPTS='-XX:+UseSerialGC' ./mvnw -o verify`: 55 Surefire + 78 Failsafe sin fallos. Después, prueba focalizada de contratos y rollback de auditoría: 3 + 4 sin fallos. | Parte 6: solicitudes de ajuste; vigencia al aplicar y concurrencia de primera existencia pendientes. |
| 9 nov. 2026 | Sprint 2, parte 6 | Solicitudes de ajuste con idempotencia y auditoría; regresión Java 21 con SerialGC en Maven y pruebas: 56 Surefire + 83 Failsafe sin fallos. Dos fallos iniciales de JVM y discrepancia de nulos corregida documentados en la bitácora. | Parte 7: aprobación/rechazo; aplicación transaccional y concurrencia de primera existencia siguen pendientes. |
| 9 nov. 2026 | Sprint 2, parte 7 | Aprobación y rechazo con segregación, bloqueo y auditoría; regresión Java 21: 56 Surefire + 87 Failsafe; focalizada posterior: 3 + 4, sin fallos. | Parte 8: aplicación atómica y control concurrente; CA-06 de aplicación pendiente. |
| 9 nov. 2026 | Sprint 2, parte 8 | Aplicación atómica de ajustes, vigencia e idempotencia; regresión Java 21: 56 Surefire + 97 Failsafe sin fallos, y DBML, siete contratos y 42 diagramas validados. | Parte 9: protección del historial y kardex ante modificación, con límites de privilegios documentados. |
| 9 nov. 2026 | Sprint 2, parte 9 | V10 protege historial y valida fuentes nuevas; regresión Java 21: 56 Surefire + 99 Failsafe sin fallos; DBML, siete contratos y 42 diagramas validados. | Parte 10: alerta de stock; V10 no sustituye permisos ni protege fuentes de Compras ante SQL directo. |
| 9 nov. 2026 | Sprint 2, parte 10 | Consulta de alerta global calculada; regresión Java 21: 57 Surefire + 100 Failsafe sin fallos; DBML, siete contratos y 42 diagramas validados. | Parte 11: informes y cobertura condicionados; rendimiento a escala y RF-INV-07 completo pendientes. |
| 9 nov. 2026 | Sprint 2, parte 11 | JaCoCo: 57 Surefire + 100 Failsafe sin fallos; líneas Surefire 13,96 %, integración 94,70 %, combinadas 95,38 %. La medición combinada no acredita 70 % unitario. | Parte 12: integración y regresión; fórmulas/fuentes de informes, pruebas unitarias y rendimiento siguen pendientes. |
| 9 nov. 2026 | Sprint 2, parte 12 | Flujo backend de orden, recepción, conteo, ajuste, kardex y alerta; regresión: 57 Surefire + 101 Failsafe sin fallos; focalizada posterior 3 + 1 sin fallos. DBML, contratos, 42 diagramas y 86 enlaces locales verificados. | Parte 13: cierre y preparación de commit; frontend, carga, cobertura unitaria e informes pendientes. |
| 9 nov. 2026 | Sprint 2, parte 13 | [Cierre de Inventario](fase-3/SPRINT_2_CIERRE.md): revisión de Git, seguridad y evidencias; incremento preparado para revisión y commit, sin confirmar. | Planificar integración de frontend, pruebas unitarias y prioridad de siguientes módulos. |
| 10 nov. 2026 | Replanificación sprint 3 | Por decisión del usuario, [ruta de Ventas por partes](fase-3/SPRINT_3_VENTAS_POR_PARTES.md) preparada en `ventas`; sin implementación ni pruebas nuevas en esta planificación. | Iniciar parte 0; resolver políticas de crédito/reserva y replanificar Compras y siguientes sprints. |
| 10 nov. 2026 | Sprint 3, parte 0 | Preflight: repetición de `./mvnw -o verify` con SerialGC pasó 57 Surefire + 101 Failsafe; arranque aislado, DBML, siete contratos y 42 diagramas validados. Primer intento reportó cero migraciones; causa no confirmada, detalles en la [bitácora](fase-3/SPRINT_3_VENTAS_POR_PARTES.md#parte-0--resultados-del-preflight). | Parte 1: políticas, contratos y compatibilidad de cliente/Seguridad; vigilar arranque Flyway. |
| 10 nov. 2026 | Sprint 3, parte 1 | [Diseño de Ventas](fase-3/SPRINT_3_DISENO_VENTAS.md), OpenAPI propuesto y guía para frontend; se acordaron contado, zona de negocio, precio pactado y cancelación del saldo tras despacho parcial. | Parte 2: estrategia de componentes. Resolver reglas pendientes antes de implementar sus operaciones; el contrato aún no representa API activa. |
| 10 nov. 2026 | Sprint 3, parte 2 | [Auditoría y estrategia de componentes](fase-3/SPRINT_3_COMPONENTES_ESTRATEGIA.md): siete candidatos, dos para demostración posterior; 8 pruebas focalizadas sin fallos. | Parte 3: DBML y migraciones; portabilidad y sustitución aún por demostrar. |
| 10 nov. 2026 | Sprint 3, parte 3 | [V11, DBML y migraciones](fase-3/SPRINT_3_MIGRACIONES.md): siete tablas, permisos y fuente de despacho; regresión 57 Surefire + 103 Failsafe, focalizada posterior 3 + 2. DBML, ocho contratos y 42 diagramas validados. | Parte 4: dominio y persistencia. Reglas comerciales y lectura de despacho en kardex siguen pendientes. |
| 10 nov. 2026 | Sprint 3, parte 4 | [Dominio y persistencia](fase-3/SPRINT_3_DOMINIO_PERSISTENCIA.md): mapeos JPA, repositorios y valores puros; regresión 60 Surefire + 104 Failsafe, DBML y 42 diagramas validados. | Parte 5: API de clientes; escritura/transiciones de documentos quedan para partes 6–11 y sus políticas. |
| 10 nov. 2026 | Sprint 3, parte 5 | [API de clientes](fase-3/SPRINT_3_CLIENTES.md), permisos, auditoría y contrato. Java 21: 61 + 110 antes del ajuste final; Java 25 focalizado final: 1 + 6. Se registraron fallos JVM en la repetición Java 21. | Parte 6: cotizaciones; resolver vigencia y redondeo. Repetir la verificación final en Java 21 estable/CI. |
