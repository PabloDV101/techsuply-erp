package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ClienteApiIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    private static String code() { return "C" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT); }
    private String token(String... permissions) throws Exception {
        String user = UUID.randomUUID().toString(), password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class, "R" + code(), "Test");
        for (String permission : permissions) {
            assertThat(db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, permission)).isEqualTo(1);
        }
        long userId = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, user, user + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", userId, role);
        String body = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador", user, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
    private String body(String code, String name, String limit, String fiscal) throws Exception {
        Map<String,Object> values = new HashMap<>();
        values.put("codigo", code); values.put("nombreRazonSocial", name);
        values.put("limiteCredito", new BigDecimal(limit)); values.put("plazoCreditoDias", 30);
        if (fiscal != null) values.put("identificadorFiscal", fiscal);
        return json.writeValueAsString(values);
    }
    private long create(String auth, String code, String limit) throws Exception {
        String result = http.perform(post("/api/v1/customers").header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(body(code, "Cliente", limit, null)))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }
    @Test void lifecycleFiltersAndPermissions() throws Exception {
        String admin = token("MAESTROS_CLIENTES_ADMIN"), read = token("MAESTROS_CLIENTES_LEER");
        String code = code(); long id = create(admin, " " + code.toLowerCase(Locale.ROOT) + " ", "0");
        http.perform(get("/api/v1/customers/{id}", id).header("Authorization", "Bearer " + read))
            .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(code)).andExpect(jsonPath("activo").value(true));
        http.perform(put("/api/v1/customers/{id}", id).header("Authorization", "Bearer " + admin).contentType("application/json")
            .content(body(code, "Actualizado", "0.00", null))).andExpect(status().isOk()).andExpect(jsonPath("nombreRazonSocial").value("Actualizado"));
        http.perform(patch("/api/v1/customers/{id}/status", id).header("Authorization", "Bearer " + admin).contentType("application/json")
            .content("{\"activo\":false}")).andExpect(status().isOk()).andExpect(jsonPath("activo").value(false));
        http.perform(patch("/api/v1/customers/{id}/status", id).header("Authorization", "Bearer " + admin).contentType("application/json")
            .content("{\"activo\":false}")).andExpect(status().isOk());
        http.perform(get("/api/v1/customers").header("Authorization", "Bearer " + read).param("activo", "false").param("size", "100"))
            .andExpect(status().isOk()).andExpect(jsonPath("content[?(@.id == " + id + ")].id").exists());
        http.perform(get("/api/v1/customers")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/customers").header("Authorization", "Bearer " + admin)).andExpect(status().isForbidden());
        http.perform(post("/api/v1/customers").header("Authorization", "Bearer " + read).contentType("application/json")
            .content(body(code(), "Cliente", "0", null))).andExpect(status().isForbidden());
        http.perform(get("/api/v1/customers/{id}", -1).header("Authorization", "Bearer " + read)).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/customers/{id}", Long.MAX_VALUE).header("Authorization", "Bearer " + read)).andExpect(status().isNotFound());
        http.perform(get("/api/v1/customers").header("Authorization", "Bearer " + read).param("size", "101")).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/customers").header("Authorization", "Bearer " + read).param("activo", "yes")).andExpect(status().isBadRequest());
        http.perform(delete("/api/v1/customers/{id}", id).header("Authorization", "Bearer " + admin)).andExpect(status().isMethodNotAllowed());
        assertThat(db.queryForObject("select count(*) from clientes where id=?", Integer.class, id)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad='CLIENTE' and entidad_id=?", Integer.class, id)).isEqualTo(3);
    }
    @Test void creditChangesRequireExtraPermissionAndAuditDoesNotStoreValues() throws Exception {
        String admin = token("MAESTROS_CLIENTES_ADMIN"), credit = token("MAESTROS_CLIENTES_ADMIN", "MAESTROS_CLIENTES_CREDITO_ADMIN");
        String code = code();
        http.perform(post("/api/v1/customers").header("Authorization", "Bearer " + admin).contentType("application/json")
            .content(body(code, "Nombre", "12345.67", null))).andExpect(status().isForbidden());
        long id = create(credit, code, "12345.67");
        http.perform(put("/api/v1/customers/{id}", id).header("Authorization", "Bearer " + admin).contentType("application/json")
            .content(body(code, "Otro", "12345.67", null))).andExpect(status().isOk());
        http.perform(put("/api/v1/customers/{id}", id).header("Authorization", "Bearer " + admin).contentType("application/json")
            .content(body(code, "No guardar", "0", null))).andExpect(status().isForbidden());
        assertThat(db.queryForObject("select limite_credito from clientes where id=?", BigDecimal.class, id)).isEqualByComparingTo("12345.67");
        http.perform(put("/api/v1/customers/{id}", id).header("Authorization", "Bearer " + credit).contentType("application/json")
            .content(body(code, "Otro", "1", null))).andExpect(status().isOk());
        String metadata = db.queryForObject("select detalle::text from eventos_auditoria where entidad='CLIENTE' and entidad_id=? and accion='CUSTOMER_UPDATED' order by id desc limit 1", String.class, id);
        assertThat(metadata).contains("LIMITE_CREDITO").doesNotContain("12345.67", "No guardar", code);
    }
    @Test void rejectsInvalidInputAndDuplicateIdentifiers() throws Exception {
        String admin = token("MAESTROS_CLIENTES_ADMIN"), fiscal = code(), first = code();
        http.perform(post("/api/v1/customers").header("Authorization", "Bearer " + admin).contentType("application/json")
            .content(body(first, "Nombre", "0", fiscal))).andExpect(status().isCreated());
        for (String body : new String[]{body(first.toLowerCase(Locale.ROOT), "Nombre", "0", null), body(code(), "Nombre", "0", fiscal)})
            http.perform(post("/api/v1/customers").header("Authorization", "Bearer " + admin).contentType("application/json").content(body)).andExpect(status().isConflict());
        for (String body : new String[]{body(" ", "Nombre", "0", null), body("ß".repeat(11), "Nombre", "0", null), body(code(), "Nombre", "-1", null), body(code(), "Nombre", "1.001", null), "{\"codigo\":\"X\",\"nombreRazonSocial\":\"A\"}"})
            http.perform(post("/api/v1/customers").header("Authorization", "Bearer " + admin).contentType("application/json").content(body)).andExpect(status().isBadRequest());
    }
    @Test void auditFailureRollsBackCustomerCreation() throws Exception {
        String admin = token("MAESTROS_CLIENTES_ADMIN"), code = code();
        db.execute("alter table eventos_auditoria add constraint ck_test_customer_audit_failure check (accion <> 'CUSTOMER_CREATED') not valid");
        try {
            http.perform(post("/api/v1/customers").header("Authorization", "Bearer " + admin).contentType("application/json")
                .content(body(code, "Cliente", "0", null))).andExpect(status().isConflict());
            assertThat(db.queryForObject("select count(*) from clientes where codigo=?", Integer.class, code)).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_customer_audit_failure"); }
    }
    @Test void concurrentUpdatesCannotBypassCreditPermission() throws Exception {
        String admin = token("MAESTROS_CLIENTES_ADMIN"), credit = token("MAESTROS_CLIENTES_ADMIN", "MAESTROS_CLIENTES_CREDITO_ADMIN");
        String code = code(); long id = create(admin, code, "0");
        String privilegedBody = body(code, "Credito", "10", null), normalBody = body(code, "Normal", "0", null);
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Integer> privileged = () -> { start.await(); return http.perform(put("/api/v1/customers/{id}", id).header("Authorization", "Bearer " + credit)
                .contentType("application/json").content(privilegedBody)).andReturn().getResponse().getStatus(); };
            Callable<Integer> normal = () -> { start.await(); return http.perform(put("/api/v1/customers/{id}", id).header("Authorization", "Bearer " + admin)
                .contentType("application/json").content(normalBody)).andReturn().getResponse().getStatus(); };
            var first = executor.submit(privileged); var second = executor.submit(normal); start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(20, TimeUnit.SECONDS)).isIn(200, 403);
            assertThat(db.queryForObject("select limite_credito from clientes where id=?", BigDecimal.class, id)).isEqualByComparingTo("10");
        } finally { executor.shutdownNow(); }
    }
    @Test void concurrentDuplicateCreationHasSingleWinner() throws Exception {
        String admin = token("MAESTROS_CLIENTES_ADMIN"), code = code(), payload = body(code, "Cliente", "0", null);
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Integer> call = () -> { start.await(); return http.perform(post("/api/v1/customers").header("Authorization", "Bearer " + admin)
                .contentType("application/json").content(payload)).andReturn().getResponse().getStatus(); };
            var first = executor.submit(call); var second = executor.submit(call); start.countDown();
            assertThat(java.util.List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
            assertThat(db.queryForObject("select count(*) from clientes where codigo=?", Integer.class, code)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
}
