# Sprint 3, parte 2 — componentes y estrategia técnica

**Estado: auditoría documental terminada; selección y pruebas futuras propuestas.** No se creó una biblioteca, interfaz ni implementación nueva de Ventas. Esta evaluación complementa la evidencia histórica de [Fase 2](../fase-2/integracion/COMPONENTES_FASE_2.md) y el [diseño del sprint](SPRINT_3_DISENO_VENTAS.md). La integración de Fase 2 describe su momento de cierre: sus afirmaciones sobre V1–V5 y categorías como único catálogo no son el estado actual, que en la revisión de parte 2 llegaba a V10 y comprendía Compras e Inventario; V11 añadió después el esquema base de Ventas.

## Criterio de evaluación

La [rúbrica final](../rubrica/rubrica_final.pdf), en el nivel superior de componentes reutilizables, pide identificar y documentar al menos siete, con interfaz documentada y uso demostrado en al menos tres contextos cada uno. La [narrativa del proyecto](../rubrica/proyecto%20final%20dise%C3%B1o.pdf) menciona al menos cinco implementados y reutilización en múltiples contextos. El objetivo más exigente es el primero; ni contar clases ni pruebas simuladas como proyectos externos demuestra cumplimiento. La rúbrica parece intercambiar los rótulos de programación funcional y patrones: aquí se atiende al contenido de sus descripciones, sin atribuir capacidades no implementadas. El alcance de tres contextos no define necesariamente tres proyectos; demostrar portabilidad a otro proyecto es un objetivo adicional solicitado por el usuario.

**Contexto** en este inventario significa módulo o flujo real distinguible; se identifica cada uno en lugar de contar varias llamadas del mismo caso como consumidores independientes. «Código existente», «usos localizados» y «evidencia pasada» no equivalen a una medición de cumplimiento. Para cada componente de entrega aún falta una ficha con interfaz pública completa, condiciones, versión, dependencias, prueba ejecutada por contexto y evaluación como componente completo. Las pruebas históricas se referencian sin afirmar que se ejecutaron en esta parte.

## Los siete candidatos de Fase 2

| Candidato | Contrato y dependencias actuales | Usos reales localizados / evidencia existente | Brecha y decisión para Ventas |
|---|---|---|---|
| Autenticación y autorización | Bearer opaco, `AuthenticatedUserProvider`, Spring Security, base de sesiones y permisos. | Seguridad y protección de Maestros, Compras e Inventario; `AuthIntegrationIT`, `MasterCatalogApiIT`, `AjusteInventarioIT`. | Reusar seguridad y agregar permisos mínimos al implementar. La portabilidad requiere adaptación de identidad, tablas y configuración; no se ha probado fuera del ERP. |
| Búsqueda y paginación | `PageRequests.of(...)`, `PageResponse.from(Page, Function)` y filtros específicos; Spring Data/HTTP. | Maestros, auditoría, notificaciones, Compras e Inventario usan las utilidades; `MasterCatalogApiIT`, `MovimientoConsultaIT`. | Aplicar paginación y listas de campos permitidos a Ventas; cada módulo conserva sus propios filtros. Varios consumidores de utilidades no prueban que exista un motor de búsqueda portable completo. |
| Reportería | Contrato HTTP objetivo en `openapi-reporteria.yaml`; fuentes y fórmulas dependen de cada módulo. | Diseño documentado; sin controladores ni ejecución integrada de reportes. | No contar como implementado ni como consumidor de Ventas; requiere fuentes verificadas, fórmulas, permisos y medición. |
| Auditoría | `AuditTrail.change(...)`; interfaz con enums y DTO propios de auditoría, `JpaAuditTrail`, Spring, PostgreSQL y actor. | Seguridad, Maestros, Compras e Inventario lo invocan; `AuditIntegrationIT`, `ConfirmarRecepcionIT`, `AplicarAjusteIT`. | Reusar acciones explícitas, actor y transacción en Ventas. Los cambios exitosos exigen transacción (`MANDATORY`); una sustitución debe respetar rollback y denegaciones de mejor esfuerzo. No se ha demostrado una segunda implementación compatible. |
| Notificaciones | `NotificationPublisher` y bandeja APP; callback tras commit y persistencia Spring/JPA. | Infraestructura y bandeja; `NotificationIntegrationIT` emplea productor de prueba. | No hay productor de negocio en producción demostrado ni entrega garantizada. Añadir solo ante evento y destinatario reales; no afirmar email/SMS u Outbox. |
| Validación | Bean Validation/DTO, reglas en dominios y `GlobalApiExceptionHandler`; no existe un único motor genérico del negocio. | Seguridad, Maestros, Compras e Inventario validan entradas; `GlobalApiExceptionHandlerTest`, pruebas de dominio y API. | Reusar validación de borde y formato `ApiError`; las reglas comerciales permanecen en Ventas. Documentar interfaces verificables antes de contar un componente completo. |
| CRUD | Controladores/servicios/repositorios y DTO específicos de cada catálogo; no existe CRUD genérico portable. | Categorías, departamentos, proveedores, almacenes y productos; `CategoriaProductoIntegrationIT`, `MasterCatalogApiIT`. | Reusar convenciones y componentes comunes para clientes, no herencia genérica ni borrar historial. Diferentes catálogos son consumidores de un enfoque, no prueba de una biblioteca CRUD intercambiable. |

