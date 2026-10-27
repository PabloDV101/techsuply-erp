# Patrones de diseño y programación funcional — backend

Este documento identifica usos **existentes** y registra usos **propuestos** para fase 3. Sirve para justificar las decisiones ante la rúbrica; nombrar un patrón no demuestra por sí solo calidad ni reutilización. Actualizar ejemplos y pruebas cuando el código cambie. Ruta de trabajo: [Fase 3](FASE_3_RUTA_BACKEND.md).

## Patrones presentes y su justificación

| Patrón o técnica | Evidencia actual | Problema que resuelve | Límite |
|---|---|---|---|
| MVC / capas de API | `security/api/AuthController.java`, `maestros/api/CategoriaProductoController.java` y sus servicios de aplicación | Separar contrato HTTP de coordinación y persistencia; los controladores devuelven DTO. | No implica una arquitectura estricta de puertos: servicios actuales usan repositorios Spring Data. |
| Repository / DAO | `maestros/infrastructure/persistence/CategoriaProductoRepository.java` y repositorios de seguridad | Encapsular operaciones de persistencia y consultas JPA. | Los repositorios no contienen reglas de negocio. |
| Specification | `maestros/infrastructure/persistence/CategoriaProductoSpecifications.java`; también `audit/application/AuditQueryService.java` | Componer filtros opcionales sin SQL concatenado y mantener una lista cerrada de campos consultables. | La construcción de predicados JPA no es una función pura independiente de infraestructura. |
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
| Datos de respuesta con `record` | `common/api/ApiError.java`, `PageResponse.java` y DTO de solicitud/respuesta | Campos finales y contrato explícito. `record` proporciona inmutabilidad superficial: un `Set` o `List` recibido puede seguir siendo mutable si no se copia. |
| Transformación determinista aislada | `maestros/infrastructure/persistence/CategoriaProductoSpecifications.java`, `escapeLike(String)` | Para la misma cadena devuelve el mismo escape y no modifica estado externo; es un ejemplo pequeño de función pura. |
| `Optional.map` | `security/infrastructure/security/SecurityAuditActorProvider.java` proyecta actor autenticado a ID | Evita un `null` intermediario y conserva explícita la ausencia del actor. Leer el contexto de seguridad sigue siendo un efecto externo. |

La programación funcional aquí **convive con servicios imperativos y JPA**. No llamar «puro» a un método que consulta repositorios, usa el reloj, modifica entidades o registra auditoría. Evitar `parallelStream()` para operaciones de base de datos o con transacción.

## Aplicación prevista en fase 3

Estas son decisiones de implementación propuestas, no capacidades existentes:

1. **Reglas monetarias y de cantidad:** funciones pequeñas sobre valores `BigDecimal` y DTO de entrada inmutables para totales, disponibilidad y diferencias de recepción. Definir primero escala y redondeo; probar casos límite y valores inválidos. La persistencia y auditoría quedan fuera de esas funciones.
2. **Políticas intercambiables solo cuando existan variantes reales:** considerar Strategy para cálculo de precio, descuento o impuesto únicamente después de acordar reglas distintas con el equipo. No crear estrategias ficticias para cumplir la rúbrica.
3. **Filtros de nuevos módulos:** reutilizar `PageRequests`/`PageResponse` y composiciones `Specification` donde aporten claridad; demostrar con pruebas su uso fuera de categorías.
4. **Eventos de negocio:** usar el publicador de notificaciones después de commit si existe un evento y destinatario reales. Si se requiere entrega garantizada, diseñar y probar Outbox con migración propia; no etiquetar el callback actual como Outbox.
5. **Cálculo de reportes:** separar proyección SQL de cálculos deterministas de presentación. Validar fórmulas de negocio antes de escribirlas y probarlas contra datos conocidos.

## Evidencia que se debe añadir por sprint

Para cada patrón o uso funcional nuevo, registrar: requisito, clase/método real, variante descartada y motivo, prueba que lo ejercita y límite conocido. Si un patrón se elimina, retirar su afirmación de este documento. La revisión final debe poder seguir cada ejemplo desde la explicación hasta el código y una prueba ejecutada.
