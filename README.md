# TechSupply Solutions ERP

ERP de TechSupply Solutions. El backend está en `erp-backend/` y utiliza Java 21, Spring Boot, PostgreSQL y migraciones Flyway. El frontend se desarrolla por separado en `frontend/`.

El primer sprint de la Fase 3 entrega catálogos maestros, órdenes de compra, registro y confirmación de recepciones y consulta de existencias. Requisiciones, facturas, devoluciones y los demás módulos aún tienen trabajo pendiente; consultar la [ruta de Fase 3](docs/FASE_3_RUTA_BACKEND.md) y el [registro de ejecución](docs/fase-3/SPRINT_1_EJECUCION_POR_PARTES.md) y el [cierre técnico](docs/fase-3/SPRINT_1_CIERRE.md).

## Ejecutar y verificar el backend

Se requiere Java 21 y acceso a Docker para las pruebas de integración. Desde la raíz:

```bash
cd erp-backend
./mvnw verify
```

El ciclo `verify` incluye pruebas unitarias y de integración con un PostgreSQL desechable de Testcontainers. Si las dependencias y la imagen ya están disponibles localmente, puede ejecutarse `./mvnw -o verify`. Los resultados quedan en `erp-backend/target/surefire-reports/` y `erp-backend/target/failsafe-reports/`.

Para iniciar la aplicación contra una base local, seguir las [instrucciones de configuración y bootstrap](docs/fase-2/autenticacion/COMPONENTE_AUTENTICACION.md). Configurar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` fuera de Git; el `.env` usado por Compose no se carga automáticamente en Spring. Flyway ejecuta las migraciones sobre la base configurada: revisar su compatibilidad antes de conectarla. El archivo `deploy/docker-compose.yml` inicia PostgreSQL con variables de entorno definidas por el usuario; no inicializa automáticamente usuarios del ERP.

## Documentación

- [Guía de integración para frontend](docs/api/guia-sprint-1.md) y [contratos OpenAPI](docs/api/README.md).
- [Módulos y decisiones del sprint 1](docs/fase-3/SPRINT_1_MODULOS.md), [políticas de negocio](docs/fase-3/SPRINT_1_POLITICAS.md) y [migraciones](docs/fase-3/SPRINT_1_MIGRACIONES.md).
- [Requisitos SRS](docs/SRS_TechSupply_Solutions.md), [modelo de datos](database/modelo.dbml) y [diagramas UML](docs/diagrams/README.md). El DBML incluye partes del modelo objetivo que todavía no existen en SQL.
- [Patrones de diseño y programación funcional](docs/PATRONES_Y_PROGRAMACION_FUNCIONAL.md).

Los objetivos de rendimiento, disponibilidad y cobertura del SRS requieren medición; los resultados de pruebas funcionales no los garantizan.
