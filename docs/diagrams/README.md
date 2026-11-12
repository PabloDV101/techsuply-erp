# Diagramas UML y modelo de datos

Estas vistas representan el diseño del ERP TechSupply Solutions, sus actores, módulos, procesos y despliegue. Cada vista se centra en una responsabilidad; la presencia de un elemento en un diagrama no sustituye la trazabilidad ni las pruebas del proyecto. El estado de los incrementos se consulta en la [ruta de la fase 3](../FASE_3_RUTA_BACKEND.md), los contratos HTTP y la documentación de cada módulo.

## Organización

| Carpeta | Contenido |
|---|---|
| `arquitectura/` | Contexto, componentes, paquetes, despliegue y secuencias. |
| `casos-uso/` | Actores y objetivos por área. Ventas cuenta con una vista general y otra de validación de pedidos. |
| `clases/` | Vista de módulos y modelos de dominio por área; Ventas separa pedidos, devoluciones y facturación. |
| `despliegue/` | Desarrollo y pruebas, despliegue de una instancia y despliegue con varias instancias. |
| `implementacion/` | Componentes, paquetes, plantilla modular y contratos compartidos. |
| `procesos/` | Actividades de negocio y procesos transversales. |
| `modelodatos/` | Copia del DBML y configuración visual del modelo relacional. |

Los nombres de archivo se conservan para no romper enlaces existentes. `_estilo.iuml` concentra tipografía, colores, espaciado y configuración de PlantUML; no requiere temas remotos.

## Convenciones UML

- **Casos de uso:** los actores están fuera del límite del sistema y sus asociaciones son líneas continuas sin flecha. `<<include>>` apunta del caso base al comportamiento obligatorio; `<<extend>>` apunta del comportamiento condicional al caso base. Los requisitos previos se expresan como notas, no como una secuencia de asociaciones.
- **Clases:** los atributos privados usan `-`, las operaciones públicas `+`, y las asociaciones expresan multiplicidades. La composición se reserva para partes cuyo ciclo de vida depende del agregado. Una cardinalidad `0..*` permite representar un borrador sin líneas; las condiciones de confirmación se indican como reglas.
- **Componentes y paquetes:** las dependencias discontinuas apuntan del consumidor al proveedor. La realización de una interfaz se dibuja con línea discontinua y triángulo hueco. Las interacciones entre módulos se expresan por contratos de aplicación.
- **Secuencia:** mensajes de solicitud y respuesta se diferencian por línea continua y discontinua. Los fragmentos `alt` representan alternativas; un grupo transaccional señala escrituras que se confirman o revierten juntas.
- **Actividad:** nodo inicial/final, decisiones con guardas y particiones por responsable representan orden de ejecución y alternativas.
- **Despliegue:** nodos y entornos contienen artefactos; las líneas entre nodos representan rutas de comunicación con el protocolo correspondiente.

Los estereotipos de dominio como `<<Compras>>` o `<<Seguridad>>` identifican el contexto de una referencia; `<<include>>` y `<<extend>>` conservan su significado UML. El estilo visual no codifica progreso de implementación.

## Modelo relacional

La fuente canónica del DBML es [`database/modelo.dbml`](../../database/modelo.dbml); [`modelodatos/modelo.dbml`](modelodatos/modelo.dbml) debe permanecer idéntica. El modelo combina esquema y diseño de datos, y sus notas tienen una función de trazabilidad independiente de los diagramas UML. Las migraciones Flyway son la autoridad ejecutable y el DBML completo no debe ejecutarse como sustituto de ellas.

Los SVG generados están organizados en el [índice de diagramas renderizados](../diagramas-renderizados/README.md). Después de modificar una fuente, vuelve a ejecutar el renderizador para actualizar sus imágenes.

## Validación y exportación

Con Java, Graphviz y PlantUML disponibles, generar SVG fuera del repositorio:

```bash
curl --fail --location --output /tmp/techsupply-plantuml.jar \
  https://repo.maven.apache.org/maven2/net/sourceforge/plantuml/plantuml/1.2024.8/plantuml-1.2024.8.jar
python3 docs/diagrams/renderizar.py \
  --jar /tmp/techsupply-plantuml.jar \
  --salida /tmp/techsupply-diagramas
```

El script comprueba la sintaxis de todas las fuentes `.puml` y genera un SVG por fuente. Para imágenes rasterizadas se puede añadir `--formato png` y escoger otra carpeta vacía. La validación sintáctica no reemplaza la revisión de las relaciones y reglas del negocio.

Para comprobar ambas copias y comparar columnas, tipos, claves y referencias documentadas con las migraciones compatibles:

```bash
npm --cache /tmp/techsupply-diagram-npm-cache \
  --prefix /tmp/techsupply-diagram-tools install \
  --ignore-scripts --no-save @dbml/core@10.3.1
DBML_CORE_PATH=/tmp/techsupply-diagram-tools/node_modules/@dbml/core \
  node docs/diagrams/validar-modelo.cjs
```

El validador DBML no sustituye las pruebas PostgreSQL de restricciones, triggers o transacciones.
