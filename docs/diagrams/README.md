# Diagramas y modelo de datos — TechSupply Solutions ERP

Revisión documental: 28 de octubre de 2026. Estos archivos distinguen el estado implementado
de la arquitectura objetivo. La existencia de una clase, caso de uso o flujo dibujado no
acredita su implementación ni el cierre de una fase.

## Lectura y alcance

| Estado | Representación | Significado |
|---|---|---|
| Implementado | Azul y `<<implementado>>` | Seguridad, validación/errores HTTP, categorías, búsqueda, auditoría y bandeja interna APP; V6 añade cuatro catálogos SQL/JPA, con API para departamentos, proveedores, almacenes y productos. |
| SQL actual | Verde y `<<SQL>>` | Tablas reales sin clase JPA propia, como `identificadores_usuario` y `usuarios_roles`. |
| Objetivo | Gris, `<<objetivo>>` o leyenda de diseño objetivo | Funcionalidad prevista; no está implementada por aparecer aquí. |
| Opcional/futuro | Texto explícito, notas y estereotipos de futuro | Alternativas del diseño, sujetas a decisión posterior. |
| Vista mixta | Leyenda y notas de alcance | Combina los componentes actuales con módulos, productores de avisos e integraciones pendientes. |

Los colores acompañan etiquetas textuales; no sustituyen la notación UML. Las cajas conservan
sus tipos: clase, componente, paquete, nodo, artefacto, actor o caso de uso. Las multiplicidades,
composiciones, dependencias y realizaciones mantienen significado UML.

La estética se centraliza en `_estilo.iuml`: fondo blanco, tipografía DejaVu Sans, cabeceras
azul oscuro, colores suaves, notas ámbar y líneas sin sombras. No hay includes remotos.
Se usan SVG para poder ampliar los diagramas detallados sin perder texto.

- Una asociación actor–caso de uso no representa orden de ejecución.
- `<<include>>` representa un comportamiento obligatorio y reutilizado; `<<extend>>`, una
  extensión condicional del caso base. No describen simplemente que una operación ocurre después.
- Recepción antes de devolución, confirmación antes de despacho y empleado antes de actualizar
  expediente son precondiciones. Las vistas de actividad/secuencia muestran el orden temporal.
- Las clases actuales muestran atributos Java `Long`/`Instant`; las clases futuras son conceptuales.
  `AsignacionRol` e `IdentificadorUsuario` son vistas de tablas SQL, no nuevas entidades Java.

## Convenciones UML aplicadas

| Vista | Notación | Regla de lectura |
|---|---|---|
| Casos de uso | Actor fuera del rectángulo del sistema; asociación continua sin flecha | Representa un objetivo observable del actor. Filtros, validación de permisos y avisos internos se expresan como parámetros, reglas o notas cuando no son objetivos autónomos. |
| Casos de uso | Dependencia discontinua `<<include>>` del caso base al incluido | El comportamiento incluido es obligatorio. `<<extend>>` apunta del caso condicional al caso base y declara la condición. Un requisito previo o un paso posterior no justifica `<<extend>>`. |
| Clases | Atributos `-` privados y operaciones `+` públicas; asociación continua con multiplicidad en cada extremo; rombo de composición solo para partes con ciclo de vida ligado al todo | Las flechas en asociaciones indican navegabilidad cuando se conoce; dependencia discontinua indica uso sin propiedad estructural. Las tablas `<<SQL>>` omiten visibilidad de Java. Las clases objetivo no se confunden con entidades JPA existentes. |
| Componentes y paquetes | Dependencia discontinua del consumidor al proveedor; realización discontinua con triángulo hueco hacia la interfaz implementada | Muestra dependencia estructural, no orden temporal de llamadas. El orden de Flyway queda en una nota; las llamadas y transacciones se detallan en secuencias y actividades. |
| Secuencia | Mensaje continuo para solicitud, discontinuo para respuesta; fragmentos `alt` y `group` | La transacción se agrupa como unidad lógica. `BEGIN`, `COMMIT` y `ROLLBACK` no se dibujan como mensajes del servicio a PostgreSQL cuando los gestiona Spring. |
| Actividad | Nodo inicial/final, decisiones con guardas y particiones por responsable | Describe flujo temporal y estados alternativos; no sustituye las dependencias estáticas entre módulos. |
| Despliegue | Nodos y entornos de ejecución contienen artefactos; línea sin flecha entre nodos | La línea es una ruta de comunicación y la etiqueta indica protocolo. Que un artefacto esté dentro de un nodo significa despliegue, no llamada entre archivos. |

