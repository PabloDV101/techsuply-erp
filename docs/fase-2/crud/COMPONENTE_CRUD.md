# Componente CRUD reutilizable — primer uso en categorías

## Alcance y límites

La rúbrica identifica CRUD como candidato reutilizable. En el backend actual no
se introduce una superclase para entidades ni un endpoint universal: cada módulo
conserva su DTO, servicio, permisos, transacciones y reglas de negocio. Desde V4, las altas,
ediciones y cambios de estado de categorías publican eventos de auditoría en la misma
transacción; véase `docs/fase-2/auditoria/COMPONENTE_AUDITORIA.md`. Se comparten
`PageRequests` (página/tamaño acotados y lista explícita de ordenaciones),
`PageResponse<T>`, Bean Validation y el formato `ApiError` existente. Las
operaciones protegidas se implementan en el servicio dueño del recurso.

`maestros` es el primer consumidor, con `CategoriaProductoService` y
`CategoriaProductoController`. RF-MAE-02 del SRS exige categorías jerárquicas;
V3 crea `categorias_producto`, su FK al padre, índice y los permisos
`MAESTROS_CATEGORIAS_LEER` y `MAESTROS_CATEGORIAS_ADMIN`. La migración añade ambos
permisos al rol `ADMINISTRADOR` existente; no crea cuentas ni contraseñas.
Los identificadores usan `bigint`/`Long`, igual que seguridad. Las demás tablas de
Gestión Maestra siguen siendo diseño objetivo en el DBML.

## Contrato HTTP

Véase [`docs/api/openapi-crud-categorias.yaml`](../../api/openapi-crud-categorias.yaml).
Base `/api/v1/categories`:

| Operación | Respuesta | Permiso |
|---|---|---|
| `POST /categories` | `201`, `Location` y `CategoriaResponse` | `MAESTROS_CATEGORIAS_ADMIN` |
| `GET /categories/{id}` | `200` o `404` | `MAESTROS_CATEGORIAS_LEER` |
| `GET /categories?page=0&size=20&sort=codigo&direction=asc` | `200`, `PageResponse<CategoriaResponse>` | `MAESTROS_CATEGORIAS_LEER` |
| `PUT /categories/{id}` | `200`, `404` o `409` | `MAESTROS_CATEGORIAS_ADMIN` |
| `PATCH /categories/{id}/status` | `200` o `404` | `MAESTROS_CATEGORIAS_ADMIN` |

La página comienza en cero, el tamaño permitido es 1–100 y la ordenación solo
acepta `id`, `codigo` o `nombre`, con dirección `asc`/`desc`. La lista incluye
categorías inactivas para administración. No existe `DELETE`: la desactivación
conserva referencias e historial. El servicio impide el mismo padre y ciclos,
serializa cambios de jerarquía con un bloqueo transaccional de PostgreSQL y
normaliza códigos a mayúsculas. Un padre inexistente devuelve `404`; código
duplicado, ciclo o conflicto de integridad devuelve `409`. DTO inválido o
paginación incorrecta devuelve `400`. Auth conserva `401`/`403`. Los errores
mantienen `{status, code, message}` sin detalles internos.

El listado admite ahora filtros opcionales sobre código, nombre, estado y padre
directo. Véase [el componente de búsqueda](../busqueda/COMPONENTE_BUSQUEDA.md)
para sus reglas, validación y alcance.

## Reutilización en otro módulo

Un módulo nuevo define su entidad y migración, DTO públicos, repositorio, servicio
y controlador propios. Su listado puede devolver
`PageResponse.from(repository.findAll(PageRequests.of(page, size, sort, direction,
Map.of("codigo", "codigo", "nombre", "nombre"))), mapper)`; las claves públicas de
ordenación se traducen mediante un mapa permitido. El servicio decide si hay
baja lógica, reversión o ninguna eliminación. Para productos, por ejemplo, el
SKU único, precios no negativos y protección de referencias pertenecen a
Gestión Maestra; el componente compartido no impone esas reglas.

No se afirma aún reutilización entre dos módulos de negocio: categorías es el
primer consumidor real. Los documentos confirmados y movimientos contables no
deben adoptar un borrado CRUD por defecto; rigen RN-03 y RN-17.

## Verificación

Desde `erp-backend/`, con Docker disponible para PostgreSQL desechable:

```bash
./mvnw -o verify
```

`CategoriaProductoIntegrationIT` comprueba creación, consulta, actualización,
desactivación, ausencia de borrado físico, validación, unicidad, página, permisos,
FK, padre inexistente y ciclos con MockMvc + Spring Security + Flyway + PostgreSQL
real de Testcontainers. El mismo comando ejecuta regresiones de auth y validación.
El contenedor de prueba no usa ni modifica una base compartida.

Resultado ejecutado: `./mvnw -o verify` terminó en `BUILD SUCCESS`, con 34 pruebas
Surefire y 36 Failsafe, sin fallos, errores ni omisiones. Flyway aplicó V1–V3 en
un PostgreSQL desechable y Hibernate validó el esquema. El contrato OpenAPI de
categorías se comprobó contra las rutas del controlador y los campos de los DTO.
También se validaron las dos copias DBML frente a V1/V3 y se renderizaron las
42 fuentes PlantUML sin errores en la revisión inicial del CRUD.

Pendientes: integrar `PageRequests`/`PageResponse` con un segundo módulo para
demostrar reutilización transversal, definir la política de categorías inactivas
cuando existan productos y medir cobertura/carga. No se han implementado flujos
de documentos confirmados ni se afirma que sus reglas de protección estén probadas.
