# Fase 3 — ruta semanal del backend

**Estado de este plan:** propuesta de ejecución, iniciada el 27 de octubre de 2026. **Fecha objetivo del enunciado:** 17 de diciembre de 2026. El compañero del equipo desarrolla el frontend; este plan solo compromete backend, contratos HTTP y la coordinación necesaria para que pueda consumirlos.

## Cómo retomar el trabajo en un nuevo chat

1. Leer este archivo, [patrones y programación funcional](PATRONES_Y_PROGRAMACION_FUNCIONAL.md), el [SRS](SRS_TechSupply_Solutions.md) y las [instrucciones del repositorio](../AGENTS.md).
2. Revisar rama, cambios sin confirmar, código y pruebas reales antes de escoger la siguiente tarea. No inferir que una casilla está terminada solo por este plan.
3. Confirmar el sprint activo con la tabla de avance y el último resultado de `cd erp-backend && ./mvnw -o verify`. Si hay diferencias, actualizar aquí el estado con evidencia.
   Para el sprint 1, seguir la primera parte pendiente con evidencia en [ejecución por partes](fase-3/SPRINT_1_EJECUCION_POR_PARTES.md).
4. Implementar una historia vertical a la vez: migración Flyway, reglas de negocio, API y permisos, auditoría, OpenAPI y pruebas de integración. Mantener los cambios ajenos intactos.
5. Al cerrar una historia, anotar requisito, archivos, comando y resultado, decisión pendiente y siguiente paso. No marcarla terminada sin verificación ejecutada.

## Fuentes y alcance

- [Enunciado de proyecto](rubrica/proyecto%20final%20dise%C3%B1o.pdf): fase 3, módulos y fecha. El documento dice sprints de dos semanas pero enumera doce; por restricción de tiempo del equipo se usarán **siete sprints de una semana** y una ventana final de corrección.
- [Rúbrica final](rubrica/rubrica_final.pdf): arquitectura, componentes reutilizables, funcionalidad, integridad, pruebas, código y documentación. Es una rúbrica de entrega final, no una prueba de que el diseño ya esté implementado.
- [SRS](SRS_TechSupply_Solutions.md), [modelo de datos](../database/modelo.dbml) y [componentes de fase 2](fase-2/integracion/COMPONENTES_FASE_2.md) rigen las reglas y el estado inicial. Los diagramas y el DBML mezclan estado actual y objetivo; las migraciones Flyway son el esquema ejecutable.
- No se implementan requisitos futuros u opcionales por el solo hecho de aparecer en la narrativa. Cambios de alcance o reglas de negocio pendientes requieren decisión del equipo/docente.

## Punto de partida comprobado

Al iniciar el sprint, el backend tenía seguridad con Bearer opaco, usuarios/roles/permisos, validación y errores HTTP, categorías con búsqueda, auditoría y bandeja APP; Flyway llegaba a V5. La parte 1 añadió V6 con cuatro catálogos SQL y permisos; la parte 2 añadió sus entidades JPA y repositorios; la parte 3 añadió servicios y API de departamentos, proveedores y almacenes. La parte 4 añadió API de producto. V7 creó el esquema inicial de compras y existencias; la parte 6 añadió entidades, repositorios y cálculo de órdenes. La parte 7 añadió servicios y API de órdenes; la parte 8 añadió el núcleo de inventario y consulta de existencias. La parte 9 añadió registro y consulta de recepciones; la parte 10 añadió confirmación transaccional. Ventas, contabilidad y reportes siguen sin implementación. El contrato de reportería es diseño. El frontend se desarrolla por separado.

La verificación comunicada antes de crear este plan fue `./mvnw -o verify` el 27 de octubre de 2026: se informaron 41 pruebas Surefire y 49 Failsafe. No se conserva una salida íntegra de esa ejecución para reconfirmar el primer número. En la ejecución de la parte 0, los reportes vigentes sumaron 40 Surefire y 49 Failsafe; el XML adicional era antiguo y seguía en `target/`. Ninguna cifra mide cobertura de código ni rendimiento concurrente.

## Calendario propuesto y entregables

