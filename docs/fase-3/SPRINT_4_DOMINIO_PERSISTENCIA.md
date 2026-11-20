# Sprint 4 — dominio y persistencia contable

**Alcance de parte 3:** mapeos JPA y reglas aisladas para las cuatro tablas de V14. Las partes posteriores añadieron servicios y API de cuentas, periodos, borradores, contabilización y reversión mediante V15. Los límites actuales y pendientes se registran en el [diseño](SPRINT_4_DISENO_CONTABILIDAD.md), las [migraciones](SPRINT_4_MIGRACIONES.md) y la [ruta](SPRINT_4_CONTABILIDAD_POR_PARTES.md).

## Componentes

- `CuentaContable`, `PeriodoContable`, `AsientoContable` y `PartidaAsiento` mapean las cuatro tablas existentes y sus columnas, identificadores, enums y referencias por identificador. Los repositorios Spring Data ofrecen solo guardar y consultar los datos que necesita esta fase; no son contratos HTTP.
- Los constructores verifican identificadores, textos y valores básicos. `PeriodoContable.contiene` utiliza límites inclusivos; la parte 5 añadió la transición de cierre, coordinada por servicio, sin reapertura. La exclusión de solapamientos y la protección de periodos cerrados siguen en V14.
- `ImporteContable` valida rango y escala acordados, incluidos ceros adicionales que superan dos decimales; no redondea. `Partida` requiere exactamente un lado positivo. `PartidaAsiento` usa esos valores y mapea `numeric` sin typmod, preservando las restricciones SQL.
- `BalanceAsiento` suma con `BigDecimal` sin acotar artificialmente el total a la capacidad de una línea y considera equilibrada una lista de al menos dos partidas cuando sus sumas son iguales. Es un cálculo aislado: no comprueba estado de cuenta, periodo, autorizaciones, origen ni ejecuta una transición.
- `JerarquiaCuentas.validarPadre` recorre una fotografía suministrada de ancestros, detecta ciclos y ancestros ausentes. La fotografía debe ser completa; no consulta la base ni protege contra modificaciones concurrentes. La parte de cuentas debe construirla y coordinar transacciones/bloqueos antes de permitir cambios.
- `AsientoContable` ahora administra transiciones manuales de borrador, contabilizado, cancelado y reversado. V15 valida mínimo y balance en la base y protege partidas; las fuentes operativas siguen bloqueadas. Los repositorios por sí solos no comprueban permisos, auditoría ni coordinación con Ventas.

La prueba de persistencia usa PostgreSQL/Testcontainers, Flyway y `ddl-auto=validate`; guarda datos sintéticos, limpia el contexto JPA y recarga cuentas, periodo, borrador y partidas, incluido el máximo permitido. No representa un flujo HTTP ni demuestra contabilización. Las reglas y restricciones financieras por completar figuran en la ruta y el diseño; no ejecutar estas entidades como un servicio operativo sin transacciones, autorización, auditoría y pruebas de las partes correspondientes.
