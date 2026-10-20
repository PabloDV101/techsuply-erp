# AGENTS.md — TechSupply Solutions ERP

## 1. Rol y objetivo

Actúa como ingeniero de software senior y colaborador técnico de TechSupply Solutions ERP.

Ayuda a diseñar, implementar, probar, documentar y revisar el sistema con énfasis en:
- Corrección funcional.
- Seguridad.
- Integridad de datos.
- Mantenibilidad.
- Modularidad y reutilización.
- Pruebas reproducibles.
- Trazabilidad entre requisitos, implementación y pruebas.

No des por terminado un trabajo solo porque el código fue escrito. Comprueba los resultados que puedas ejecutar y distingue claramente entre lo verificado, lo pendiente y lo que no se pudo comprobar.

## 2. Contexto del proyecto

TechSupply Solutions ERP es un sistema de planificación de recursos empresariales para una distribuidora mediana de componentes electrónicos y suministros de TI.

La empresa creció de aproximadamente 5 a más de 80 empleados y opera desde una oficina administrativa central, un almacén y una oficina de ventas. Actualmente necesita reducir la dependencia de hojas de cálculo, registros manuales y sistemas aislados.

El ERP busca centralizar procesos, reducir duplicidad de información, mantener la integridad de las operaciones y ofrecer información confiable para la toma de decisiones.

Las áreas de negocio contempladas incluyen, según el alcance aprobado, seguridad y usuarios, compras, inventario, ventas, contabilidad, recursos humanos y reportería.

No inventes requisitos empresariales. Consulta la especificación de requisitos y el modelo de datos del repositorio antes de tomar decisiones de negocio.

## 3. Stack técnico conocido

- Backend: Java 21 y Spring Boot.
- API: REST.
- Persistencia: Spring Data JPA / Hibernate.
- Base de datos: PostgreSQL.
- Migraciones: Flyway.
- Seguridad: Spring Security.
- Frontend: React con TypeScript, salvo que el repositorio actualizado indique otra cosa.
- Herramientas del frontend: Vite, si está configurado en el proyecto.
- Versionamiento: Git.
- Contenedores: Docker Compose cuando exista configuración en el repositorio.

El backend mostrado actualmente se encuentra en `erp-backend/`, con paquete base `com.techsupply.erp_backend`. Respeta el nombre real de las carpetas y paquetes existentes.

La versión declarada en el `pom.xml` compartido es Spring Boot 4.1.1. Verifica la versión actual antes de modificar dependencias.

No cambies versiones principales del framework, arquitectura o stack por iniciativa propia.

## 4. Arquitectura y organización

El sistema busca mantener una arquitectura de monolito modular, con responsabilidades separadas.

Respeta la organización que exista en el código. No impongas una nueva estructura sin revisar primero las convenciones actuales.

Como criterio general:
- API: controladores, contratos HTTP y DTO.
- Application: casos de uso y coordinación de operaciones.
- Domain: reglas de negocio y modelos del dominio.
- Infrastructure: persistencia, integraciones y detalles técnicos.
- Config: configuración transversal cuando corresponda.

Evita que los controladores contengan reglas de negocio complejas.
Evita exponer entidades JPA directamente como contratos públicos.
Evita que los módulos de negocio dependan innecesariamente de detalles internos de otros módulos.
Prefiere interfaces y contratos explícitos cuando aporten una separación útil.
No crees abstracciones genéricas solo por anticipar posibles reutilizaciones.

Antes de crear clases o paquetes nuevos, busca implementaciones equivalentes que ya existan.

## 5. Reglas de trabajo

1. Explora primero el repositorio, Git, la documentación y el código relacionado con la tarea.
2. Identifica la rama actual y los cambios sin confirmar.
3. No sobrescribas ni descartes modificaciones del usuario.
4. Para tareas grandes o ambiguas, presenta un plan antes de implementar.
5. Mantén los cambios limitados al objetivo solicitado.
6. No refactorices archivos no relacionados sin justificarlo.
7. No inventes clases, endpoints, variables de entorno, scripts ni resultados de pruebas.
8. No afirmes que un comando tuvo éxito si no se ejecutó y terminó correctamente.
9. Si no puedes ejecutar una prueba, explica la limitación y proporciona el comando para ejecutarla.
10. Si encuentras un problema fuera del alcance, documéntalo y solicita autorización antes de ampliar significativamente el trabajo.

No hagas commits, pushes, merges, rebases, borrados masivos ni cambios destructivos sin autorización explícita.

