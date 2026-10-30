# Patrones de diseño y programación funcional — backend

Este documento identifica usos **existentes** y registra usos **propuestos** para fase 3. Sirve para justificar las decisiones ante la rúbrica; nombrar un patrón no demuestra por sí solo calidad ni reutilización. Actualizar ejemplos y pruebas cuando el código cambie. Ruta de trabajo: [Fase 3](FASE_3_RUTA_BACKEND.md).

## Patrones presentes y su justificación

| Patrón o técnica | Evidencia actual | Problema que resuelve | Límite |
|---|---|---|---|
| MVC / capas de API | `security/api/AuthController.java`, `maestros/api/CategoriaProductoController.java` y los tres controladores de `Departamento`, `Proveedor`, `Almacen`, con sus servicios de aplicación | Separar contrato HTTP de coordinación y persistencia; los controladores devuelven DTO. | No implica una arquitectura estricta de puertos: servicios actuales usan repositorios Spring Data. |
| Repository / DAO | `maestros/infrastructure/persistence/CategoriaProductoRepository.java`, repositorios V6 de `Departamento`, `Proveedor`, `Almacen` y `Producto`, y repositorios de seguridad | Encapsular operaciones de persistencia y consultas JPA. El mapeo V6 se ejercita en `MasterCatalogPersistenceIT`; tres repositorios también en `MasterCatalogApiIT`. | Los repositorios no contienen reglas de negocio; producto también dispone de servicio y API. |
| Specification | `maestros/infrastructure/persistence/CategoriaProductoSpecifications.java`, `MasterCatalogSpecifications.java`; también `audit/application/AuditQueryService.java` | Componer filtros opcionales sin SQL concatenado y mantener una lista cerrada de campos consultables. La nueva especificación comparte `codigo` y `activo` entre tres catálogos. | La construcción de predicados JPA no es una función pura independiente de infraestructura. |
| Método de fábrica estático | `common/api/PageResponse.java`, método `from(Page<E>, Function<E,T>)` | Crear una respuesta paginada con un mapeador aportado por cada módulo; lo usan categorías, auditoría y notificaciones. | Es una fábrica sencilla, no una implementación del patrón GoF Factory Method ni de Abstract Factory. |
| Adapter y contrato acotado | `security/infrastructure/security/SecurityAuditActorProvider.java` adapta el usuario autenticado a `AuditActorProvider`; `common/api/AuthorizationDenialRecorder.java` desacopla el manejo HTTP de auditoría | Obtener el actor y registrar denegaciones sin acoplar cada consumidor a detalles del otro módulo. | El contrato acotado es inversión de dependencia; solo el proveedor de actor es un adaptador concreto. |
| Callback de evento tras commit | `notifications/application/AfterCommitNotificationPublisher.java` registra `TransactionSynchronization.afterCommit` | Publicar la notificación APP después de confirmar una operación y no revertir la transacción por fallo del aviso. | Es un mecanismo de observación de la transacción, no una implementación general del patrón GoF Observer. No hay productor de negocio en producción; el callback puede perderse si el proceso cae. |
| Inyección de dependencias | Beans de Spring como `AuthService` y `JpaAuditTrail` con constructor | Sustituir colaboradores en pruebas y mantener responsabilidades separadas. | El ciclo de vida singleton lo gestiona Spring; no se implementó un Singleton propio. |

`@Transactional` es un límite transaccional de Spring, no un patrón funcional. Las pruebas relevantes están en `erp-backend/src/test`: `CategoriaProductoIntegrationIT`, `AuditIntegrationIT`, `NotificationIntegrationIT` y `AuthIntegrationIT`.

## Programación funcional presente