| Sprint | Fechas | Entrega backend verificable | Requisitos / aceptación | Estado |
|---|---|---|---|---|
| 1 | 27 oct.–2 nov. | Gestión Maestra y Compras: proveedores, productos, departamentos, almacenes, orden y recepción parcial con inventario mínimo. [Partes verificables](fase-3/SPRINT_1_EJECUCION_POR_PARTES.md), [plan](fase-3/SPRINT_1_GESTION_MAESTRA_COMPRAS.md), [migraciones](fase-3/SPRINT_1_MIGRACIONES.md), [módulos](fase-3/SPRINT_1_MODULOS.md), [OpenAPI del sprint](api/openapi-sprint-1-maestros-compras.yaml). | RF-MAE-01/02/03 y RF-MAE-05 parcial; RF-COM-03/04/05; RF-INV-01/02 parcial; CA-01. | Partes 0–11 cerradas; ver límites y preparación de commit en [cierre](fase-3/SPRINT_1_CIERRE.md) |
| 2 | 3–9 nov. | Completar pendientes prioritarios de Gestión Maestra/Compras según evidencia del sprint 1: requisición, factura y devolución cuando sus dependencias estén listas; consolidar inventario. | RF-MAE-04/05, RF-COM-01/02/06/07/08 y RF-INV-01/02 según alcance real; no marcar completo sin pruebas. | Pendiente |
| 3 | 10–16 nov. | Inventario: conteos, ajustes autorizados, alertas simples y consultas de rotación basadas en movimientos reales. Cerrar defectos del flujo de compras antes de avanzar. | RF-INV-05 a 07, CA-06; priorizar según evidencia real del sprint 2. | Pendiente |
| 4 | 17–23 nov. | Cotización, pedido, validación comercial, reserva y despacho parcial; factura comercial si el flujo queda cerrado. | RF-VEN-01 a 05, RF-INV-03/04, CA-02/03; concurrencia e idempotencia comprobadas. | Pendiente |
| 5 | 24–30 nov. | Periodos, plan de cuentas, asientos y reversión; cuentas por pagar/cobrar y aplicaciones de pagos sobre documentos disponibles. | RF-CON-01 a 06, CA-04/05; equilibrio, periodo abierto, saldo e idempotencia. | Pendiente |
| 6 | 1–7 dic. | Reportes sobre fuentes reales, permisos de filas y primer dashboard; cerrar históricos y notificaciones de negocio que tengan productor y destinatario definidos. | RF-REP-01 a 04 según fuentes existentes, CA-08; no publicar cifras sin fórmula acordada. | Pendiente |
| 7 | 8–14 dic. | Integración de contratos con frontend del compañero, regresión, pruebas de carga y cobertura, manual técnico, trazabilidad y preparación de demostración. | RNF-02/03/04/12/17; evidencia reproducible y defectos corregidos. | Pendiente |
| Cierre | 15–17 dic. | Correcciones de hallazgos, revisión de entrega y documentación final. | No iniciar un módulo grande nuevo en esta ventana. | Pendiente |

**Dependencias:** por decisión del equipo, Gestión Maestra y el flujo central de Compras se adelantan al sprint 1. La recepción necesita existencias y movimientos, por eso su base se construye en esa misma semana. Facturación y contabilidad necesitan documentos operativos válidos; reportería necesita fuentes y fórmulas aprobadas. El calendario es una secuencia de prioridades, no una promesa de que todas las funcionalidades caben en siete semanas.

## Criterio de terminado para cada historia

- Requisito y caso de aceptación identificados; decisión de negocio no inventada.
- Migración Flyway nueva y coherente con entidad, restricciones, claves e índices. No editar V1–V5 ya aplicadas.
- Operación transaccional, autorización en backend, DTO y validación de entrada; errores con el formato existente.
- Auditoría de cambios críticos sin secretos; idempotencia y concurrencia probadas cuando corresponda.
- OpenAPI y consumidor frontend coordinados si cambia el contrato. No presentar rutas de diseño como activas.
- Pruebas unitarias de reglas, HTTP/autorización e integración PostgreSQL según el riesgo. Ejecutar `./mvnw -o verify` y registrar resultado real.
- Evidencia de reutilización y de patrones/programación funcional actualizada en [su documento](PATRONES_Y_PROGRAMACION_FUNCIONAL.md). Usar patrones solo si simplifican el código.

## Seguimiento y decisiones abiertas

| Tema | Estado / siguiente decisión |
|---|---|
| Alcance de RR. HH. y portal de clientes | El SRS los incluye, pero la lista de siete módulos principales de la narrativa no cuenta RR. HH. como módulo principal. Confirmar con docente/equipo si entran en la entrega funcional y ajustar esta ruta. |
| Fórmulas y reglas | [Políticas de compras e inventario del sprint 1](fase-3/SPRINT_1_POLITICAS.md) fijadas para el MVP, aún sin implementar. Crédito, rotación, ABC, rentabilidad, estados financieros y zona de negocio requieren definición antes de calcularlos. |
| Cobertura y CI | Configurar medición; objetivo de la narrativa: mínimo 70 %, rúbrica excelente: 80 % o más. Ningún porcentaje se ha medido aún. |
| Rendimiento | Diseñar datos y carga representativos; objetivos: p95 < 2 s en consultas, p95 < 5 s en reportes y evaluación de 200 usuarios concurrentes. No inferir cumplimiento de pruebas MockMvc secuenciales. |
| Configuración local | Hay cambios sin confirmar en `application.yaml`; revisar especialmente cualquier contraseña por defecto antes de entregar o desplegar. No alterar cambios de otro autor sin revisarlos. |
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
