# Sprint 3 — cierre de Ventas backend

**Estado:** incremento backend integrado y listo para revisión de commit en `fix/docs`. No equivale a la entrega completa del ERP ni a que toda la rúbrica esté satisfecha. No se creó commit.

## Alcance entregado

- Alta y consulta de clientes a través del módulo de Gestión Maestra; cotizaciones con vigencia, redondeo e idempotencia; conversión a pedido de contado; reserva; despachos parciales/totales y movimientos de salida; cancelación con liberación del saldo pendiente; factura comercial interna explícita por pedido totalmente despachado.
- Facturas con snapshot inmutable, permiso y auditoría, idempotencia y una factura por pedido. No se emite CFDI, no hay timbrado, PDF, cobro, cuenta por cobrar ni asiento contable. El vencimiento es la fecha de emisión para las ventas de contado de este incremento.
- Las migraciones nuevas V12 y V13 agregan metadatos/protección de cancelación y factura comercial. Las pruebas ejecutan PostgreSQL desde cero y migran hasta V13. No se afirma que V13 esté aplicada a una base compartida; desplegarla requiere comprobar el historial Flyway y el procedimiento del entorno.
- Se actualizaron DBML, contratos OpenAPI, trazabilidad SRS, UML, diagramas renderizados, guías y documentación por parte. La [ruta de ejecución](SPRINT_3_VENTAS_POR_PARTES.md) conserva las decisiones, anomalías y evidencia de cada etapa.
- La parte 13 extrajo el cálculo `HALF_UP` compartido entre Compras y Ventas y probó la sustitución del mapper paginado. No se añadió una estrategia ficticia sin reglas alternativas aprobadas.

## Verificación

En Java 21, el 12 de noviembre de 2026, `./mvnw -o -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify` pasó con **72 pruebas Surefire y 183 Failsafe, sin fallos**. Incluye Testcontainers/PostgreSQL y el flujo HTTP desde el alta de cliente hasta la factura, con comprobación del movimiento de kardex y de la existencia física.

También pasaron los validadores: ocho contratos OpenAPI, siete ejemplos válidos y tres casos negativos; DBML con 71 tablas y 143 relaciones y copias iguales; 50 fuentes PlantUML; enlaces locales; y `git diff --check`. El render completo se ejecutó en una carpeta temporal, pues esta parte no cambió los diagramas.

La medición JaCoCo de la parte 13 dio **12,78 % de líneas unitarias**, **89,50 % de integración** y **90,12 % combinado**. La meta unitaria de 70 % no se cumple; la cobertura de integración o combinada no la reemplaza.

La búsqueda local de patrones no encontró claves privadas, tokens comunes ni credenciales con los patrones revisados. Además, `detect-secrets` 1.5.0 revisó 490 rutas versionadas o nuevas y señaló dos falsos positivos, sin mostrar sus valores: el ejemplo de contraseña incorrecta de `docs/api/pruebas-manuales-fase-2.http` y el vector conocido de SHA-256 en `OpaqueTokenServiceTest`. Ambos se revisaron en contexto. El escaneo no analiza el historial Git y sus filtros pueden omitir binarios u otros formatos; repetirlo si se agregan archivos antes de preparar el commit.

Comando reproducible; el resultado se guarda fuera del repositorio y debe inspeccionarse sin imprimir los valores detectados:

```sh
python3 -m venv /tmp/techsuply-secrets-venv
/tmp/techsuply-secrets-venv/bin/pip install detect-secrets==1.5.0
git ls-files --cached --others --exclude-standard -z | xargs -0 /tmp/techsuply-secrets-venv/bin/detect-secrets scan --no-verify > /tmp/techsuply-sprint3-secrets.json
```

Los comandos para repetir Maven, OpenAPI, DBML y PlantUML están en [`docs/api/README.md`](../api/README.md) y [`docs/diagrams/README.md`](../diagrams/README.md). Para la regresión completa desde `erp-backend/`:

```sh
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -o \
  -DargLine='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

## Límites antes de considerar la entrega completa

- Falta aumentar y medir cobertura unitaria hasta la meta del proyecto; el cálculo común sí tiene cobertura unitaria de 100 % en su clase, lo cual no cambia el porcentaje global.
- No se probó `CalculoImportes` desde un proyecto independiente ni se acredita el objetivo de siete componentes con tres contextos reales cada uno. `PageResponse` permite cambiar el mapper, pero sigue ligado a Spring Data.
- No se integró el frontend React ni se verificaron navegador, CORS, red, TLS, despliegue o carga. Tampoco se midieron disponibilidad, latencia ni 200 usuarios concurrentes.
- Pagos, crédito operativo, cuentas por cobrar, Contabilidad, CFDI, devoluciones, reversas, portal, comisiones e informes siguen dependiendo de políticas y módulos posteriores.
- La creación del cliente de negocio está en `MAESTROS_CLIENTES_*`; un perfil de Ventas necesita también los permisos de Gestión Maestra que use el flujo.

## Git y recomendación de entrega

La rama revisada es `fix/docs`; el índice de Git estaba vacío y todos los cambios permanecen sin commit. Incluyen código, pruebas, migraciones y documentación acumulados durante las partes del sprint, además de los cambios UML/DBML acordados. `git diff --check` pasó. Tras revisar y preparar explícitamente este conjunto de cambios, considero razonable hacer el commit del incremento backend; las limitaciones anteriores deben permanecer visibles en la entrega.

No se prepararon archivos ni se ejecutaron commit, push, merge o cambio de rama. Antes de confirmar, volver a revisar `git status --short`, el diff preparado, los archivos nuevos y el escaneo de secretos sobre la selección final.
