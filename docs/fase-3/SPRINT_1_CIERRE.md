# Cierre técnico — sprint 1

**Fecha:** 29 de octubre de 2026. **Rama revisada:** `maestra-compras`. Este cierre corresponde al incremento backend de Gestión Maestra, Compras y el inventario mínimo necesario para recepciones. La [secuencia de partes](SPRINT_1_EJECUCION_POR_PARTES.md) conserva la evidencia de cada incremento y la [guía de frontend](../api/guia-sprint-1.md) muestra el flujo de consumo.

## Evidencia de integración

- `cd erp-backend && ./mvnw -o verify`: `BUILD SUCCESS`, 50 pruebas Surefire y 70 Failsafe, sin fallos, errores ni omisiones. Las integraciones usan PostgreSQL 17 en Testcontainers, Flyway V1–V7 y validación JPA del esquema. Este resultado se tomó antes de la prueba adicional de campos HTTP y los ajustes documentales del cierre.
- Después de añadir el caso de propiedad adicional, `./mvnw -o -Dtest=MasterCatalogOpenApiContractTest,CategoriaOpenApiContractTest,AuditOpenApiContractTest,OrdenCompraOpenApiContractTest,RecepcionCompraOpenApiContractTest,InventarioOpenApiContractTest,MasterCatalogApiIT test`: 14 pruebas, sin fallos, errores ni omisiones. Este caso confirmó que el cuerpo de cambio de estado ignora propiedades extra; el contrato se ajustó a ese comportamiento.
- `ConfirmarRecepcionIT` cubre el recorrido HTTP de orden, aprobación, registro y confirmación, el caso CA-01 (20/18/2), saldo, reintento, confirmaciones concurrentes, permisos y rollback ante un fallo de valoración posterior a una escritura. Las pruebas de migración incluyen una base nueva y actualización de V6 a V7 con datos conservados. Esto no sustituye pruebas con un despliegue real ni con el frontend.
- `SWAGGER_PARSER_PATH=/tmp/techsupply-openapi-tools/node_modules/@apidevtools/swagger-parser node docs/api/validar-openapi.cjs`: seis documentos OpenAPI válidos, incluidas referencias; el contrato del sprint tiene 21 rutas y todas están marcadas implementadas. Las pruebas Java comparan rutas y campos principales con controladores y DTO. Se corrigieron descripciones YAML con comas en los contratos de sprint, auditoría, categorías y reportería. El contrato de reportería conserva `design-only`.
- `DBML_CORE_PATH=/tmp/techsupply-diagram-tools/node_modules/@dbml/core node docs/diagrams/validar-modelo.cjs`: 71 tablas y 141 relaciones; copias idénticas y comparación de 14 tablas V1/V3/V4/V5/V6 y las seis tablas parciales V7. La herramienta no verifica `CHECK`, triggers ni índices parciales: Flyway y PostgreSQL son la autoridad para esos elementos.
- `python3 docs/diagrams/renderizar.py --jar /home/pablodev/.local/bin/plantuml-1.2026.7.jar --salida /tmp/techsupply-sprint1-close-final-uml`: 42 fuentes validadas y renderizadas. Validación sintáctica no demuestra que cada proceso futuro esté implementado. Una comprobación de enlaces locales en los 20 Markdown de `docs` y el README no detectó enlaces faltantes.
- `git diff --check` no reportó errores; una comprobación separada de archivos nuevos no encontró espacios finales. No se han generado imágenes de diagramas dentro del repositorio.

## Alcance y límites

Las API de este contrato implementan departamentos, proveedores, almacenes, productos, órdenes, recepciones y lectura de existencias. `POST /purchase-receipts/{id}/confirm` aplica la entrada aceptada y la auditoría en una transacción. Los cálculos usan las [políticas del sprint](SPRINT_1_POLITICAS.md); su valoración no constituye una política contable o fiscal aprobada. Los catálogos conservan registros inactivos y no hay borrado físico desde estas API.

Requisiciones, facturas de proveedor, cuentas por pagar, devoluciones, ajustes, conteos y alertas siguen pendientes. El DBML conserva esas partes como modelo objetivo y V7 solo implementa un subconjunto de movimientos; no hay garantía de inmutabilidad del kardex contra SQL directo. No se midió cobertura de código ni pruebas de carga de 200 usuarios, disponibilidad o tiempos de respuesta de producción. Tampoco se integró el frontend ni se verificaron CORS, TLS o despliegue productivo.

## Revisión previa al commit

La búsqueda de patrones de claves privadas y tokens en los archivos cambiados o nuevos no detectó credenciales de ese tipo; la revisión específica de configuraciones sí encontró una contraseña predeterminada en el cambio local de `erp-backend/src/main/resources/application.yaml`. La búsqueda es limitada y no es una auditoría del historial Git. `.env` está ignorado y no aparece entre los archivos nuevos candidatos; no se encontraron claves privadas ni archivos generados candidatos.

`application.yaml` **ya está seguido por Git**: la regla nueva en `.gitignore` no impide que se incluya su modificación. No incluir ese cambio ni el cambio previo de `.gitignore` en el commit del sprint sin revisarlos. Si se desea preparar únicamente los demás cambios, revisar primero `git status` y usar desde la raíz:

```bash
git add -A -- . ':(exclude)erp-backend/src/main/resources/application.yaml' ':(exclude).gitignore'
git diff --cached --check
git diff --cached --stat
git status --short
```

Revisar el contenido preparado, especialmente archivos nuevos y documentación, antes de hacer el commit. Estos comandos preparan cambios; no se ejecutaron como parte de este cierre. La configuración local se conservó sin modificar.
