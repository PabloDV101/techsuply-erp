package com.techsupply.erp_backend.security;

import com.techsupply.erp_backend.security.infrastructure.security.OpaqueTokenService;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AuthIntegrationIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired OpaqueTokenService tokens;
    @Autowired Flyway flyway;

    record Account(long id, String username, String password, long roleId) {}

    private Account account(String... authorities) {
        String key = UUID.randomUUID().toString();
        String password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class,
            "T_" + key.substring(0, 20).toUpperCase(Locale.ROOT), "Test");
        for (String code : authorities) {
            db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, code);
        }
        long id = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",
            Long.class, key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", id, role);
        return new Account(id, key, password, role);
    }

    private ResultActions login(String identifier, String password) throws Exception {
        return http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador", identifier, "password", password))));
    }

    private String login(Account account) throws Exception {
        String body = login(account.username(), account.password()).andExpect(status().isOk())
            .andExpect(jsonPath("tokenType").value("Bearer"))
            .andExpect(jsonPath("usuario.username").value(account.username()))
            .andExpect(jsonPath("usuario.passwordHash").doesNotExist()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }

    private ResultActions me(String token) throws Exception {
        return http.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token));
    }

    private int failures(Account a) {
        return db.queryForObject("select intentos_fallidos from usuarios where id=?", Integer.class, a.id());
    }

    @Test void flywayAndRealPersistenceMatchJpaSchema() throws Exception {
        assertThat(flyway.info().applied()).hasSize(4);
        var a = account("SEGURIDAD_USUARIOS_ADMIN");
        String token = login(a);
        assertThat(db.queryForObject("select password_hash from usuarios where id=?", String.class, a.id()))
            .startsWith("$2").isNotEqualTo(a.password());
        assertThat(db.queryForObject("select creado_en is not null from usuarios where id=?", Boolean.class, a.id())).isTrue();
        assertThat(db.queryForObject("select count(*) from usuarios_roles ur join roles_permisos rp on rp.rol_id=ur.rol_id "
            + "join permisos p on p.id=rp.permiso_id where ur.usuario_id=?", Long.class, a.id())).isEqualTo(1);
        assertThat(db.queryForObject("select token_hash from tokens_sesion where usuario_id=?", String.class, a.id()))
            .isEqualTo(tokens.calcularHash(token)).isNotEqualTo(token);
        me(token).andExpect(status().isOk()).andExpect(jsonPath("permisos[0]").value("SEGURIDAD_USUARIOS_ADMIN"));
    }

    @Test void wrongPasswordCommitsFailureDespite401() throws Exception {
        var a = account();
        login(a.username(), "incorrect").andExpect(status().isUnauthorized()).andExpect(jsonPath("code").value("UNAUTHORIZED"));
        assertThat(failures(a)).isEqualTo(1);
    }

    @Test void unknownUserAndInactiveUserHaveGenericFailure() throws Exception {
        var a = account();
        db.update("update usuarios set activo=false where id=?", a.id());
        String inactive = login(a.username(), a.password()).andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        String absent = login(UUID.randomUUID().toString(), a.password()).andExpect(status().isUnauthorized())
            .andReturn().getResponse().getContentAsString();
        assertThat(inactive).isEqualTo(absent);
        assertThat(db.queryForObject("select count(*) from tokens_sesion where usuario_id=?", Integer.class, a.id())).isZero();
    }

    @Test void fifthFailureLocksAndCorrectPasswordCannotBypassIt() throws Exception {
        var a = account();
        for (int i = 0; i < 5; i++) login(a.username(), "wrong").andExpect(status().isUnauthorized());
        login(a.username(), a.password()).andExpect(status().isUnauthorized());
        assertThat(failures(a)).isEqualTo(5);
        assertThat(db.queryForObject("select bloqueado_hasta > now() from usuarios where id=?", Boolean.class, a.id())).isTrue();
    }

    @Test void successfulLoginResetsFailuresAndExpiredLock() throws Exception {
        var a = account();
        db.update("update usuarios set intentos_fallidos=5,bloqueado_hasta=now()-interval '1 second' where id=?", a.id());
        login(a);
        assertThat(failures(a)).isZero();
        assertThat(db.queryForObject("select bloqueado_hasta is null from usuarios where id=?", Boolean.class, a.id())).isTrue();
    }

    @Test void expiredLockThenFailureStartsNewWindow() throws Exception {
        var a = account();
        db.update("update usuarios set intentos_fallidos=5,bloqueado_hasta=now()-interval '1 second' where id=?", a.id());
        login(a.username(), "wrong").andExpect(status().isUnauthorized());
        assertThat(failures(a)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "expired", "revoked"})
    void rejectsInvalidSession(String kind) throws Exception {
        var a = account();
        String token = kind.equals("missing") ? tokens.generarToken() : login(a);
        if (kind.equals("expired")) db.update("update tokens_sesion set emitido_en=now()-interval '3 hours', "
            + "vence_en=now()-interval '1 second' where token_hash=?", tokens.calcularHash(token));
        if (kind.equals("revoked")) db.update("update tokens_sesion set revocado_en=now() where token_hash=?", tokens.calcularHash(token));
        me(token).andExpect(status().isUnauthorized()).andExpect(header().string("WWW-Authenticate", "Bearer"))
            .andExpect(jsonPath("code").value("UNAUTHORIZED"));
    }

    @Test void logoutRevokesAndSubsequentRequestFails() throws Exception {
        var a = account(); String token = login(a);
        http.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
            .andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(db.queryForObject("select revocado_en is not null from tokens_sesion where token_hash=?",
            Boolean.class, tokens.calcularHash(token))).isTrue();
        me(token).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/logout").header("Authorization", "Bearer " + token))
            .andExpect(status().isUnauthorized());
    }

    @Test void missingAuthenticationAndContextIsolation() throws Exception {
        String token = login(account());
        me(token).andExpect(status().isOk());
        http.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/auth/logout")).andExpect(status().isUnauthorized());
        me("malformed").andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/users", "/roles", "/permissions"})
    void administrativePermissionAllowsAndMissingPermissionDenies(String path) throws Exception {
        String code = switch (path) {
            case "/users" -> "SEGURIDAD_USUARIOS_ADMIN";
            case "/roles" -> "SEGURIDAD_ROLES_ADMIN";
            default -> "SEGURIDAD_PERMISOS_ADMIN";
        };
        http.perform(get("/api/v1/admin" + path).header("Authorization", "Bearer " + login(account(code))))
            .andExpect(status().isOk());
        http.perform(get("/api/v1/admin" + path).header("Authorization", "Bearer " + login(account())))
            .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("FORBIDDEN"));
    }

    @Test void usersAdminCannotCreatePrivilegedAccountWithoutRolesPermission() throws Exception {
        String token = login(account("SEGURIDAD_USUARIOS_ADMIN"));
        http.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token)
            .contentType("application/json").content(json.writeValueAsString(Map.of("username", UUID.randomUUID().toString(),
                "correo", UUID.randomUUID() + "@example.test", "password", UUID.randomUUID().toString(),
                "roles", Set.of("ADMINISTRADOR")))))
            .andExpect(status().isForbidden());
    }

    @Test void actorCannotDelegatePermissionsTheyDoNotPossess() throws Exception {
        String token = login(account("SEGURIDAD_USUARIOS_ADMIN", "SEGURIDAD_ROLES_ADMIN"));
        http.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token)
            .contentType("application/json").content(json.writeValueAsString(Map.of("username", UUID.randomUUID().toString(),
                "correo", UUID.randomUUID() + "@example.test", "password", UUID.randomUUID().toString(),
                "roles", Set.of("ADMINISTRADOR")))))
            .andExpect(status().isForbidden());
    }

    @Test void authorizedCreationPersistsAssignmentAndDuplicateReturns409() throws Exception {
        var actor = account("SEGURIDAD_USUARIOS_ADMIN", "SEGURIDAD_ROLES_ADMIN", "SEGURIDAD_PERMISOS_ADMIN");
        String token = login(actor);
        String username = UUID.randomUUID().toString();
        String payload = json.writeValueAsString(Map.of("username", username, "correo", username+"@example.test",
            "password", UUID.randomUUID().toString(), "roles", Set.of("ADMINISTRADOR")));
        // Use a non-administrator role with the same permissions so the last-admin test stays independent.
        String role = db.queryForObject("select codigo from roles where id=?", String.class, actor.roleId());
        payload = payload.replace("ADMINISTRADOR", role);
        http.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token)
            .contentType("application/json").content(payload)).andExpect(status().isOk());
        assertThat(db.queryForObject("select ur.asignado_por_usuario_id from usuarios_roles ur join usuarios u "
            + "on u.id=ur.usuario_id where u.username=?", Long.class, username)).isEqualTo(actor.id());
        http.perform(post("/api/v1/admin/users").header("Authorization", "Bearer " + token)
            .contentType("application/json").content(payload)).andExpect(status().isConflict());
    }

    @Test void deactivationRevokesSession() throws Exception {
        var actor = account("SEGURIDAD_USUARIOS_ADMIN"); var target = account();
        String token = login(target);
        http.perform(patch("/api/v1/admin/users/"+target.id()+"/status")
            .header("Authorization", "Bearer " + login(actor)).contentType("application/json").content("{\"activo\":false}"))
            .andExpect(status().isOk());
        me(token).andExpect(status().isUnauthorized());
        assertThat(db.queryForObject("select revocado_en is not null from tokens_sesion where token_hash=?",
            Boolean.class, tokens.calcularHash(token))).isTrue();
    }

    @Test void parallelFailuresAreNotLost() throws Exception {
        var a = account();
        try (var pool = Executors.newFixedThreadPool(5)) {
            var ready = new CountDownLatch(5); var start = new CountDownLatch(1);
            var work = new ArrayList<Future<Integer>>();
            for (int i=0; i<5; i++) work.add(pool.submit(() -> {
                ready.countDown(); start.await();
                return login(a.username(), "wrong").andReturn().getResponse().getStatus();
            }));
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue(); start.countDown();
            for (var result : work) assertThat(result.get(30, TimeUnit.SECONDS)).isEqualTo(401);
        }
        assertThat(failures(a)).isEqualTo(5);
    }

    @Test void identifierNamespaceConstraintRejectsCrossFieldCollision() {
        var a = account();
        assertThatThrownBy(() -> db.update("insert into usuarios(username,correo,password_hash) values (?,?,?)",
            a.username()+"@example.test", UUID.randomUUID()+"@example.test", "fixture-hash"))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }

    @Test void malformedAndUnicodePasswordsReturnUniform400() throws Exception {
        login("tester", "é".repeat(37)).andExpect(status().isBadRequest())
            .andExpect(jsonPath("code").value("BAD_REQUEST"))
            .andExpect(jsonPath("message").value("Solicitud inválida"));
        http.perform(post("/api/v1/auth/login").contentType("application/json").content("{"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("message").value("Solicitud inválida"));
    }
    @Test void allAdministrativeMutationsDenyMissingPermissions() throws Exception {
        String token = login(account());
        var target = account();
        var paths = List.of(
            new String[]{"POST", "/users", "{\"username\":\"new\",\"correo\":\"new@example.test\",\"password\":\"test-only-password\",\"roles\":[\"ADMINISTRADOR\"]}"},
            new String[]{"PATCH", "/users/"+target.id()+"/status", "{\"activo\":false}"},
            new String[]{"PUT", "/users/"+target.id()+"/roles", "{\"roles\":[\"ADMINISTRADOR\"]}"},
            new String[]{"POST", "/roles", "{\"codigo\":\"TEST\",\"nombre\":\"Test\",\"permisos\":[]}"},
            new String[]{"PUT", "/roles/"+target.roleId()+"/permissions", "{\"permisos\":[]}"},
            new String[]{"POST", "/permissions", "{\"codigo\":\"TEST\",\"modulo\":\"TEST\",\"accion\":\"READ\"}"});
        for (var call : paths) {
            http.perform(request(HttpMethod.valueOf(call[0]), "/api/v1/admin"+call[1])
                .header("Authorization", "Bearer "+token).contentType("application/json").content(call[2]))
                .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("FORBIDDEN"));
            http.perform(request(HttpMethod.valueOf(call[0]), "/api/v1/admin"+call[1])
                .contentType("application/json").content(call[2])).andExpect(status().isUnauthorized());
        }
    }

    @Test void roleAndPermissionCreationAndReplacementUseRealJpaPersistence() throws Exception {
        var actor = account("SEGURIDAD_USUARIOS_ADMIN", "SEGURIDAD_ROLES_ADMIN", "SEGURIDAD_PERMISOS_ADMIN");
        String token = login(actor);
        String key = "P_"+UUID.randomUUID().toString().substring(0, 20).toUpperCase(Locale.ROOT);
        http.perform(post("/api/v1/admin/permissions").header("Authorization", "Bearer "+token)
            .contentType("application/json").content(json.writeValueAsString(Map.of(
                "codigo", key, "modulo", "TEST", "accion", key))))
            .andExpect(status().isOk()).andExpect(jsonPath("codigo").value(key));
        assertThat(db.queryForObject("select count(*) from permisos where codigo=?", Integer.class, key)).isEqualTo(1);
        String code = "R_"+UUID.randomUUID().toString().substring(0,20).toUpperCase(Locale.ROOT);
        String body = http.perform(post("/api/v1/admin/roles").header("Authorization", "Bearer "+token)
            .contentType("application/json").content(json.writeValueAsString(Map.of("codigo",code,"nombre","Test",
                "permisos",Set.of("SEGURIDAD_USUARIOS_ADMIN")))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long role = json.readTree(body).get("id").asLong();
        assertThat(db.queryForObject("select count(*) from roles_permisos where rol_id=?", Integer.class, role)).isEqualTo(1);
        http.perform(put("/api/v1/admin/roles/"+role+"/permissions").header("Authorization", "Bearer "+token)
            .contentType("application/json").content("{\"permisos\":[]}"))
            .andExpect(status().isOk()).andExpect(jsonPath("permisos").isEmpty());
        assertThat(db.queryForObject("select count(*) from roles_permisos where rol_id=?", Integer.class, role)).isZero();
    }

    @Test void replacingRolesRevokesExistingTokens() throws Exception {
        var actor = account("SEGURIDAD_ROLES_ADMIN"); var target = account();
        String token = login(target);
        String roleCode = db.queryForObject("select codigo from roles where id=?", String.class, target.roleId());
        http.perform(put("/api/v1/admin/users/"+target.id()+"/roles")
            .header("Authorization", "Bearer "+login(actor)).contentType("application/json")
            .content(json.writeValueAsString(Map.of("roles",Set.of(roleCode)))))
            .andExpect(status().isOk());
        me(token).andExpect(status().isUnauthorized());
    }

    @Test void simultaneousDeactivationsPreserveLastAdministratorAndRole() throws Exception {
        var first = account(); var second = account();
        for (var a : List.of(first, second)) {
            db.update("update usuarios_roles set rol_id=(select id from roles where codigo='ADMINISTRADOR') where usuario_id=?", a.id());
        }
        String token = login(account("SEGURIDAD_USUARIOS_ADMIN", "SEGURIDAD_ROLES_ADMIN"));
        try (var pool = Executors.newFixedThreadPool(2)) {
            var start = new CountDownLatch(1);
            var tasks = new ArrayList<Future<Integer>>();
            for (var a : List.of(first, second)) tasks.add(pool.submit(() -> {
                start.await();
                return http.perform(patch("/api/v1/admin/users/"+a.id()+"/status")
                    .header("Authorization", "Bearer "+token).contentType("application/json").content("{\"activo\":false}"))
                    .andReturn().getResponse().getStatus();
            }));
            start.countDown();
            assertThat(List.of(tasks.get(0).get(30,TimeUnit.SECONDS), tasks.get(1).get(30,TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200,409);
        }
        long remaining = db.queryForObject("select u.id from usuarios u join usuarios_roles ur on ur.usuario_id=u.id "
            + "join roles r on r.id=ur.rol_id where u.activo and r.codigo='ADMINISTRADOR'", Long.class);
        String emptyRole = db.queryForObject("select codigo from roles where id=?", String.class, first.roleId());
        long inactive = remaining == first.id() ? second.id() : first.id();
        http.perform(patch("/api/v1/admin/users/"+inactive+"/status").header("Authorization", "Bearer "+token)
            .contentType("application/json").content("{\"activo\":false}")).andExpect(status().isOk());
        http.perform(put("/api/v1/admin/users/"+inactive+"/roles").header("Authorization", "Bearer "+token)
            .contentType("application/json").content(json.writeValueAsString(Map.of("roles",Set.of(emptyRole)))))
            .andExpect(status().isOk());
        http.perform(put("/api/v1/admin/users/"+remaining+"/roles").header("Authorization", "Bearer "+token)
            .contentType("application/json").content(json.writeValueAsString(Map.of("roles",Set.of(emptyRole)))))
            .andExpect(status().isConflict()).andExpect(jsonPath("code").value("CONFLICT"));
    }

    @Test void apiRejectsCrossFieldIdentifierCollisionAndInvalidPassword() throws Exception {
        var target = account();
        String token = login(account("SEGURIDAD_USUARIOS_ADMIN","SEGURIDAD_ROLES_ADMIN","SEGURIDAD_PERMISOS_ADMIN"));
        var data = new HashMap<String,Object>();
        data.put("username", target.username()+"@example.test");
        data.put("correo",UUID.randomUUID()+"@example.test");
        data.put("password",UUID.randomUUID().toString()); data.put("roles",Set.of("ADMINISTRADOR"));
        http.perform(post("/api/v1/admin/users").header("Authorization", "Bearer "+token)
            .contentType("application/json").content(json.writeValueAsString(data))).andExpect(status().isConflict());
        data.put("password","é".repeat(37));
        http.perform(post("/api/v1/admin/users").header("Authorization", "Bearer "+token)
            .contentType("application/json").content(json.writeValueAsString(data))).andExpect(status().isBadRequest());
    }

}
