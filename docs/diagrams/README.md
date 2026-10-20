# Diagramas y modelo de datos — TechSupply Solutions ERP

Revisión documental: 19 de octubre de 2026. Estos archivos distinguen el estado implementado
de la arquitectura objetivo. La existencia de una clase, caso de uso o flujo dibujado no
acredita su implementación ni el cierre de una fase.

## Lectura y alcance

| Estado | Representación | Significado |
|---|---|---|
| Implementado | Azul y `<<implementado>>` | Código actual de seguridad y validación/errores HTTP comunes. |
| SQL actual | Verde y `<<SQL>>` | Tablas auxiliares reales sin clase JPA propia. |
| Objetivo | Gris, `<<objetivo>>` o leyenda de diseño objetivo | Funcionalidad prevista; no está implementada por aparecer aquí. |
| Opcional/futuro | Texto explícito, notas y estereotipos de futuro | Alternativas del diseño, sujetas a decisión posterior. |
| Vista mixta | Leyenda y notas de alcance | Combina seguridad actual con módulos/integraciones pendientes. |

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

## Inventario

| Carpeta | Cantidad | Contenido |
|---|---:|---|
| `arquitectura/` | 8 | Contexto, componentes, dependencias, despliegue objetivo y secuencias. |
| `casos-uso/` | 9 | Alcance por actor/módulo; seguridad distingue implementación y auditoría pendiente. |
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

`common.api` aporta `ApiError` y `GlobalApiExceptionHandler`; `security.api` conserva
`AuthExceptionHandler` con mayor prioridad y la misma forma JSON de tres campos.
La validación común tiene pruebas MVC aisladas y se ha verificado junto a auth;
su reutilización en un segundo módulo de negocio aún no está demostrada.

Las demás vistas conservan los procesos de compras, inventario, ventas, finanzas, RR. HH. y
reportería. Son objetivos funcionales, sin clases de producción que los implementen todavía.
La autenticación es Bearer opaco con estado en PostgreSQL; no se supone JWT. El escalado
futuro comparte `tokens_sesion`, requiere medición y no elimina el punto único de fallo de BD.

Nginx, HTTPS/TLS, SMTP, trabajos de respaldo/release y cliente React integrado son objetivo.
Hoy Compose solo configura PostgreSQL, con volumen persistente y publicación `5432:5432`;
Flyway se ejecuta al arrancar Spring Boot. Las pruebas usan otro PostgreSQL desechable.
La separación por puertos de los módulos futuros no describe el aislamiento actual de JPA:
`security.application` depende directamente de repositorios Spring Data y el dominio usa JPA.

## DBML y SQL

- Fuente canónica: `database/modelo.dbml`.
- Copia para diagramación: `docs/diagrams/modelodatos/modelo.dbml`, idéntica byte a byte.
- `database/modelo.dbdiagram` solo contiene configuración visual; no es un esquema alternativo.
- El modelo tiene 71 tablas y 141 relaciones: siete tablas de seguridad implementadas y
  64 tablas objetivo del ERP. Los grupos y notas explican la diferencia.
- Identificadores: `Long` en Java, `bigint` autogenerado en SQL. Se propagó esta convención
  documental a los módulos futuros para mantener compatibles sus referencias.
- `identificadores_usuario` y su trigger describen la unicidad conjunta de username/correo.
  `usuarios_roles` conserva asignador/fecha; `roles_permisos` conserva la PK compuesta.
- `fk_futura_usuarios_cliente` y `fk_futura_usuarios_empleado` son relaciones de diseño;
  no existen como FK en las migraciones actuales. Los catálogos maestros aún no existen.
- El DBML incluye notas sobre CHECK, normalización, triggers e índices parciales. La sintaxis
  lógica `increment` no sustituye la definición física `GENERATED BY DEFAULT AS IDENTITY`.

La autoridad ejecutable del esquema actual es `erp-backend/src/main/resources/db/migration/`:
V1 crea seguridad y V2 sus catálogos. El DBML global mezcla esquema actual y diseño futuro;
**no debe exportarse y ejecutarse completo como sustituto de Flyway**. Esta sincronización no
modifica migraciones, datos ni bases existentes. No habilita baseline/clean automáticos.

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

Para analizar DBML y comparar seguridad con V1, instalar el analizador oficial en un directorio
auxiliar, con acceso a npm cuando no esté disponible:

```bash
npm --cache /tmp/techsupply-diagram-npm-cache \
  --prefix /tmp/techsupply-diagram-tools install \
  --ignore-scripts --no-save @dbml/core@10.3.1

DBML_CORE_PATH=/tmp/techsupply-diagram-tools/node_modules/@dbml/core \
  node docs/diagrams/validar-modelo.cjs
```

El validador comprueba las dos copias, sintaxis DBML, columnas, tipos, nulabilidad, PK,
unicidad y siete FK de las tablas actuales; además exige que las otras tablas estén marcadas
como objetivo. No ejecuta SQL ni conecta a PostgreSQL. Los CHECK, triggers e índices parciales
se documentan en notas; su ejecución sigue correspondiendo a las pruebas Flyway/PostgreSQL.

## Resultado de esta revisión

- 42 fuentes PlantUML validadas y renderizadas a SVG sin errores de sintaxis tras
  incorporar `common.api` a las vistas de componentes y paquetes.
- Ambas copias DBML aceptadas por el analizador oficial y comparadas con V1 en esta revisión.
- Se conservaron todas las clases/enumeraciones y casos de uso originales; las actividades de
  negocio se mantuvieron. Seguridad se detalló según su implementación real.
- Revisión visual de una muestra de paquetes, seguridad, ventas y compras/inventario.
- Esta sincronización documental no modificó fuentes, configuración, dependencias ni migraciones del backend.
- No se ejecutó SQL ni se accedió a bases compartidas. El código permanece sin commit.

Los reportes de autenticación anteriores siguen documentados en
`docs/fase-2/autenticacion/COMPONENTE_AUTENTICACION.md`; no se presentan como pruebas nuevas
por haber actualizado diagramas. Auditoría, CORS/frontend, carga y adopción de bases existentes
siguen pendientes y están señalados como tales.