`ApiError(status, code, message)` se usa tanto en manejo común como en seguridad; es un contrato de datos, no toda la gestión de errores. El manejador común conoce `AuthorizationDenialRecorder`, cuyo método `deniedAuthorization()` desacopla la denegación de la implementación de auditoría. Mantener sus respuestas y cabeceras probadas al integrar Ventas.

## Candidatos seleccionados para portabilidad y sustitución

1. **Paginación y proyección:** `PageResponse.from(Page<E>, Function<E,T>)` recibe una función sustituible y se usa en distintos módulos. `PageResponseTest` pasa dos proyecciones distintas sobre la misma página y verifica contenido y metadatos. Esto demuestra sustitución del **mapeador**, no de toda la infraestructura de paginación; `Page` exige Spring Data y `PageRequests` también depende de Spring. La prueba es un consumidor dentro del mismo build, no acredita traslado a otro proyecto.
2. **Cálculo determinista compartido:** la parte 13 extrajo la aritmética común de `CalculoOrden` y `CalculoCotizacion` a `common/domain/CalculoImportes`, con JDK (`BigDecimal`) solamente. Los módulos conservan sus tipos de línea y validaciones propias, que adaptan los valores al componente común. La misma función se usa en Compras y en cotizaciones, pedidos y facturas de Ventas. `CalculoImportesTest`, `CalculoOrdenTest` y `CalculoCotizacionTest` fijan redondeo, límites y compatibilidad. Esto prueba reutilización real entre módulos, bajo la política aprobada; no hay una segunda política aprobada que permita demostrar sustitución de estrategia, ni un artefacto probado desde otro proyecto.

Compras y Ventas aprobaron la misma política de descuento e impuesto por línea con `HALF_UP`; la parte 13 comparó casos de frontera y extrajo solo la aritmética a `CalculoImportes`, sin dependencia `ventas → compras`. La validación de cantidades, escalas y porcentajes sigue en los tipos de cada módulo, y debe conservarse al introducir nuevos consumidores. No mezclar el costo promedio de entradas con el precio de venta ni volver a calcular el promedio al despachar sin regla aprobada.

## Idempotencia y concurrencia

Recepciones y ajustes normalizan clave, usan `pg_advisory_xact_lock(hashtextextended(...))` parametrizado con espacios distintos, consultan una clave única y comparan contenido en funciones específicas. El bloqueo de PostgreSQL y la restricción son complementarios; cada agregado define la equivalencia de líneas, escalas y orden. `RecepcionCompraApiIT` y `AjusteInventarioIT` son evidencia histórica disponible, no una biblioteca genérica ni pruebas ejecutadas hoy. Para Ventas repetir el diseño de transacción, unicidad, equivalencia y pruebas con carreras reales. Evaluar extracción únicamente después de comprobar que contrato, ámbitos, bloqueo, versión de clave y fallas son compatibles; no acoplar el dominio a `JdbcTemplate` como solución portable ni confiar solo en caché o mocks.

## Plan por etapa y evidencia

- **Parte 3:** mantener interfaces actuales y preparar solo esquema, integridad y permisos; no introducir biblioteca ni registrar como habilitado un componente de Ventas.
- **Partes 4–11:** separar cálculo puro de efectos; reutilizar seguridad, errores, paginación y auditoría en los flujos reales. Para cada adopción anotar ruta, permisos, contrato, prueba, dependencias y contexto; contrastar reglas y transacciones. Tests de dominio para valores límite y tests de PostgreSQL para idempotencia, concurrencia y rollback.
- **Parte 13:** ejecutados consumidores de prueba de cálculo y paginación. La sustitución demostrada se limita al mapper de proyección; el cálculo es compartido bajo la misma política aprobada. Sigue pendiente una prueba desde proyecto independiente. La matriz de siete componentes conserva sus brechas de tres contextos y portabilidad externa.
- **Partes 14–15:** integrar pruebas y actualizar [patrones y programación funcional](../PATRONES_Y_PROGRAMACION_FUNCIONAL.md), matriz, contratos, diagramas y cierre. Si el tiempo o funcionalidad no alcanzan, reportar la brecha sin atribuir cobertura por inferencia.

## Validación de esta parte

Con Java 21, desde `erp-backend/`, la suite focalizada de cálculo y sustitución terminó con **9 pruebas sin fallos**. La regresión completa con JaCoCo pasó: **72 Surefire y 183 Failsafe sin fallos**. Las líneas cubiertas fueron **12,78 % en unitarias (371/2.902), 89,50 % en integración (2.599/2.904) y 90,12 % combinado (2.617/2.904)**. La meta unitaria de 70 % continúa incumplida; integración no sustituye esa meta. `CalculoImportes` obtuvo 100 % de líneas en Surefire (17/17). Se verificó con `jdeps -verbose:class target/classes/com/techsupply/erp_backend/common/domain/CalculoImportes.class` que el cálculo común solo depende de `java.base`.

Comandos reproducibles desde `erp-backend/`:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' -Dtest=CalculoImportesTest,PageResponseTest,CalculoOrdenTest,CalculoCotizacionTest test
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o -Pcoverage -Dcoverage.jvm.args='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
jdeps -verbose:class target/classes/com/techsupply/erp_backend/common/domain/CalculoImportes.class
```

La regresión verifica compatibilidad con los módulos actuales; los tests de sustitución corren dentro del mismo build. No se probó un consumidor en otro proyecto, no se completa la rúbrica de siete componentes × tres contextos y no se cambia esquema, API ni UML en esta parte.
