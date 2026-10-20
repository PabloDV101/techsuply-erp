package com.techsupply.erp_backend.audit;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuditIntegrationIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired AuditTrail audit;
    @Autowired PlatformTransactionManager transactionManager;

    private record Account(long id, String token) {}

    private Account account(String... permissions) throws Exception {
        String name = UUID.randomUUID().toString();
        String password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class,
            "A_" + name.substring(0, 20).toUpperCase(Locale.ROOT), "Test");
        for (String code : permissions) db.update(
            "insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, code);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",
            Long.class, name, name + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        String body = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador", name, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(user, json.readTree(body).get("accessToken").asText());
    }

    @Test void successfulWritesCarryActorAndOnlySafeMetadata() throws Exception {
        Account owner = account("MAESTROS_CATEGORIAS_ADMIN", "SEGURIDAD_AUDITORIA_LEER");
        String code = "AUD" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(Locale.ROOT);
        String created = http.perform(post("/api/v1/categories").header("Authorization", "Bearer " + owner.token())
            .contentType("application/json").content(json.writeValueAsString(
                Map.of("codigo", code, "nombre", "Auditable"))))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        http.perform(put("/api/v1/categories/{id}", id).header("Authorization", "Bearer " + owner.token())
            .contentType("application/json").content(json.writeValueAsString(
                Map.of("codigo", code, "nombre", "Actualizada"))))
            .andExpect(status().isOk());

        assertThat(db.queryForObject("select usuario_id from eventos_auditoria where accion='CATEGORY_CREATED' and entidad_id=?",
            Long.class, id)).isEqualTo(owner.id());
        String detail = db.queryForObject("select detalle::text from eventos_auditoria where accion='CATEGORY_UPDATED' and entidad_id=?",
            String.class, id);
        assertThat(detail).contains("NOMBRE").doesNotContain("Actualizada", owner.token());
        http.perform(get("/api/v1/audit/events").header("Authorization", "Bearer " + owner.token())
            .param("usuarioId", Long.toString(owner.id())).param("entidad", "CATEGORIA_PRODUCTO")
            .param("entidadId", Long.toString(id)).param("sort", "id"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
            .andExpect(jsonPath("$.content[0].accion").value("CATEGORY_UPDATED"));
    }

    @Test void deniedAccessAndLoginAreRecordedWithoutCredentials() throws Exception {
        Account limited = account();
        long before = db.queryForObject("select count(*) from eventos_auditoria where accion='AUTHORIZATION_DENIED'", Long.class);
        http.perform(get("/api/v1/audit/events").header("Authorization", "Bearer " + limited.token()))
            .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='AUTHORIZATION_DENIED'", Long.class))
            .isEqualTo(before + 1);
        http.perform(get("/api/v1/audit/events")).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content("{\"identificador\":\"unknown-test-user\",\"password\":\"wrong-secret\"}"))
            .andExpect(status().isUnauthorized());
        String detail = db.queryForObject("select detalle::text from eventos_auditoria where accion='LOGIN_DENIED' order by id desc limit 1",
            String.class);
        assertThat(detail).doesNotContain("wrong-secret", "unknown-test-user");
    }

    @Test void rolledBackChangeLeavesNoEventAndDatabaseForbidsMutation() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> audit.systemBootstrap(Long.MAX_VALUE));
        long id = db.queryForObject("select id from eventos_auditoria where accion='ADMIN_BOOTSTRAPPED' "
            + "and entidad_id=? order by id desc limit 1", Long.class, Long.MAX_VALUE);
        long before = db.queryForObject("select count(*) from eventos_auditoria", Long.class);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            audit.systemBootstrap(Long.MAX_VALUE - 1);
            throw new IllegalStateException("Rollback de prueba");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria", Long.class)).isEqualTo(before);
        assertThatThrownBy(() -> db.update("update eventos_auditoria set accion=? where id=?", AuditAction.LOGIN_DENIED.name(), id))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(() -> db.update("delete from eventos_auditoria where id=?", id))
            .isInstanceOf(org.springframework.dao.DataAccessException.class);
    }

    @Test void auditInsertFailureRollsBackBusinessWrite() {
        String code = "ROLL" + UUID.randomUUID().toString().substring(0, 10).toUpperCase(Locale.ROOT);
        assertThatThrownBy(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            db.update("insert into categorias_producto(codigo,nombre) values (?,?)", code, "Se debe revertir");
            audit.verifiedLogin(Long.MAX_VALUE, 1L); // Invalid actor FK forces an audit persistence failure.
        })).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(db.queryForObject("select count(*) from categorias_producto where codigo=?", Long.class, code)).isZero();
    }

    @Test void logoutIsAuditedOnlyForActualRevocation() throws Exception {
        Account owner = account("SEGURIDAD_AUDITORIA_LEER");
        long before = db.queryForObject("select count(*) from eventos_auditoria where accion='LOGOUT' and usuario_id=?",
            Long.class, owner.id());
        http.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + owner.token()))
            .andExpect(status().isNoContent());
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='LOGOUT' and usuario_id=?",
            Long.class, owner.id())).isEqualTo(before + 1);
        http.perform(get("/api/v1/audit/events").header("Authorization", "Bearer " + owner.token()))
            .andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + owner.token()))
            .andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='LOGOUT' and usuario_id=?",
            Long.class, owner.id())).isEqualTo(before + 1);
    }

    @Test void invalidSearchAndWriteMethodsAreRejected() throws Exception {
        assertThat(db.queryForObject("select count(*) from roles_permisos rp join roles r on r.id=rp.rol_id "
            + "join permisos p on p.id=rp.permiso_id where r.codigo='ADMINISTRADOR' "
            + "and p.codigo='SEGURIDAD_AUDITORIA_LEER'", Long.class)).isEqualTo(1L);
        Account reader = account("SEGURIDAD_AUDITORIA_LEER");
        for (String query : new String[] {"size=101", "sort=accion", "usuarioId=0", "resultado=OTHER",
                "desde=tomorrow", "modulo=SEGURIDAD%3BDELETE"}) {
            http.perform(get("/api/v1/audit/events?" + query).header("Authorization", "Bearer " + reader.token()))
                .andExpect(status().isBadRequest());
        }
        http.perform(post("/api/v1/audit/events").header("Authorization", "Bearer " + reader.token()))
            .andExpect(status().isMethodNotAllowed());
    }
}