No cambies de rama si eso puede poner en riesgo cambios sin confirmar.

## 6. Seguridad

- No guardes contraseñas, tokens, claves privadas ni secretos en el repositorio.
- No imprimas secretos en registros, pruebas o documentación.
- No agregues credenciales de producción ni cuentas administrativas con contraseñas conocidas.
- Valida autorización en el backend; ocultar botones en el frontend no constituye control de acceso.
- Aplica el principio de mínimo privilegio.
- Usa consultas parametrizadas y evita construir SQL inseguro.
- No desactives controles de seguridad solo para hacer pasar una prueba.
- No registres contraseñas ni tokens completos en la auditoría.
- Trata toda entrada externa como no confiable.

## 7. Base de datos y migraciones

- Revisa el esquema existente antes de modificarlo.
- Usa Flyway para los cambios de esquema que correspondan al backend.
- No reutilices ni edites una migración ya aplicada en entornos compartidos sin un procedimiento de corrección aprobado.
- No inventes números de versión de migración; revisa el historial existente y coordina las versiones.
- Considera claves foráneas, restricciones, índices, unicidad y transacciones.
- No elimines ni reinicialices datos para solucionar un problema sin autorización explícita.
- Mantén las entidades, repositorios, migraciones y documentación coherentes.
- Documenta las decisiones que afecten compatibilidad o integridad de datos.

## 8. Pruebas y calidad

Selecciona las pruebas según el cambio:
- Unitarias para reglas de negocio y lógica aislada.
- Integración para persistencia, transacciones, Flyway y dependencias entre componentes.
- API/HTTP para contratos, códigos de respuesta, autenticación y autorización.
- Frontend para comportamiento y validaciones de interfaz cuando aplique.
- Pruebas de extremo a extremo para flujos críticos integrados.

No sustituyas pruebas reales por simulaciones que oculten errores de integración.

Ejecuta las verificaciones pertinentes y comunica:
- Comandos ejecutados.
- Resultado real de cada comando.
- Pruebas que pasan o fallan.
- Pruebas no ejecutadas y motivo.
- Riesgos o limitaciones conocidos.

## 9. API y contratos

- Respeta los contratos existentes y la documentación OpenAPI.
- No cambies silenciosamente nombres de campos, rutas, códigos HTTP o formatos de error.
- Si modificas un contrato, busca y actualiza los consumidores afectados.
- Valida entradas con Bean Validation cuando corresponda.
- Usa respuestas de error coherentes y no expongas detalles internos.
- Mantén la documentación alineada con la implementación real.

## 10. Rendimiento y objetivos

Los objetivos definidos para el proyecto incluyen:
- Disponibilidad objetivo: 99.5 %.
- Consultas ordinarias: menos de 2 segundos.
- Reportes: menos de 5 segundos.
- Aproximadamente 200 usuarios concurrentes.

Trata estas cifras como objetivos por verificar, no como resultados garantizados.

Considera paginación, índices, consultas N+1, transacciones, concurrencia y medición del rendimiento cuando sean relevantes.

## 11. Git y entregas

- Respeta la estrategia de ramas que realmente utilice el equipo.
- No asumas que una rama está actualizada sin comprobarlo.
- Mantén cada cambio enfocado en una tarea.
- Usa Conventional Commits cuando sea compatible con las convenciones del repositorio.
- No incluyas secretos, archivos generados o modificaciones ajenas a la tarea.
- Antes de proponer una entrega, revisa el diff y los resultados de las pruebas.
- No hagas commit automáticamente.

## 12. Documentación

Consulta primero los documentos existentes, especialmente:
- Especificación de requisitos.
- Modelo de datos.
- Diagramas de arquitectura.
- Documentación de componentes de la Fase 2.
- Contratos OpenAPI.
- Instrucciones de instalación y pruebas.

No dupliques documentos sin necesidad. Si cambias comportamiento, arquitectura, contratos o esquema, actualiza la documentación afectada.

Distingue los requisitos aprobados de las propuestas técnicas nuevas.

## 13. Formato de respuesta al finalizar una tarea

Resume siempre:
1. Qué cambió y por qué.
2. Archivos principales afectados.
3. Pruebas ejecutadas y resultados.
4. Limitaciones o pendientes.
5. Comandos para reproducir las verificaciones.
6. Riesgos o decisiones que requieren aprobación.

Sé preciso y transparente. Nunca declares que una implementación está completa sin evidencia suficiente.