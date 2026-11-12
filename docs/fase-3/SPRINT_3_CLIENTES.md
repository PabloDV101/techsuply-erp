# Sprint 3, parte 5 — API de clientes

La API implementa el catálogo de clientes de RF-MAE-04 mediante `/api/v1/customers`. Su alcance es **parcial**: los pedidos todavía no verifican el estado del cliente y no existen cálculo de saldo ni ventas a crédito. El límite y el plazo son datos maestros; un límite registrado no significa que exista crédito disponible.

| Método | Ruta | Permiso | Resultado |
|---|---|---|---|
| POST | `/customers` | `MAESTROS_CLIENTES_ADMIN`; además `MAESTROS_CLIENTES_CREDITO_ADMIN` si el límite inicial es distinto de cero | `201` y `Location`. |
| GET | `/customers` | `MAESTROS_CLIENTES_LEER` | Página ordenada por id ascendente; `page`, `size` y `activo` opcional. |
| GET | `/customers/{id}` | `MAESTROS_CLIENTES_LEER` | `200` o `404`. |
| PUT | `/customers/{id}` | `MAESTROS_CLIENTES_ADMIN`; además el permiso de crédito si cambia el límite | Reemplaza los campos del DTO; conserva el estado. |
| PATCH | `/customers/{id}/status` | `MAESTROS_CLIENTES_ADMIN` | Activa o desactiva sin eliminar; repetir el mismo estado no duplica auditoría. |

Las rutas, DTO y respuestas están en el [OpenAPI mixto](../api/openapi-sprint-3-ventas.yaml): clientes está marcado `implemented` y las rutas de Ventas siguen en diseño. El DTO requiere código y nombre no vacíos, límite no negativo con hasta 12 enteros y dos decimales, y plazo entre 0 y 32767; valida longitud de contacto y formato de correo. Código se recorta, convierte a mayúsculas con `Locale.ROOT` y se comprueba de nuevo su longitud tras normalizar; nombre se recorta, y textos opcionales vacíos se convierten en nulos. La aplicación actual ignora propiedades JSON desconocidas, como otros catálogos; los campos obligatorios deben estar presentes. El identificador fiscal es único cuando no es nulo y se compara tras recortar, conservando mayúsculas y minúsculas; no se presume una normalización fiscal que no está especificada.

Se reutilizan `PageRequests`, `PageResponse`, `MasterCatalogSpecifications`, DTO, `ApiError` y `AuditTrail`. Las respuestas de error son `400` entrada, `401` autenticación, `403` permiso, `404` cliente inexistente y `409` colisión de datos o integridad. No existe `DELETE`. Las consultas son globales para identidades con permiso; la API no es un portal y no se aplica filtro por propietario de cliente.

## Integridad, seguridad y auditoría

La autorización se comprueba en controlador y servicio. El permiso adicional de crédito se revisa en el servicio, comparando `BigDecimal` por valor; escribir un límite sin modificarlo no exige ese permiso. El `PUT` y el cambio de estado bloquean la fila en transacción antes de comprobar y modificar; esto evita que una actualización concurrente utilice un límite obsoleto para evitar la autorización. Las restricciones únicas de PostgreSQL siguen siendo la autoridad en carreras de alta, además de las comprobaciones previas.

Los cambios exitosos se registran en la misma transacción; un fallo de auditoría revierte el cliente. Solo se registran nombres de campos y estados, no valores personales ni montos. Las denegaciones siguen la política existente de mejor esfuerzo. Las acciones `CUSTOMER_CREATED`, `CUSTOMER_UPDATED` y `CUSTOMER_STATUS_CHANGED` y el campo `LIMITE_CREDITO` se añadieron al catálogo Java de auditoría; V11 ya contenía los permisos, por lo que no se añadió migración. No se modifica `usuarios.cliente_id` ni se crea FK; debe diagnosticarse antes de incorporarla.

## Evidencia

`ClienteOpenApiContractTest` contrasta rutas, DTO y estados del contrato. `ClienteApiIT` usa Bearer real y PostgreSQL de Testcontainers para el ciclo de vida, permisos separados, límites, duplicados, carrera de creación y de modificación de crédito, auditoría sin valores y rollback ante fallo de auditoría. Los escenarios usan solicitudes MockMvc y datos de prueba: no demuestran navegador, frontend, portal ni carga productiva. La guía [para frontend](../api/guia-sprint-3-ventas.md) separa las rutas disponibles de las pendientes.

La regresión `MAVEN_OPTS='-XX:+UseSerialGC' ./mvnw -o verify` pasó en Java 21 con **61 Surefire y 110 Failsafe**, sin fallos, antes de añadir la comprobación de expansión del código al convertirlo a mayúsculas. Tras ese ajuste, la prueba focalizada con Java 21 falló porque la JVM de integración terminó con `SIGSEGV`; la repetición con `SerialGC` y límites de memoria también sufrió un `SIGSEGV` en Maven antes de las pruebas. La causa no está confirmada. Una repetición focalizada de la versión final con Java 25, compilando para Java 21, pasó con **1 Surefire y 6 Failsafe**. No se modificó la versión declarada del proyecto. Además, un intento anterior de regresión falló en el plugin Maven de JAR después de 61 pruebas unitarias, y el reintento completo fue el que pasó. Debe repetirse la verificación en Java 21 en un entorno estable/CI antes de declarar reproducibilidad.

Se validaron ocho contratos OpenAPI y sus ejemplos, las dos copias DBML, 42 diagramas PlantUML y enlaces locales. La validación de ejemplos no comprueba formato de fecha ni precisión decimal de backend.
