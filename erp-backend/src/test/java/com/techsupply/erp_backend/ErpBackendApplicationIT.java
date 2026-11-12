package com.techsupply.erp_backend;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ErpBackendApplicationIT extends PostgresIntegrationSupport {
    @Test void contextLoadsWithFlywayAndPostgres() {}
}