`<<implementado>>`, `<<objetivo>>` y `<<SQL>>` son estereotipos de estado propios de esta documentación, no estereotipos estándar que impliquen comportamiento UML adicional. Cada vista conserva además una leyenda textual para no depender del color.

## Inventario

| Carpeta | Cantidad | Contenido |
|---|---:|---|
| `arquitectura/` | 8 | Contexto, componentes, dependencias, despliegue objetivo y secuencias. |
| `casos-uso/` | 9 | Alcance por actor/módulo; seguridad y categorías distinguen implementación del resto pendiente. |
| `clases/` | 7 | `00` es el mapa de componentes del dominio; `01`–`06` son vistas de clases. |
| `despliegue/` | 3 | Ejecución actual, MVP objetivo y objetivo escalable. |
| `implementacion/` | 5 | Organización real, arquitectura objetivo, plantilla futura y reutilización. |
| `procesos/` | 10 | Actividades de negocio objetivo y flujo de seguridad implementado. |
| Total PlantUML | 42 | Los 40 originales conservados y dos vistas nuevas de ejecución real. |

Vistas recomendadas para conocer el estado actual:

1. `implementacion/05-paquetes-java-springboot.puml`: archivos, paquetes, migraciones y pruebas reales.
2. `arquitectura/03-paquetes-java-arquitectura-capas.puml`: dependencias actuales y módulos propuestos.
3. `arquitectura/08-secuencia-autenticacion.puml`: login, errores, Bearer, permisos y logout reales.
4. `procesos/07-proceso-seguridad.puml`: bloqueo, reinicio, expiración y revocación.
5. `despliegue/00-despliegue-actual.puml`: JVM local, Compose existente y pruebas desechables.
6. `clases/01-gestion-maestra-seguridad.puml`: entidades actuales y relaciones futuras con maestros/auditoría.
7. `clases/06-reporteria-auditoria-integracion.puml`: auditoría y bandeja APP actuales frente a reportería/Outbox objetivo.

`common.api` aporta `ApiError`, `GlobalApiExceptionHandler`, `PageRequests` y
`PageResponse<T>`; `security.api` conserva
`AuthExceptionHandler` con mayor prioridad y el mismo record Java `ApiError`.
El esquema OpenAPI `AuthError` conserva su nombre como alias. `common.api` declara
`AuthorizationDenialRecorder`; `AuditTrail` lo implementa, de modo que el manejador
común no importa `audit` y la consulta de auditoría puede usar paginación común
sin formar un ciclo de paquetes.
La validación común tiene pruebas MVC aisladas y se ha verificado junto a auth,
categorías, auditoría y notificaciones. La paginación común tiene tres consumidores reales:
categorías, consulta de auditoría y bandeja APP; su reutilización en otro módulo de negocio
aún no está demostrada.
`maestros` añade criterios validados y `CategoriaProductoSpecifications` para
filtros combinables con JPA; la búsqueda de otros módulos sigue pendiente.

Las demás vistas conservan los procesos de compras, inventario, ventas, finanzas, RR. HH. y
reportería. Son objetivos funcionales, sin clases de producción que los implementen todavía.
El diseño aprobado de reportería se documenta en `docs/fase-2/reporteria/COMPONENTE_REPORTERIA.md`
y `docs/api/openapi-reporteria.yaml`; ambos son contratos propuestos, no endpoints activos.
Las vistas `casos-uso/reporteria.puml`, `procesos/08-proceso-reporteria.puml` y
`clases/06-reporteria-auditoria-integracion.puml` distinguen consultas de lectura,
permisos, datos no disponibles y exportación diferida.
La autenticación es Bearer opaco con estado en PostgreSQL; no se supone JWT. El escalado
futuro comparte `tokens_sesion`, requiere medición y no elimina el punto único de fallo de BD.

