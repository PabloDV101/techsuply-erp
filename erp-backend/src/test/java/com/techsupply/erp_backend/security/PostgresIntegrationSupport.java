package com.techsupply.erp_backend.security;

import java.util.UUID;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Only a disposable container is ever supplied to integration tests. Docker failure is a test failure. */
public abstract class PostgresIntegrationSupport {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
        .withDatabaseName("auth_test")
        .withUsername("auth_test")
        .withPassword(UUID.randomUUID().toString())
        .withReuse(false);

    /** Test-only chart and current-day period for operational posting integration scenarios. */
    protected void configureInventoryAccounting(JdbcTemplate db, long actorId) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(java.util.Locale.ROOT);
        long inventory = account(db, suffix + "I", "Inventario", "ACTIVO", "DEUDORA");
        long received = account(db, suffix + "R", "Mercancía recibida no facturada", "PASIVO", "ACREEDORA");
        long cogs = account(db, suffix + "C", "Costo de ventas", "COSTO", "DEUDORA");
        long gain = account(db, suffix + "G", "Ganancia por ajuste", "INGRESO", "ACREEDORA");
        long loss = account(db, suffix + "L", "Pérdida por ajuste", "GASTO", "DEUDORA");
        db.update("delete from configuracion_cuentas_inventario");
        Object[][] mappings = {
            {"INVENTARIO", inventory}, {"MERCANCIA_RECIBIDA_NO_FACTURADA", received},
            {"COSTO_DE_VENTAS", cogs}, {"AJUSTE_INVENTARIO_POSITIVO", gain}, {"AJUSTE_INVENTARIO_NEGATIVO", loss}
        };
        for (Object[] mapping : mappings) db.update("insert into configuracion_cuentas_inventario" +
            "(concepto,cuenta_contable_id,actualizado_por_usuario_id,actualizado_en) values (?,?,?,now())",
            mapping[0], mapping[1], actorId);
        LocalDate date = LocalDate.now(ZoneId.of("America/Mexico_City"));
        db.update("insert into periodos_contables(fecha_inicio,fecha_fin,estado) select ?,?,'ABIERTO' " +
            "where not exists(select 1 from periodos_contables where fecha_inicio<=? and fecha_fin>=?)",
            java.sql.Date.valueOf(date), java.sql.Date.valueOf(date), java.sql.Date.valueOf(date), java.sql.Date.valueOf(date));
    }

    private static long account(JdbcTemplate db, String code, String name, String type, String nature) {
        return db.queryForObject("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?,?,?,?) returning id",
            Long.class, "T" + code, name, type, nature);
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.jpa.open-in-view", () -> "false");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.baseline-on-migrate", () -> "false");
        registry.add("spring.flyway.clean-disabled", () -> "true");
        registry.add("app.security.bootstrap-admin.enabled", () -> "false");
    }
}
