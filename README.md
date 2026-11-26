# TechSupply Solutions ERP

ERP de TechSupply Solutions. El backend está en `erp-backend/` y utiliza Java 21, Spring Boot, PostgreSQL y migraciones Flyway. El frontend se desarrolla por separado en `frontend/`.

Los sprints 1–5 entregan Gestión Maestra, Compras, Inventario, Ventas, Contabilidad y Reportería dentro de los límites documentados. El sprint 7 conecta las pantallas React con esos contratos y añade seguridad administrativa, auditoría de solo lectura y bandeja personal de notificaciones. La evidencia de integración, pruebas y límites está en el [cierre del sprint 7](docs/fase-3/SPRINT_7_CIERRE.md), su [ruta por partes](docs/fase-3/SPRINT_7_RUTA_INTEGRACION_FRONTEND.md), [bitácora](docs/fase-3/SPRINT_7_INTEGRACION_TESTING_POR_PARTES.md) y [matriz de trazabilidad](docs/fase-3/SPRINT_7_MATRIZ_TRAZABILIDAD_PRUEBAS.md). Nómina, despliegue productivo y CORS siguen fuera de la entrega.

## Ejecutar y verificar el backend

Se requiere Java 21 y acceso a Docker para las pruebas de integración. Desde la raíz:

```bash
cd erp-backend
./mvnw verify
```

El ciclo `verify` incluye pruebas unitarias y de integración con un PostgreSQL desechable de Testcontainers. Si las dependencias y la imagen ya están disponibles localmente, puede ejecutarse `./mvnw -o verify`. Los resultados quedan en `erp-backend/target/surefire-reports/` y `erp-backend/target/failsafe-reports/`.

Para medir cobertura de código con JaCoCo, desde `erp-backend/` ejecutar la suite completa con el perfil opcional:

```bash
MAVEN_OPTS='-XX:+UseSerialGC -Xms128m -Xmx768m' ./mvnw -Pcoverage \
  -Dcoverage.jvm.args='-XX:+UseSerialGC -Xms128m -Xmx768m' verify
```

Se necesita acceso a Maven Central la primera vez para descargar el plugin; después se puede añadir `-o`. Los informes HTML y XML se generan en `target/site/jacoco-unit/`, `target/site/jacoco-integration/` y `target/site/jacoco-combined/`. El combinado une ambas suites; no equivale a cobertura exclusivamente unitaria ni a corrección funcional. No usar `-DskipTests`, selección parcial de pruebas ni cambiar el número de forks para una medición comparable: podrían quedar datos de ejecuciones previas. Comprobar que `verify` terminó correctamente y que ambos archivos `.exec` se regeneraron. En el perfil, usar `coverage.jvm.args` para argumentos de JVM; `-DargLine` puede interferir con el agente. `target/` es generado y no se versiona.


Para iniciar las aplicaciones localmente, seguir la [guía de ejecución y demostración](docs/fase-3/SPRINT_7_GUIA_EJECUCION_Y_DEMO.md) y las [instrucciones de configuración y bootstrap](docs/fase-2/autenticacion/COMPONENTE_AUTENTICACION.md). Configurar credenciales fuera de Git; el `.env` usado por Compose no se carga automáticamente en Spring. Flyway ejecuta migraciones sobre la base configurada: revisar su compatibilidad antes de conectarla. Compose no inicializa usuarios del ERP.

## Documentación

- [Guía de integración para frontend](docs/api/guia-sprint-1.md) y [contratos OpenAPI](docs/api/README.md).
- [Módulos y decisiones del sprint 1](docs/fase-3/SPRINT_1_MODULOS.md), [políticas de negocio](docs/fase-3/SPRINT_1_POLITICAS.md) y [migraciones](docs/fase-3/SPRINT_1_MIGRACIONES.md).
- [Requisitos SRS](docs/SRS_TechSupply_Solutions.md), [modelo de datos](database/modelo.dbml) y [diagramas UML](docs/diagrams/README.md). El DBML incluye partes del modelo objetivo que todavía no existen en SQL.
- [Sprint 7 — integración y testing por partes](docs/fase-3/SPRINT_7_INTEGRACION_TESTING_POR_PARTES.md).
- [Ruta de integración del frontend](docs/fase-3/SPRINT_7_RUTA_INTEGRACION_FRONTEND.md), incluidos arranque local y prueba de sesión.
- [Guía de ejecución y demostración del frontend integrado](docs/fase-3/SPRINT_7_GUIA_EJECUCION_Y_DEMO.md).
- [Patrones de diseño y programación funcional](docs/PATRONES_Y_PROGRAMACION_FUNCIONAL.md).

Los objetivos de rendimiento y disponibilidad requieren medición; la cobertura puede medirse con el perfil descrito y debe interpretarse junto con los resultados y límites de las pruebas.