Nginx, HTTPS/TLS, SMTP, trabajos de respaldo/release y cliente React integrado son objetivo.
Hoy Compose solo configura PostgreSQL, con volumen persistente y publicación `5432:5432`;
Flyway se ejecuta al arrancar Spring Boot. Las pruebas usan otro PostgreSQL desechable.
La separación por puertos de los módulos futuros no describe el aislamiento actual de JPA:
`security.application` y `maestros.application` dependen directamente de repositorios
Spring Data y sus modelos actuales usan JPA.

## DBML y SQL

- Fuente canónica: `database/modelo.dbml`.
- Copia para diagramación: `docs/diagrams/modelodatos/modelo.dbml`, idéntica byte a byte.
- `database/modelo.dbdiagram` solo contiene configuración visual; no es un esquema alternativo.
- El modelo tiene 71 tablas y 141 relaciones: siete tablas de seguridad,
  `categorias_producto`, `eventos_auditoria` y `notificaciones` implementadas;
  61 tablas objetivo. Los grupos y notas
  explican la diferencia.
- Identificadores: `Long` en Java, `bigint` autogenerado en SQL. Se propagó esta convención
  documental a los módulos futuros para mantener compatibles sus referencias.
- `identificadores_usuario` y su trigger describen la unicidad conjunta de username/correo.
  `usuarios_roles` conserva asignador/fecha; `roles_permisos` conserva la PK compuesta.
- `fk_futura_usuarios_cliente` y `fk_futura_usuarios_empleado` son relaciones de diseño;
  no existen como FK en las migraciones actuales. Gestión Maestra tiene `categorias_producto`
  con API y cuatro catálogos SQL/JPA en V6, todavía sin servicio ni API.
- El DBML incluye notas sobre CHECK, normalización, triggers e índices parciales. La sintaxis
  lógica `increment` no sustituye la definición física `GENERATED BY DEFAULT AS IDENTITY`.

La autoridad ejecutable del esquema actual es `erp-backend/src/main/resources/db/migration/`:
V1 crea seguridad, V2 sus catálogos, V3 categorías, V4 auditoría, V5 bandeja APP y V6 cuatro catálogos maestros físicos. El DBML global
mezcla esquema actual y diseño futuro; **no debe exportarse y ejecutarse completo
como sustituto de Flyway**. Al iniciar el backend contra una base en V5,
Flyway aplicará V6 y V7. No habilita baseline/clean automáticos.

## Validar y generar las vistas

Se utilizaron Java 21, Graphviz, PlantUML 1.2024.8, Node 24.21.0 y `@dbml/core` 10.3.1.
Las herramientas auxiliares pueden residir en `/tmp`, sin cambiar dependencias del proyecto.
Node debe estar activo en el PATH; Graphviz proporciona el ejecutable `dot`.

Desde la raíz:

```bash
curl --fail --location --output /tmp/techsupply-plantuml.jar \
  https://repo.maven.apache.org/maven2/net/sourceforge/plantuml/plantuml/1.2024.8/plantuml-1.2024.8.jar

python3 docs/diagrams/renderizar.py \
  --jar /tmp/techsupply-plantuml.jar \
  --salida /tmp/techsupply-diagramas
```

El script comprueba la sintaxis de todas las fuentes y genera SVG separados por categoría.
Para PNG, añadir `--formato png` y usar otro directorio de salida. PlantUML utiliza el nombre
tras `@startuml` para el artefacto: por ejemplo, `TSS_Secuencia_Auth.svg`.
Los generados quedan fuera del repositorio. Los diagramas extensos se consultan ampliando el SVG.

