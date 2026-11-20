package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.*;
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
class CuentaContableApiIT extends PostgresIntegrationSupport {
    private static final String ROUTE = "/api/v1/accounting/accounts";
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    private String token(String... authorities) throws Exception {
        String key = UUID.randomUUID().toString();
        String password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class,
            "AC_" + key.substring(0, 20).toUpperCase(Locale.ROOT), "Test");
        for (String code : authorities) db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, code);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class,
            key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        var response = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador", key, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }
    private String code() { return "T" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT); }
    private String body(String code, String name, Long parent, boolean accepts) throws Exception {
        Map<String, Object> value = new HashMap<>();
        value.put("codigo", code); value.put("nombre", name); value.put("tipo", "ACTIVO");
        value.put("naturaleza", "DEUDORA"); value.put("cuentaPadreId", parent); value.put("aceptaMovimientos", accepts);
        return json.writeValueAsString(value);
    }
    private long create(String auth, String code, String name, Long parent, boolean accepts) throws Exception {
        var result = http.perform(post(ROUTE).header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(body(code, name, parent, accepts)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }

    @Test void crearConsultarEditarYDesactivarConPermisosYAuditoria() throws Exception {
        String admin = token("CONTABILIDAD_CUENTAS_ADMIN", "CONTABILIDAD_CUENTAS_LEER");
        String reader = token("CONTABILIDAD_CUENTAS_LEER");
        String key = code();
        http.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        http.perform(post(ROUTE).header("Authorization", "Bearer " + reader).contentType("application/json")
            .content(body(key, "Cuenta", null, true))).andExpect(status().isForbidden());
        long root = create(admin, " " + key.toLowerCase(Locale.ROOT) + " ", " Grupo ", null, false);
        long child = create(admin, code(), "Cuenta", root, true);
        http.perform(get(ROUTE + "/{id}", child).header("Authorization", "Bearer " + reader))
            .andExpect(status().isOk()).andExpect(jsonPath("nivel").value(2))
            .andExpect(jsonPath("cuentaPadreId").value(root));
        http.perform(get(ROUTE).header("Authorization", "Bearer " + reader).param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("size").value(1));
        http.perform(get(ROUTE).header("Authorization", "Bearer " + reader).param("size", "101"))
            .andExpect(status().isBadRequest());
        http.perform(get(ROUTE).header("Authorization", "Bearer " + reader).param("sort", "activa"))
            .andExpect(status().isBadRequest());
        http.perform(post(ROUTE).header("Authorization", "Bearer " + admin).contentType("application/json")
            .content("{\"codigo\":\"\"}")).andExpect(status().isBadRequest());
        http.perform(post(ROUTE).header("Authorization", "Bearer " + admin).contentType("application/json")
            .content(body(key.toLowerCase(Locale.ROOT), "Repetida", null, false))).andExpect(status().isConflict());
        http.perform(put(ROUTE + "/{id}", root).header("Authorization", "Bearer " + admin)
            .contentType("application/json").content(body(key, "Agrupadora", null, false)))
            .andExpect(status().isOk()).andExpect(jsonPath("nombre").value("Agrupadora"));
        http.perform(patch(ROUTE + "/{id}/status", child).header("Authorization", "Bearer " + admin)
            .contentType("application/json").content("{\"activa\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("activa").value(false));
        http.perform(get(ROUTE + "/{id}", child).header("Authorization", "Bearer " + reader)).andExpect(status().isOk());
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad='CUENTA_CONTABLE' and entidad_id=?", Long.class, root)).isEqualTo(2L);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad='CUENTA_CONTABLE' and entidad_id=?", Long.class, child)).isEqualTo(2L);
    }

    @Test void jerarquiaYPartidasProtegenHistoria() throws Exception {
        String auth = token("CONTABILIDAD_CUENTAS_ADMIN", "CONTABILIDAD_CUENTAS_LEER");
        String a = code(), b = code();
        long root = create(auth, a, "Grupo", null, false);
        long leaf = create(auth, b, "Hoja", root, true);
        http.perform(put(ROUTE + "/{id}", root).header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(body(a, "Grupo", null, true))).andExpect(status().isConflict());
        http.perform(put(ROUTE + "/{id}", root).header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(body(a, "Grupo", leaf, false))).andExpect(status().isConflict());
        http.perform(post(ROUTE).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body(code(), "Invalida", leaf, true))).andExpect(status().isConflict());
        http.perform(post(ROUTE).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body(code(), "Invalida", Long.MAX_VALUE, true))).andExpect(status().isNotFound());
        String key = UUID.randomUUID().toString();
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?, 'hash') returning id", Long.class, key, key + "@example.test");
        long period = db.queryForObject("insert into periodos_contables(fecha_inicio,fecha_fin,estado) values ('2051-01-01','2051-01-31','ABIERTO') returning id", Long.class);
        long entry = db.queryForObject("insert into asientos_contables(folio,periodo_id,fecha_contable,descripcion,origen_modulo,origen_tipo,clave_idempotencia,creado_por_usuario_id,estado) values (?,?,'2051-01-01','Test','MANUAL','MANUAL',?,?,'BORRADOR') returning id", Long.class, "E"+key.substring(0,8),period,"K"+key,user);
        db.update("insert into partidas_asiento(asiento_id,numero_linea,cuenta_contable_id,debe,haber) values (?,1,?,1,0)",entry,leaf);
        http.perform(put(ROUTE + "/{id}", leaf).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body(b, "Renombrada", root, true))).andExpect(status().isOk());
        http.perform(put(ROUTE + "/{id}", leaf).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body(code(), "Renombrada", root, true))).andExpect(status().isConflict());
        http.perform(put(ROUTE + "/{id}", leaf).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body(b, "Renombrada", root, true).replace("ACTIVO", "PASIVO"))).andExpect(status().isConflict());
        http.perform(put(ROUTE + "/{id}", leaf).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body(b, "Renombrada", root, true).replace("DEUDORA", "ACREEDORA"))).andExpect(status().isConflict());
        http.perform(put(ROUTE + "/{id}", leaf).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body(b, "Renombrada", null, true))).andExpect(status().isConflict());
        http.perform(put(ROUTE + "/{id}", leaf).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body(b, "Renombrada", root, false))).andExpect(status().isConflict());
        http.perform(patch(ROUTE + "/{id}/status", leaf).header("Authorization", "Bearer " + auth)
            .contentType("application/json").content("{\"activa\":false}")).andExpect(status().isConflict());
        assertThat(db.queryForObject("select count(*) from partidas_asiento where cuenta_contable_id=?", Integer.class,leaf)).isEqualTo(1);
        // V14 did not require uppercase; a pre-existing code must remain unchanged when renaming.
        db.update("update cuentas_contables set codigo=? where id=?", b.toLowerCase(Locale.ROOT), leaf);
        http.perform(put(ROUTE + "/{id}", leaf).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body(b, "Nombre heredado", root, true))).andExpect(status().isOk())
            .andExpect(jsonPath("codigo").value(b.toLowerCase(Locale.ROOT)));
    }

    @Test void altasConcurrentesConMismoCodigoNoSeDuplican() throws Exception {
        String auth = token("CONTABILIDAD_CUENTAS_ADMIN"); String codigo = code();
        CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> request = () -> { gate.await(); return http.perform(post(ROUTE).header("Authorization", "Bearer " + auth)
                .contentType("application/json").content(body(codigo, "Cuenta", null, true))).andReturn().getResponse().getStatus(); };
            var first = pool.submit(request); var second = pool.submit(request); gate.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
        assertThat(db.queryForObject("select count(*) from cuentas_contables where codigo=?", Integer.class, codigo)).isEqualTo(1);
    }

    @Test void modificacionesConcurrentesNoCreanCiclos() throws Exception {
        String auth = token("CONTABILIDAD_CUENTAS_ADMIN");
        String a = code(), b = code();
        long first = create(auth, a, "Primera", null, false);
        long second = create(auth, b, "Segunda", null, false);
        CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var one = pool.submit(() -> { gate.await(); return http.perform(put(ROUTE + "/{id}", first).header("Authorization", "Bearer " + auth)
                .contentType("application/json").content(body(a, "Primera", second, false))).andReturn().getResponse().getStatus(); });
            var two = pool.submit(() -> { gate.await(); return http.perform(put(ROUTE + "/{id}", second).header("Authorization", "Bearer " + auth)
                .contentType("application/json").content(body(b, "Segunda", first, false))).andReturn().getResponse().getStatus(); });
            gate.countDown();
            assertThat(List.of(one.get(20, TimeUnit.SECONDS), two.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 409);
        }
    }

    @Test void errorDeAuditoriaRevierteElAlta() throws Exception {
        String auth = token("CONTABILIDAD_CUENTAS_ADMIN"); String key = code();
        db.execute("alter table eventos_auditoria add constraint ck_test_account_audit check (accion <> 'ACCOUNTING_ACCOUNT_CREATED') not valid");
        try {
            http.perform(post(ROUTE).header("Authorization", "Bearer " + auth).contentType("application/json")
                .content(body(key, "No persistir", null, true))).andExpect(status().isConflict());
            assertThat(db.queryForObject("select count(*) from cuentas_contables where codigo=?", Integer.class,key)).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_account_audit"); }
    }
}
