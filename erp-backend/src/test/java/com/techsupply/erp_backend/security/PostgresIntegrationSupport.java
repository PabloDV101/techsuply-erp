package com.techsupply.erp_backend.security;

import java.util.UUID;
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
