# Búsqueda avanzada reutilizable — categorías

## Decisión y alcance comprobable

RF-REP-02 pide filtrar, ordenar y paginar catálogos y reportes. El único catálogo
de negocio implementado es `categorias_producto` (RF-MAE-02, parcialmente
implementado); por eso la primera integración es `GET /api/v1/categories`.
Se conservan su ruta, permiso `MAESTROS_CATEGORIAS_LEER`, respuesta `PageResponse`
y errores `{status, code, message}`. Los filtros se basan en campos presentes en
la entidad, V3 y el DBML; no se atribuyen reglas de búsqueda a módulos pendientes.

Se eligió `JpaSpecificationExecutor` de Spring Data JPA, ya incluido en Maven.
`CategoriaProductoSpecifications` construye predicados Criteria con propiedades
conocidas y valores ligados por JPA. El cliente solo escoge claves de ordenación
de la lista permitida de `PageRequests`; nunca envía nombres de columnas ni SQL.
Cada módulo conserva sus filtros y reglas de acceso. Lo reutilizable es la
paginación acotada, el ordenamiento permitido, `PageResponse`, el formato de
errores y el patrón de composición de predicados; no se introduce un buscador
universal que conozca entidades de otros módulos.

## Contrato HTTP

El contrato completo está en [`openapi-crud-categorias.yaml`](../../api/openapi-crud-categorias.yaml).

| Parámetro opcional | Semántica |
|---|---|
| `codigo` | Coincidencia exacta tras `trim` y conversión a mayúsculas; 1–20 caracteres útiles. |
| `nombre` | Subcadena sin distinguir mayúsculas; 1–100 caracteres útiles. `%`, `_` y `\` son literales. |
| `activa` | Solo `true` o `false`. Omitido: ambos estados. |
| `categoriaPadreId` | Identificador positivo del padre directo; no recorre descendientes. |

Los filtros presentes se combinan con **AND**. Un parámetro de texto presente
pero vacío se rechaza con `400`; no amplía accidentalmente la consulta.
`page` empieza en 0; `size` admite 1–100 (predeterminado 20); `sort` admite
`id`, `codigo`, `nombre` (predeterminado `codigo`), y `direction` admite
`asc`/`desc`. `id` desempata ordenaciones no únicas. Sin coincidencias o con
una página fuera de rango, la respuesta `200` tiene `content: []` y conserva
los metadatos de página. Errores de parámetros usan `ApiError`; faltan token o
permiso producen `401` y `403`, respectivamente.

Ejemplo:

```http
GET /api/v1/categories?nombre=conectores&activa=true&categoriaPadreId=42&page=0&size=20&sort=nombre&direction=asc
Authorization: Bearer <token-de-prueba>
```

## Integración en otro módulo

El módulo dueño define un criterio de entrada validado y sus `Specification`
con campos de entidad fijos. Su servicio combina **siempre** cualquier
restricción de visibilidad derivada del actor autenticado con los filtros
opcionales antes de `findAll(specification, pageable)`. Después convierte
entidades a DTO con `PageResponse.from(...)`. En un futuro portal de clientes,
por ejemplo, el alcance de filas debe venir del backend y probarse con dos
identidades; no se debe aceptar como filtro opcional elegido por el cliente.
Categorías hoy usa autorización por permiso y no tiene aislamiento por fila.

## Verificación y límites

Desde `erp-backend/`, con Docker disponible para el PostgreSQL desechable de
Testcontainers:

```bash
./mvnw -o verify
```

`CategoriaProductoIntegrationIT` cubre filtros ausentes, simples y combinados,
límites y entradas inválidas, caracteres especiales literales, ausencia de
coincidencias, páginas vacías, orden estable, un lote de 1500 registros y autorización sobre la API real con Spring
Security, Flyway y PostgreSQL. `CategoriaOpenApiContractTest` comprueba rutas,
esquemas y parámetros documentados. El DBML no cambia: la búsqueda no añade
columnas, relaciones ni restricciones; V3 ya contiene índice del padre y
unicidad de código.

Verificación ejecutada el 19 de octubre de 2026: `./mvnw -o verify` terminó
`BUILD SUCCESS`, con 34 pruebas unitarias/de contrato y 36 de integración,
sin fallos ni omisiones. En el entorno local, después de cargar 1500 categorías,
20 solicitudes secuenciales de MockMvc (`nombre`, página 14, tamaño 100) dieron
p95 = 14 ms. Esta muestra mide el recorrido de la API en un solo proceso; no
constituye una prueba de carga representativa ni demuestra RNF-02.

RNF-02 (p95 < 2 s bajo carga representativa) y los 200 usuarios concurrentes
son objetivos pendientes de medir con volumen y entorno documentados. La
búsqueda parcial por nombre puede requerir un índice apropiado solo si las
mediciones de PostgreSQL lo justifican; no se añade una migración especulativa.
La reutilización en un segundo módulo y el aislamiento por fila siguen
pendientes porque esos módulos aún no están implementados.
