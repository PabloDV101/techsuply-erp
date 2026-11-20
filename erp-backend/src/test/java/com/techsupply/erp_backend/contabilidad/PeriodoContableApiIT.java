package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.sql.Connection;
import java.util.*;
import java.util.concurrent.*;
import javax.sql.DataSource;
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
class PeriodoContableApiIT extends PostgresIntegrationSupport {
    private static final String ROUTE = "/api/v1/accounting/periods";
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired DataSource source;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    private String token(String... authorities) throws Exception {
        String key = UUID.randomUUID().toString();
        String password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class,
            "PER_" + key.substring(0, 20).toUpperCase(Locale.ROOT), "Test");
        for (String code : authorities) db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, code);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class,
            key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        String response = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador", key, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("accessToken").asText();
    }
    private static String body(String start, String end) { return "{\"fechaInicio\":\"" + start + "\",\"fechaFin\":\"" + end + "\"}"; }
    private long create(String auth, String start, String end) throws Exception {
        String result = http.perform(post(ROUTE).header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(body(start, end)))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }
    private long user() {
        String key = UUID.randomUUID().toString();
        return db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?, 'hash') returning id", Long.class, key, key + "@example.test");
    }

    @Test void crearListarCerrarYReintentarConPermisosYAuditoria() throws Exception {
        String admin = token("CONTABILIDAD_PERIODOS_ADMIN", "CONTABILIDAD_PERIODOS_LEER", "CONTABILIDAD_PERIODOS_CERRAR");
        String reader = token("CONTABILIDAD_PERIODOS_LEER");
        String closeOnly = token("CONTABILIDAD_PERIODOS_CERRAR");
        http.perform(get(ROUTE)).andExpect(status().isUnauthorized());
        http.perform(post(ROUTE).header("Authorization", "Bearer " + reader).contentType("application/json")
            .content(body("2061-01-01", "2061-01-31"))).andExpect(status().isForbidden());
        long period = create(admin, "2061-01-01", "2061-01-31");
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad='PERIODO_CONTABLE' and entidad_id=? and accion='ACCOUNTING_PERIOD_CREATED'", Integer.class, period)).isEqualTo(1);
        http.perform(get(ROUTE + "/{id}", period).header("Authorization", "Bearer " + reader))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("ABIERTO"));
        http.perform(get(ROUTE).header("Authorization", "Bearer " + reader).param("size", "1").param("sort", "fechaInicio"))
            .andExpect(status().isOk()).andExpect(jsonPath("size").value(1));
        http.perform(get(ROUTE).header("Authorization", "Bearer " + reader).param("sort", "estado"))
            .andExpect(status().isBadRequest());
        http.perform(post(ROUTE + "/{id}/close", period).header("Authorization", "Bearer " + reader))
            .andExpect(status().isForbidden());
        String closed = http.perform(post(ROUTE + "/{id}/close", period).header("Authorization", "Bearer " + closeOnly))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("CERRADO"))
            .andExpect(jsonPath("cerradoPorUsuarioId").isNumber()).andExpect(jsonPath("cerradoEn").isNotEmpty())
            .andReturn().getResponse().getContentAsString();
        String repeated = http.perform(post(ROUTE + "/{id}/close", period).header("Authorization", "Bearer " + admin))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(repeated)).isEqualTo(json.readTree(closed));
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad='PERIODO_CONTABLE' and entidad_id=? and accion='ACCOUNTING_PERIOD_CLOSED'", Integer.class, period)).isEqualTo(1);
        assertThat(db.queryForObject("select usuario_id from eventos_auditoria where entidad='PERIODO_CONTABLE' and entidad_id=? and accion='ACCOUNTING_PERIOD_CLOSED'", Long.class, period))
            .isEqualTo(json.readTree(closed).get("cerradoPorUsuarioId").asLong());
        assertThat(db.queryForObject("select detalle->>'estadoAnterior' from eventos_auditoria where entidad='PERIODO_CONTABLE' and entidad_id=? and accion='ACCOUNTING_PERIOD_CLOSED'", String.class, period)).isEqualTo("ABIERTO");
        assertThat(db.queryForObject("select detalle->>'estadoNuevo' from eventos_auditoria where entidad='PERIODO_CONTABLE' and entidad_id=? and accion='ACCOUNTING_PERIOD_CLOSED'", String.class, period)).isEqualTo("CERRADO");
        http.perform(post(ROUTE + "/{id}/reopen", period).header("Authorization", "Bearer " + admin))
            .andExpect(status().isNotFound());
    }

    @Test void fechasYConflictosSeValidanSinInventarReglasDeCalendario() throws Exception {
        String auth = token("CONTABILIDAD_PERIODOS_ADMIN");
        create(auth, "1900-01-01", "1900-01-31");
        create(auth, "2062-01-01", "2062-01-31");
        create(auth, "2062-02-01", "2062-02-28");
        http.perform(post(ROUTE).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body("2062-01-31", "2062-02-01"))).andExpect(status().isConflict());
        http.perform(post(ROUTE).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content(body("2062-04-30", "2062-04-01"))).andExpect(status().isBadRequest());
        http.perform(post(ROUTE).header("Authorization", "Bearer " + auth).contentType("application/json")
            .content("{\"fechaInicio\":\"no-fecha\"}")).andExpect(status().isBadRequest());
    }

    @Test void borradoresImpidenCierreSinModificarDatos() throws Exception {
        String auth = token("CONTABILIDAD_PERIODOS_ADMIN", "CONTABILIDAD_PERIODOS_CERRAR");
        long period = create(auth, "2063-01-01", "2063-01-31"); long actor = user();
        db.update("insert into asientos_contables(folio,periodo_id,fecha_contable,descripcion,origen_modulo,origen_tipo,clave_idempotencia,creado_por_usuario_id,estado) values (?,?, '2063-01-01','Borrador','MANUAL','MANUAL',?,?,'BORRADOR')", "F"+UUID.randomUUID().toString().substring(0,8),period,UUID.randomUUID().toString(),actor);
        http.perform(post(ROUTE + "/{id}/close", period).header("Authorization", "Bearer " + auth)).andExpect(status().isConflict());
        assertThat(db.queryForObject("select estado from periodos_contables where id=?", String.class, period)).isEqualTo("ABIERTO");
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad_id=? and accion='ACCOUNTING_PERIOD_CLOSED'", Integer.class, period)).isZero();
    }

    @Test void cierresConcurrentesSonIdempotentes() throws Exception {
        String auth = token("CONTABILIDAD_PERIODOS_ADMIN", "CONTABILIDAD_PERIODOS_CERRAR");
        long period = create(auth, "2064-01-01", "2064-01-31"); CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> close = () -> { gate.await(); return http.perform(post(ROUTE + "/{id}/close", period).header("Authorization", "Bearer " + auth)).andReturn().getResponse().getStatus(); };
            var first = pool.submit(close); var second = pool.submit(close); gate.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(200, 200);
        }
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad_id=? and accion='ACCOUNTING_PERIOD_CLOSED'", Integer.class, period)).isEqualTo(1);
    }

    @Test void creacionConcurrenteDeFechasSolapadasSoloPermiteUna() throws Exception {
        String auth = token("CONTABILIDAD_PERIODOS_ADMIN"); CountDownLatch gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> create = () -> { gate.await(); return http.perform(post(ROUTE).header("Authorization", "Bearer " + auth)
                .contentType("application/json").content(body("2065-01-01", "2065-01-31"))).andReturn().getResponse().getStatus(); };
            var first = pool.submit(create); var second = pool.submit(create); gate.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS))).containsExactlyInAnyOrder(201, 409);
        }
    }

    @Test void bloqueoDePeriodoEsperaAlEscritorCooperanteYDetectaBorrador() throws Exception {
        String auth = token("CONTABILIDAD_PERIODOS_ADMIN", "CONTABILIDAD_PERIODOS_CERRAR");
        long period = create(auth, "2066-01-01", "2066-01-31"); long actor = user();
        try (Connection writer = source.getConnection(); var pool = Executors.newSingleThreadExecutor()) {
            writer.setAutoCommit(false);
            try (var lock = writer.prepareStatement("select id from periodos_contables where id=? for update")) {
                lock.setLong(1, period); lock.executeQuery().close();
            }
            CountDownLatch started = new CountDownLatch(1);
            var close = pool.submit(() -> { started.countDown(); return http.perform(post(ROUTE + "/{id}/close", period).header("Authorization", "Bearer " + auth)).andReturn().getResponse().getStatus(); });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> close.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            try (var insert = writer.prepareStatement("insert into asientos_contables(folio,periodo_id,fecha_contable,descripcion,origen_modulo,origen_tipo,clave_idempotencia,creado_por_usuario_id,estado) values (?,?, '2066-01-01','Simulado','MANUAL','MANUAL',?,?,'BORRADOR')")) {
                insert.setString(1, "F" + UUID.randomUUID().toString().substring(0,8)); insert.setLong(2, period);
                insert.setString(3, UUID.randomUUID().toString()); insert.setLong(4, actor); insert.executeUpdate();
            }
            writer.commit();
            assertThat(close.get(10, TimeUnit.SECONDS)).isEqualTo(409);
        }
        assertThat(db.queryForObject("select estado from periodos_contables where id=?", String.class,period)).isEqualTo("ABIERTO");
    }

    @Test void falloDeAuditoriaRevierteCierre() throws Exception {
        String auth = token("CONTABILIDAD_PERIODOS_ADMIN", "CONTABILIDAD_PERIODOS_CERRAR");
        long period = create(auth, "2067-01-01", "2067-01-31");
        db.execute("alter table eventos_auditoria add constraint ck_test_period_audit check (accion <> 'ACCOUNTING_PERIOD_CLOSED') not valid");
        try {
            http.perform(post(ROUTE + "/{id}/close", period).header("Authorization", "Bearer " + auth)).andExpect(status().isConflict());
            assertThat(db.queryForObject("select estado from periodos_contables where id=?", String.class,period)).isEqualTo("ABIERTO");
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_period_audit"); }
    }

    @Test void falloDeAuditoriaRevierteCreacion() throws Exception {
        String auth = token("CONTABILIDAD_PERIODOS_ADMIN");
        db.execute("alter table eventos_auditoria add constraint ck_test_period_create_audit check (accion <> 'ACCOUNTING_PERIOD_CREATED') not valid");
        try {
            http.perform(post(ROUTE).header("Authorization", "Bearer " + auth).contentType("application/json")
                .content(body("2068-01-01", "2068-01-31"))).andExpect(status().isConflict());
            assertThat(db.queryForObject("select count(*) from periodos_contables where fecha_inicio=date '2068-01-01'", Integer.class)).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_period_create_audit"); }
    }
}
