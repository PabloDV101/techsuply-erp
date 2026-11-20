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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AsientoContableApiIT extends PostgresIntegrationSupport {
    private static final String ROUTE = "/api/v1/accounting/entries";
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired DataSource source;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    private record Auth(String token, long userId) {}

    private Auth token(String... permissions) throws Exception {
        String key = UUID.randomUUID().toString(); String password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class,
            "ASI_" + key.substring(0, 20).toUpperCase(Locale.ROOT), "Test");
        for (String code : permissions) db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, code);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class,
            key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        String response = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador", key, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Auth(json.readTree(response).get("accessToken").asText(), user);
    }

    private long periodo(String start, String end) {
        return db.queryForObject("insert into periodos_contables(fecha_inicio,fecha_fin,estado) values (?,?, 'ABIERTO') returning id",
            Long.class, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    private long cuenta(boolean activa, boolean aceptaMovimientos) {
        String key = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        return db.queryForObject("insert into cuentas_contables(codigo,nombre,tipo,naturaleza,nivel,acepta_movimientos,activa) values (?,?, 'ACTIVO','DEUDORA',1,?,?) returning id",
            Long.class, "T" + key, "Cuenta " + key, aceptaMovimientos, activa);
    }

    private String request(long period, long debitAccount, long creditAccount, String key) {
        return """
            {"periodoId":%d,"fechaContable":"2070-02-10","descripcion":"Ajuste manual",
             "claveIdempotencia":"%s","partidas":[
              {"cuentaId":%d,"debe":100.00,"haber":0,"descripcion":"Debe"},
              {"cuentaId":%d,"debe":0,"haber":100.00,"descripcion":"Haber"}]}
            """.formatted(period, key, debitAccount, creditAccount);
    }

    @Test void creaConsultaYPaginaBorradorConPartidasEIdempotencia() throws Exception {
        Auth auth = token("CONTABILIDAD_ASIENTOS_REGISTRAR", "CONTABILIDAD_ASIENTOS_LEER");
        long period = periodo("2072-02-01", "2072-02-28"); long debit = cuenta(true, true); long credit = cuenta(true, true);
        http.perform(post(ROUTE).contentType("application/json").content("{}"))
            .andExpect(status().isUnauthorized());
        String body = request(period, debit, credit, "asiento-api-" + UUID.randomUUID()).replace("2070-02-10", "2072-02-10");
        String created = http.perform(post(ROUTE).header("Authorization", "Bearer " + auth.token())
                .contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("estado").value("BORRADOR"))
            .andExpect(jsonPath("origenModulo").value("MANUAL"))
            .andExpect(jsonPath("creadoPorUsuarioId").value(auth.userId()))
            .andExpect(jsonPath("partidas.length()").value(2))
            .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        String replay = http.perform(post(ROUTE).header("Authorization", "Bearer " + auth.token())
                .contentType("application/json").content(body))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(replay)).isEqualTo(json.readTree(created));
        http.perform(get(ROUTE + "/{id}", id).header("Authorization", "Bearer " + auth.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("partidas[0].numeroLinea").value(1));
        http.perform(get(ROUTE).header("Authorization", "Bearer " + auth.token()).param("size", "1").param("sort", "id").param("direction", "desc"))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").isNumber())
            .andExpect(jsonPath("content[0].id").value(id));
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad='ASIENTO_CONTABLE' and entidad_id=? and accion='ACCOUNTING_ENTRY_DRAFT_CREATED'", Integer.class, id)).isEqualTo(1);
        assertThat(db.queryForObject("select estado from asientos_contables where id=?", String.class, id)).isEqualTo("BORRADOR");
    }

    @Test void validaPermisosIdempotenciaPeriodoCuentasYPrecision() throws Exception {
        Auth writer = token("CONTABILIDAD_ASIENTOS_REGISTRAR"); Auth reader = token("CONTABILIDAD_ASIENTOS_LEER");
        long period = periodo("2070-02-01", "2070-02-28"); long debit = cuenta(true, true); long credit = cuenta(true, true);
        String key = "asiento-invalid-" + UUID.randomUUID();
        String body = request(period, debit, credit, key);
        http.perform(post(ROUTE).header("Authorization", "Bearer " + reader.token()).contentType("application/json").content(body))
            .andExpect(status().isForbidden());
        String created = http.perform(post(ROUTE).header("Authorization", "Bearer " + writer.token()).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String altered = body.replace("Ajuste manual", "otro contenido");
        http.perform(post(ROUTE).header("Authorization", "Bearer " + writer.token()).contentType("application/json").content(altered))
            .andExpect(status().isConflict());
        long createdId = json.readTree(created).get("id").asLong();
        String closed = "update periodos_contables set estado='CERRADO',cerrado_por_usuario_id=?,cerrado_en=now() where id=?";
        db.update(closed, writer.userId(), period);
        String nextKey = "asiento-closed-" + UUID.randomUUID();
        http.perform(post(ROUTE).header("Authorization", "Bearer " + writer.token()).contentType("application/json")
                .content(request(period, debit, credit, nextKey))).andExpect(status().isConflict());
        assertThat(db.queryForObject("select count(*) from asientos_contables where clave_idempotencia=?", Integer.class, nextKey)).isZero();

        long openPeriod = periodo("2070-03-01", "2070-03-31"); long inactive = cuenta(false, true);
        String inactiveKey = "asiento-inactive-" + UUID.randomUUID();
        http.perform(post(ROUTE).header("Authorization", "Bearer " + writer.token()).contentType("application/json")
                .content(request(openPeriod, inactive, credit, inactiveKey))).andExpect(status().isConflict());
        String precise = request(openPeriod, debit, credit, "asiento-precision-" + UUID.randomUUID())
            .replace("2070-02-10", "2070-03-10").replace("100.00", "1.000");
        http.perform(post(ROUTE).header("Authorization", "Bearer " + writer.token()).contentType("application/json").content(precise))
            .andExpect(status().isBadRequest());
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad='ASIENTO_CONTABLE' and entidad_id=? and accion='ACCOUNTING_ENTRY_DRAFT_CREATED'", Integer.class, createdId)).isEqualTo(1);
        assertThat(created).contains("\"estado\":\"BORRADOR\"");
    }

    @Test void rechazaFechaFueraDePeriodoCuentaAgrupadoraAsientoDesequilibradoYReintentoConCambioDePartida() throws Exception {
        Auth auth = token("CONTABILIDAD_ASIENTOS_REGISTRAR"); long period = periodo("2071-04-01", "2071-04-30");
        long debit = cuenta(true, true), credit = cuenta(true, true), group = cuenta(true, false);
        String base = request(period, debit, credit, "asiento-rules-" + UUID.randomUUID()).replace("2070-02-10", "2071-04-10");
        String outside = base.replace("2071-04-10", "2071-05-01");
        http.perform(post(ROUTE).header("Authorization", "Bearer " + auth.token()).contentType("application/json").content(outside)).andExpect(status().isConflict());
        String grouped = request(period, group, credit, "asiento-group-" + UUID.randomUUID()).replace("2070-02-10", "2071-04-10");
        http.perform(post(ROUTE).header("Authorization", "Bearer " + auth.token()).contentType("application/json").content(grouped)).andExpect(status().isConflict());
        String unbalanced = base.replace("\"haber\":100.00", "\"haber\":99.00");
        http.perform(post(ROUTE).header("Authorization", "Bearer " + auth.token()).contentType("application/json").content(unbalanced))
            .andExpect(status().isCreated()).andExpect(jsonPath("estado").value("BORRADOR"));
    }

    @Test void creacionCooperaConBloqueoDePeriodoYElCierrePosteriorRechazaElBorrador() throws Exception {
        Auth writer = token("CONTABILIDAD_ASIENTOS_REGISTRAR"); Auth closer = token("CONTABILIDAD_PERIODOS_CERRAR");
        long period = periodo("2073-06-01", "2073-06-30"); long debit = cuenta(true, true); long credit = cuenta(true, true);
        String body = request(period, debit, credit, "asiento-race-" + UUID.randomUUID()).replace("2070-02-10", "2073-06-15");
        try (Connection lockOwner = source.getConnection(); var pool = Executors.newSingleThreadExecutor()) {
            lockOwner.setAutoCommit(false);
            try (var lock = lockOwner.prepareStatement("select id from periodos_contables where id=? for update")) {
                lock.setLong(1, period); lock.executeQuery().close();
            }
            CountDownLatch started = new CountDownLatch(1);
            var creation = pool.submit(() -> {
                started.countDown();
                return http.perform(post(ROUTE).header("Authorization", "Bearer " + writer.token())
                    .contentType("application/json").content(body)).andReturn().getResponse().getStatus();
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> creation.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            lockOwner.commit();
            assertThat(creation.get(10, TimeUnit.SECONDS)).isEqualTo(201);
        }
        http.perform(post("/api/v1/accounting/periods/{id}/close", period)
                .header("Authorization", "Bearer " + closer.token()))
            .andExpect(status().isConflict());
        assertThat(db.queryForObject("select estado from periodos_contables where id=?", String.class, period)).isEqualTo("ABIERTO");
    }

    @Test void falloDeAuditoriaRevierteEncabezadoYPartidas() throws Exception {
        Auth writer = token("CONTABILIDAD_ASIENTOS_REGISTRAR"); long period = periodo("2074-07-01", "2074-07-31");
        long debit = cuenta(true, true); long credit = cuenta(true, true); String key = "asiento-audit-" + UUID.randomUUID();
        db.execute("alter table eventos_auditoria add constraint ck_test_accounting_entry_audit check (accion <> 'ACCOUNTING_ENTRY_DRAFT_CREATED') not valid");
        try {
            http.perform(post(ROUTE).header("Authorization", "Bearer " + writer.token())
                    .contentType("application/json").content(request(period, debit, credit, key)))
                .andExpect(status().isConflict());
            assertThat(db.queryForObject("select count(*) from asientos_contables where clave_idempotencia=?", Integer.class, key)).isZero();
            assertThat(db.queryForObject("select count(*) from partidas_asiento p join asientos_contables a on a.id=p.asiento_id where a.clave_idempotencia=?", Integer.class, key)).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_accounting_entry_audit"); }
    }

    @Test void editaYCancelaBorradorConMotivoYConservaHistoria() throws Exception {
        Auth auth = token("CONTABILIDAD_ASIENTOS_REGISTRAR", "CONTABILIDAD_ASIENTOS_LEER");
        long period = periodo("2075-08-01", "2075-08-31");
        long debit = cuenta(true, true), credit = cuenta(true, true);
        String created = http.perform(post(ROUTE).header("Authorization", "Bearer " + auth.token())
                .contentType("application/json").content(request(period, debit, credit, "draft-edit-" + UUID.randomUUID()).replace("2070-02-10", "2075-08-10")))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        String edit = """
            {"fechaContable":"2075-08-11","descripcion":"Ajuste corregido","motivo":"Corrección de captura",
             "partidas":[{"cuentaId":%d,"debe":125.00,"haber":0},{"cuentaId":%d,"debe":0,"haber":125.00}]}
            """.formatted(debit, credit);
        http.perform(put(ROUTE + "/{id}/edit", id).header("Authorization", "Bearer " + auth.token())
                .contentType("application/json").content(edit))
            .andExpect(status().isOk()).andExpect(jsonPath("fechaContable").value("2075-08-11"))
            .andExpect(jsonPath("partidas[0].debe").value(125.0));
        http.perform(post(ROUTE + "/{id}/cancel", id).header("Authorization", "Bearer " + auth.token())
                .contentType("application/json").content("{\"motivo\":\"No se requiere el ajuste\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("CANCELADO"));
        assertThat(db.queryForObject("select count(*) from historial_asientos_contables where asiento_id=?", Integer.class, id)).isEqualTo(2);
        assertThat(db.queryForObject("select motivo from historial_asientos_contables where asiento_id=? and accion='BORRADOR_EDITADO'", String.class, id))
            .isEqualTo("Corrección de captura");
        http.perform(post(ROUTE + "/{id}/cancel", id).header("Authorization", "Bearer " + auth.token())
                .contentType("application/json").content("{\"motivo\":\"repetido\"}"))
            .andExpect(status().isConflict());
    }

    @Test void contabilizaSoloBalanceadoYRevierteEnPeriodoAbiertoConIdempotencia() throws Exception {
        Auth auth = token("CONTABILIDAD_ASIENTOS_REGISTRAR", "CONTABILIDAD_ASIENTOS_CONTABILIZAR",
            "CONTABILIDAD_ASIENTOS_REVERTIR", "CONTABILIDAD_ASIENTOS_LEER");
        long originalPeriod = periodo("2076-09-01", "2076-09-30");
        long reversalPeriod = periodo("2076-10-01", "2076-10-31");
        long debit = cuenta(true, true), credit = cuenta(true, true);
        String body = request(originalPeriod, debit, credit, "entry-post-" + UUID.randomUUID()).replace("2070-02-10", "2076-09-10");
        long entry = json.readTree(http.perform(post(ROUTE).header("Authorization", "Bearer " + auth.token())
            .contentType("application/json").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString()).get("id").asLong();
        http.perform(post(ROUTE + "/{id}/post", entry).header("Authorization", "Bearer " + auth.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("CONTABILIZADO"));
        http.perform(post(ROUTE + "/{id}/post", entry).header("Authorization", "Bearer " + auth.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("CONTABILIZADO"));
        String reverseBody = """
            {"fechaContable":"2076-10-05","motivo":"Corrección del asiento","claveIdempotencia":"reverse-%s"}
            """.formatted(UUID.randomUUID());
        String reversed = http.perform(post(ROUTE + "/{id}/reverse", entry).header("Authorization", "Bearer " + auth.token())
                .contentType("application/json").content(reverseBody))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("CONTABILIZADO"))
            .andExpect(jsonPath("contraasientoDeId").value(entry)).andExpect(jsonPath("fechaContable").value("2076-10-05"))
            .andReturn().getResponse().getContentAsString();
        long contraId = json.readTree(reversed).get("id").asLong();
        String replay = http.perform(post(ROUTE + "/{id}/reverse", entry).header("Authorization", "Bearer " + auth.token())
                .contentType("application/json").content(reverseBody)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(replay).get("id").asLong()).isEqualTo(contraId);
        assertThat(db.queryForObject("select estado from asientos_contables where id=?", String.class, entry)).isEqualTo("REVERSADO");
        assertThat(db.queryForObject("select count(*) from partidas_asiento where asiento_id=? and debe=0 and haber=100", Integer.class, contraId)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from historial_asientos_contables where clave_idempotencia is not null", Integer.class)).isGreaterThanOrEqualTo(1);
    }
}