Para analizar DBML y comparar las tablas actuales con V1/V3/V4/V5/V6, instalar el analizador oficial en un directorio
auxiliar, con acceso a npm cuando no esté disponible:

```bash
npm --cache /tmp/techsupply-diagram-npm-cache \
  --prefix /tmp/techsupply-diagram-tools install \
  --ignore-scripts --no-save @dbml/core@10.3.1

DBML_CORE_PATH=/tmp/techsupply-diagram-tools/node_modules/@dbml/core \
  node docs/diagrams/validar-modelo.cjs
```

El validador comprueba las dos copias, sintaxis DBML, columnas, tipos, nulabilidad, PK,
unicidad y las FK de las tablas actuales; además exige que las otras tablas estén marcadas
como objetivo. No ejecuta SQL ni conecta a PostgreSQL. Los CHECK, triggers e índices parciales
se documentan en notas; su ejecución sigue correspondiendo a las pruebas Flyway/PostgreSQL.

## Resultados de validación

- En la revisión del 27 de octubre se corrigieron relaciones de casos de uso, dependencias
  entre componentes y paquetes, representación de transacciones en secuencia y rutas de
  comunicación en despliegue. Las 42 fuentes se validaron y renderizaron a SVG con PlantUML
  1.2026.7; la validación sintáctica no sustituye la revisión semántica de cada regla de negocio.
- La revisión anterior validó V1/V3/V4/V5: 10 tablas físicas, 10 FK y 61 tablas objetivo.
- V6 añade cuatro tablas físicas y dos FK. `validar-modelo.cjs` comprobó ambas copias DBML: 71 tablas totales, 14 físicas y 12 FK físicas; el SQL se verificó aparte en PostgreSQL.
- En la parte 2 del sprint 1 se marcaron `Departamento`, `Proveedor`, `Almacen` y `Producto` como entidades JPA. En las partes 3 y 4 se añadieron servicios/API de los cuatro. Las 42 fuentes PlantUML se validaron y renderizaron con PlantUML 1.2026.7.
- Se conservan las vistas de procesos de los módulos pendientes y se distingue
  `CategoriaProducto` y los cuatro catálogos V6 con API.
- V5 y las pruebas de integración ejecutaron SQL solo en PostgreSQL desechable de
  Testcontainers. No se accedió a bases compartidas. El código permanece sin commit.

Los reportes de autenticación anteriores siguen documentados en
`docs/fase-2/autenticacion/COMPONENTE_AUTENTICACION.md`; no se presentan como pruebas nuevas
por haber actualizado diagramas. Productores reales de notificaciones, auditoría de módulos
futuros, política de retención, CORS/frontend, carga y adopción de bases existentes siguen pendientes.

- Revisión posterior a la parte 5: el proceso y la secuencia usan recepción `PENDIENTE` y clave de idempotencia al registrar. `validar-modelo.cjs` compara además las seis tablas parciales de V7 con el DBML, manteniendo explícitos los campos futuros. La comparación no verifica `CHECK`, triggers ni índices parciales; Flyway y las pruebas PostgreSQL siguen siendo la autoridad ejecutable.

- Parte 6: la vista de Compras e Inventario es mixta; orden, detalle, estados y cálculo están implementados, mientras recepción, inventario y el servicio/API de compras siguen como diseño. La vista de paquetes muestra las entidades y repositorios de orden junto a V7.
- Parte 7: la vista de paquetes incorpora el controlador y servicio de órdenes, el límite de catálogo y las pruebas HTTP y de contrato; la API de recepción sigue pendiente.

- Parte 8: las vistas de clases y paquetes distinguen existencias y entradas de inventario implementadas de la confirmación de recepción aún pendiente.

- Parte 9: las vistas de clases y paquetes muestran el registro y consulta de recepción; la confirmación sigue como objetivo.

- Parte 10: la secuencia de recepción refleja la confirmación implementada, incluidos el bloqueo, la entrada de inventario, el rollback y el reintento; el proceso conserva requisiciones y facturas como objetivo.
