# Fase 3 — ruta semanal del backend

**Estado de este plan:** propuesta de ejecución, iniciada el 27 de octubre de 2026. **Fecha objetivo del enunciado:** 17 de diciembre de 2026. El compañero del equipo desarrolla el frontend; este plan solo compromete backend, contratos HTTP y la coordinación necesaria para que pueda consumirlos.

## Cómo retomar el trabajo en un nuevo chat

1. Leer este archivo, [patrones y programación funcional](PATRONES_Y_PROGRAMACION_FUNCIONAL.md), el [SRS](SRS_TechSupply_Solutions.md) y las [instrucciones del repositorio](../AGENTS.md).
2. Revisar rama, cambios sin confirmar, código y pruebas reales antes de escoger la siguiente tarea. No inferir que una casilla está terminada solo por este plan.
3. Confirmar el sprint activo con la tabla de avance y el último resultado de `cd erp-backend && ./mvnw -o verify`. Si hay diferencias, actualizar aquí el estado con evidencia.
4. Implementar una historia vertical a la vez: migración Flyway, reglas de negocio, API y permisos, auditoría, OpenAPI y pruebas de integración. Mantener los cambios ajenos intactos.
5. Al cerrar una historia, anotar requisito, archivos, comando y resultado, decisión pendiente y siguiente paso. No marcarla terminada sin verificación ejecutada.

## Fuentes y alcance

- [Enunciado de proyecto](rubrica/proyecto%20final%20dise%C3%B1o.pdf): fase 3, módulos y fecha. El documento dice sprints de dos semanas pero enumera doce; por restricción de tiempo del equipo se usarán **siete sprints de una semana** y una ventana final de corrección.
- [Rúbrica final](rubrica/rubrica_final.pdf): arquitectura, componentes reutilizables, funcionalidad, integridad, pruebas, código y documentación. Es una rúbrica de entrega final, no una prueba de que el diseño ya esté implementado.
- [SRS](SRS_TechSupply_Solutions.md), [modelo de datos](../database/modelo.dbml) y [componentes de fase 2](fase-2/integracion/COMPONENTES_FASE_2.md) rigen las reglas y el estado inicial. Los diagramas y el DBML mezclan estado actual y objetivo; las migraciones Flyway son el esquema ejecutable.
- No se implementan requisitos futuros u opcionales por el solo hecho de aparecer en la narrativa. Cambios de alcance o reglas de negocio pendientes requieren decisión del equipo/docente.

## Punto de partida comprobado

El backend tiene seguridad con Bearer opaco, usuarios/roles/permisos, validación y errores HTTP, categorías con búsqueda, auditoría y bandeja APP. Flyway llega a V5. No hay en producción productos, proveedores, clientes, almacenes operativos, compras, existencias, ventas, contabilidad ni reportes. El contrato de reportería es diseño. El frontend se desarrolla por separado.

La última verificación comunicada antes de crear este plan fue `./mvnw -o verify` el 27 de octubre de 2026: 41 pruebas Surefire y 49 Failsafe aprobadas con PostgreSQL de Testcontainers. Repetirla tras los cambios; esta cifra no mide cobertura de código ni rendimiento concurrente.

## Calendario propuesto y entregables

| Sprint | Fechas | Entrega backend verificable | Requisitos / aceptación | Estado |
|---|---|---|---|---|
| 1 | 27 oct.–2 nov. | Productos, proveedores, clientes y almacenes mínimos. Asociar producto a categoría. Búsqueda y permisos por recurso. | RF-MAE-01 a 05 según alcance; SKU único, importes no negativos, maestros inactivos rechazados en nuevas operaciones. | Pendiente |
| 2 | 3–9 nov. | Existencia y movimiento base; orden de compra y recepción parcial confirmable. | RF-COM-03 a 05, RF-INV-01/02, CA-01; recepción, stock y movimiento atómicos. | Pendiente |
| 3 | 10–16 nov. | Requisición y aprobación; conteos, ajustes autorizados, devoluciones y alertas simples de stock. | RF-COM-01/02/07, RF-INV-05 a 07, CA-06. Priorización interna según avance real. | Pendiente |
| 4 | 17–23 nov. | Cotización, pedido, validación comercial, reserva y despacho parcial; factura comercial si el flujo queda cerrado. | RF-VEN-01 a 05, RF-INV-03/04, CA-02/03; concurrencia e idempotencia comprobadas. | Pendiente |
| 5 | 24–30 nov. | Periodos, plan de cuentas, asientos y reversión; cuentas por pagar/cobrar y aplicaciones de pagos sobre documentos disponibles. | RF-CON-01 a 06, CA-04/05; equilibrio, periodo abierto, saldo e idempotencia. | Pendiente |
| 6 | 1–7 dic. | Reportes sobre fuentes reales, permisos de filas y primer dashboard; cerrar históricos y notificaciones de negocio que tengan productor y destinatario definidos. | RF-REP-01 a 04 según fuentes existentes, CA-08; no publicar cifras sin fórmula acordada. | Pendiente |
| 7 | 8–14 dic. | Integración de contratos con frontend del compañero, regresión, pruebas de carga y cobertura, manual técnico, trazabilidad y preparación de demostración. | RNF-02/03/04/12/17; evidencia reproducible y defectos corregidos. | Pendiente |
| Cierre | 15–17 dic. | Correcciones de hallazgos, revisión de entrega y documentación final. | No iniciar un módulo grande nuevo en esta ventana. | Pendiente |

**Dependencias:** la recepción necesita existencias y movimientos, por eso su base se construye en el sprint 2 aunque el enunciado enumere compras antes de inventario. Facturación y contabilidad necesitan documentos operativos válidos; reportería necesita fuentes y fórmulas aprobadas. El calendario es una secuencia de prioridades, no una promesa de que todas las funcionalidades caben en siete semanas.

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
| Fórmulas y reglas | Acordar stock mínimo/reorden, crédito, impuestos/descuentos, rotación, ABC, rentabilidad, estados financieros y zona de negocio antes de calcularlos. Ver SRS §12.2. |
| Cobertura y CI | Configurar medición; objetivo de la narrativa: mínimo 70 %, rúbrica excelente: 80 % o más. Ningún porcentaje se ha medido aún. |
| Rendimiento | Diseñar datos y carga representativos; objetivos: p95 < 2 s en consultas, p95 < 5 s en reportes y evaluación de 200 usuarios concurrentes. No inferir cumplimiento de pruebas MockMvc secuenciales. |
| Configuración local | Hay cambios sin confirmar en `application.yaml`; revisar especialmente cualquier contraseña por defecto antes de entregar o desplegar. No alterar cambios de otro autor sin revisarlos. |
| Coordinación con frontend | Entregar OpenAPI y ejemplos por sprint; acordar autenticación, errores, paginación y estados con el compañero. La implementación del frontend no forma parte de este plan. |

## Bitácora de avance

Añadir una fila por historia cerrada o cambio de plan. Registrar hechos, no estimaciones.

| Fecha | Sprint / requisito | Resultado comprobado y comando | Pendiente o decisión |
|---|---|---|---|
| 27 oct. 2026 | Inicio del plan | Fase 2: `./mvnw -o verify`, 41 Surefire + 49 Failsafe aprobadas en la revisión anterior. | Iniciar sprint 1; confirmar alcance de RR. HH. y reglas abiertas. |