| Concepto | Ejemplo actual | Justificación y alcance real |
|---|---|---|
| Transformación con `map` | `PageResponse.from` transforma `Page<E>` en `PageResponse<T>` mediante `Function<E,T>` y `stream().map(mapper).toList()` | Reutiliza la conversión sin conocer entidades concretas. La transformación no debe modificar `source` ni cada entidad. |
| Composición `filter` / `flatMap` / `collect` | `security/application/AuthService.java`, método `resumen`: roles activos y permisos a partir de sus colecciones | Expresa selección y proyección de datos; `TreeSet` da orden estable. La lectura parte de entidades JPA mutables, por lo que el método completo no se declara puro. |
| Predicados y `anyMatch` | `security/application/UserManagementService.java` comprueba roles administrativos y permisos delegables | Expresa condiciones sobre colecciones sin bucles de estado auxiliar; el servicio sí tiene efectos de persistencia. |
| Datos de respuesta con `record` | `common/api/ApiError.java`, `PageResponse.java` y DTO de solicitud/respuesta de catálogos | Campos finales y contrato explícito. `record` proporciona inmutabilidad superficial: un `Set` o `List` recibido puede seguir siendo mutable si no se copia. |
| Transformación determinista aislada | `maestros/infrastructure/persistence/CategoriaProductoSpecifications.java`, `escapeLike(String)` | Para la misma cadena devuelve el mismo escape y no modifica estado externo; es un ejemplo pequeño de función pura. |
| `Optional.map` | `security/infrastructure/security/SecurityAuditActorProvider.java` proyecta actor autenticado a ID | Evita un `null` intermediario y conserva explícita la ausencia del actor. Leer el contexto de seguridad sigue siendo un efecto externo. |
| Normalización determinista | `maestros/application/MasterCatalogText.java` transforma código y texto de entrada sin leer ni modificar estado externo | Para la misma entrada produce la misma salida; los servicios imperativos la aplican antes de guardar. La validación de nulos y tamaños la hace Bean Validation en el borde HTTP. |

La programación funcional aquí **convive con servicios imperativos y JPA**. No llamar «puro» a un método que consulta repositorios, usa el reloj, modifica entidades o registra auditoría. Evitar `parallelStream()` para operaciones de base de datos o con transacción.

## Aplicación prevista en fase 3

**Sprint 1, parte 1 (28 oct. 2026):** la migración V6 agregó catálogos, restricciones, índices y permisos. No se implementó ningún patrón de diseño ni función Java nueva; no se atribuye mérito de programación funcional a SQL. Su justificación y pruebas están en [documentación del módulo](fase-3/SPRINT_1_MODULOS.md) y [migraciones](fase-3/SPRINT_1_MIGRACIONES.md). Esta sección se actualizará en las partes de dominio y aplicación cuando exista código comprobable.

**Sprint 1, parte 2 (28 oct. 2026):** se reutilizó el patrón Repository/DAO con cuatro interfaces `JpaRepository`, probadas con PostgreSQL en `MasterCatalogPersistenceIT`. Las entidades JPA son modelos de persistencia mutables; no se presentan como programación funcional ni como funciones puras. Los cálculos y transformaciones funcionales previstos se añadirán solo cuando existan reglas y pruebas en las partes de aplicación.

**Sprint 1, parte 3 (28 oct. 2026):** tres servicios usan Repository y Specification para búsqueda paginada, y separan HTTP de aplicación mediante controlador y DTO. `MasterCatalogText` es una transformación determinista compartida; `PageResponse.from` recibe referencias de método (`DepartamentoService::response`, etc.) para mapear páginas. La auditoría y persistencia permanecen imperativas y transaccionales. `MasterCatalogOpenApiContractTest` y `MasterCatalogApiIT` comprueban el contrato y el flujo con PostgreSQL; no se atribuye pureza a la Specification JPA.

Estas son decisiones de implementación propuestas, no capacidades existentes:

1. **Reglas monetarias y de cantidad:** funciones pequeñas sobre valores `BigDecimal` y DTO de entrada inmutables para totales, disponibilidad y diferencias de recepción. Definir primero escala y redondeo; probar casos límite y valores inválidos. La persistencia y auditoría quedan fuera de esas funciones.
2. **Políticas intercambiables solo cuando existan variantes reales:** considerar Strategy para cálculo de precio, descuento o impuesto únicamente después de acordar reglas distintas con el equipo. No crear estrategias ficticias para cumplir la rúbrica.
3. **Filtros de nuevos módulos:** reutilizar `PageRequests`/`PageResponse` y composiciones `Specification` donde aporten claridad; demostrar con pruebas su uso fuera de categorías.
4. **Eventos de negocio:** usar el publicador de notificaciones después de commit si existe un evento y destinatario reales. Si se requiere entrega garantizada, diseñar y probar Outbox con migración propia; no etiquetar el callback actual como Outbox.
5. **Cálculo de reportes:** separar proyección SQL de cálculos deterministas de presentación. Validar fórmulas de negocio antes de escribirlas y probarlas contra datos conocidos.

## Evidencia que se debe añadir por sprint

Para cada patrón o uso funcional nuevo, registrar: requisito, clase/método real, variante descartada y motivo, prueba que lo ejercita y límite conocido. Si un patrón se elimina, retirar su afirmación de este documento. La revisión final debe poder seguir cada ejemplo desde la explicación hasta el código y una prueba ejecutada.

**Sprint 1, parte 4 (29 oct. 2026):** `ProductoController` usa DTO y delega en `ProductoService`; `ProductoRepository` y una Specification JPA encapsulan persistencia y filtros. La normalización del SKU reutiliza la transformación determinista `MasterCatalogText`; `PageResponse.from` proyecta entidades a DTO. La persistencia, auditoría y resolución de referencias son operaciones con efectos y no se consideran funciones puras.

**Sprint 1, parte 6:** `CalculoOrden` recibe records de valores y devuelve totales sin acceder a base de datos, reloj ni estado externo. La función puede probarse con medias unidades, límites y múltiples líneas; la entidad coordina sus cambios de forma imperativa. Los repositorios de orden y detalle siguen el patrón Repository. Las relaciones JPA y los métodos de transición no son funciones puras.

**Sprint 1, parte 7:** `OrdenCompraController` delega en `OrdenCompraService`, que coordina la transacción y usa el servicio `CatalogoParaCompras` como límite entre módulos. `OrdenCompraRepository` encapsula la consulta y bloqueo de órdenes; los filtros combinan predicados JPA, mientras `PageResponse.from` mapea los resultados a DTO. La consulta y la auditoría tienen efectos y no son funciones puras. Las transformaciones del dominio siguen en `CalculoOrden`.

**Sprint 1, parte 8:** `ValoracionInventario.entrada` es una función determinista sobre `BigDecimal`; devuelve cantidad y costo sin acceder a la base de datos. `InventarioService` coordina efectos transaccionales y bloqueo PostgreSQL, y `ExistenciaRepository` encapsula lecturas JPA. El uso de un método funcional para mapear páginas y la Specification de filtros no convierte la transacción ni las consultas JPA en funciones puras.

**Sprint 1, parte 9:** las entidades de recepción y sus repositorios siguen el patrón Repository; el controlador delega validaciones transaccionales al servicio. La comparación de reintentos usa transformación de líneas y `allMatch`, sin atribuir pureza a la consulta o a las entidades JPA. Un bloqueo advisory por clave y la restricción única de V7 garantizan serialización de reintentos; no constituyen por sí mismos un patrón GoF.

**Sprint 1, parte 10:** el servicio de recepción coordina la transacción y delega las entradas al servicio de inventario, reutilizando el cálculo determinista `ValoracionInventario`. El filtrado y ordenación de líneas usa `stream`, `filter` y `sorted`; la persistencia, los bloqueos y la auditoría siguen teniendo efectos y no son funciones puras. Los bloqueos PostgreSQL, la clave única y el estado confirmado aseguran idempotencia y consistencia, sin atribuirles un patrón GoF.
