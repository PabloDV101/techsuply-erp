package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContabilidadIntegrationIT extends PostgresIntegrationSupport {
    private static final AtomicInteger PERIOD_SEQUENCE = new AtomicInteger();

    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    private String adminToken;
    private LocalDate firstStart;
    private LocalDate secondStart;
    private long debitAccount;
    private long creditAccount;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = token("CONTABILIDAD_ADMIN", "CONTABILIDAD_LEER");
        firstStart = LocalDate.of(2030, 1, 1).plusDays(PERIOD_SEQUENCE.incrementAndGet() * 100L);
        secondStart = firstStart.plusDays(31);
        debitAccount = createAccount("ACTIVO", "DEUDORA");
        creditAccount = createAccount("INGRESO", "ACREEDORA");
    }

    @Test
    void financialReportsOnlyIncludePostedEntriesFromSelectedPeriod() throws Exception {
        long firstPeriod = createPeriod(firstStart, firstStart.plusDays(30));
        long secondPeriod = createPeriod(secondStart, secondStart.plusDays(30));
        long posted = createJournal(firstStart.plusDays(1), "posted", "key-" + UUID.randomUUID(), "100", "100");
        http.perform(post("/api/v1/asientos-contables/{id}/contabilizar", posted)
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk());

        createJournal(firstStart.plusDays(2), "draft", null, "15", "15");
        long otherPeriod = createJournal(secondStart.plusDays(1), "other-period", null, "30", "30");
        http.perform(post("/api/v1/asientos-contables/{id}/contabilizar", otherPeriod)
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk());

        String trialBalance = http.perform(get("/api/v1/reportes/contabilidad/balance-prueba")
                .param("periodoId", Long.toString(firstPeriod))
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        JsonNode trial = json.readTree(trialBalance);
        assertThat(lineFor(trial, debitAccount).path("debit").decimalValue())
            .isEqualByComparingTo("100.00");
        assertThat(lineFor(trial, creditAccount).path("credit").decimalValue())
            .isEqualByComparingTo("100.00");
        assertThat(trial.path("total").decimalValue()).isEqualByComparingTo("0.00");

        String incomeStatement = http.perform(get("/api/v1/reportes/contabilidad/estado-resultados")
                .param("periodoId", Long.toString(firstPeriod))
                .header("Authorization", bearer(adminToken)))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(incomeStatement).path("totalsByType").path("INGRESO").decimalValue())
            .isEqualByComparingTo("100.00");
    }

    @Test
    void idempotencyRequiresAnEquivalentRequestAndRejectsZeroValueLines() throws Exception {
        createPeriod(firstStart, firstStart.plusDays(30));
        String key = "same-" + UUID.randomUUID();
        long journalId = createJournal(firstStart.plusDays(1), "same payload", key, "25", "25");
        String repeated = http.perform(post("/api/v1/asientos-contables")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content(journalBody(firstStart.plusDays(1), "same payload", key, "25", "25")))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(repeated).path("id").asLong()).isEqualTo(journalId);

        http.perform(post("/api/v1/asientos-contables")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content(journalBody(firstStart.plusDays(1), "same payload", key, "26", "26")))
            .andExpect(status().isConflict());

        String zeroLine = json.writeValueAsString(Map.of(
            "fechaContable", firstStart.plusDays(2).toString(),
            "descripcion", "Importe cero",
            "origenModulo", "MANUAL",
            "origenTipo", "TEST",
            "partidas", List.of(
                Map.of("cuentaId", debitAccount, "debe", BigDecimal.ZERO, "haber", BigDecimal.ZERO),
                Map.of("cuentaId", creditAccount, "debe", BigDecimal.ZERO, "haber", BigDecimal.ZERO)
            )
        ));
        http.perform(post("/api/v1/asientos-contables")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content(zeroLine))
            .andExpect(status().isBadRequest());
    }

    @Test
    void accountingReportRequiresReadPermissionAndPeriodsCannotOverlap() throws Exception {
        long periodId = createPeriod(firstStart, firstStart.plusDays(30));
        http.perform(get("/api/v1/reportes/contabilidad/balance-prueba")
                .param("periodoId", Long.toString(periodId)))
            .andExpect(status().isUnauthorized());

        String writeOnlyToken = token("CONTABILIDAD_ADMIN");
        http.perform(get("/api/v1/reportes/contabilidad/balance-prueba")
                .param("periodoId", Long.toString(periodId))
                .header("Authorization", bearer(writeOnlyToken)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("code").value("FORBIDDEN"));

        http.perform(post("/api/v1/periodos-contables")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content(periodBody(firstStart.plusDays(20), firstStart.plusDays(40))))
            .andExpect(status().isConflict());
    }

    private long createPeriod(LocalDate start, LocalDate end) throws Exception {
        String response = http.perform(post("/api/v1/periodos-contables")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content(periodBody(start, end)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("id").asLong();
    }

    private String periodBody(LocalDate start, LocalDate end) throws Exception {
        return json.writeValueAsString(Map.of("fechaInicio", start.toString(), "fechaFin", end.toString()));
    }

    private long createAccount(String type, String nature) throws Exception {
        String code = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String response = http.perform(post("/api/v1/cuentas-contables")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "codigo", code,
                    "nombre", "Cuenta " + code,
                    "tipo", type,
                    "naturaleza", nature,
                    "nivel", 1,
                    "aceptaMovimientos", true
                ))))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("id").asLong();
    }

    private long createJournal(LocalDate date, String description, String key, String debit, String credit)
            throws Exception {
        String response = http.perform(post("/api/v1/asientos-contables")
                .header("Authorization", bearer(adminToken))
                .contentType("application/json")
                .content(journalBody(date, description, key, debit, credit)))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("id").asLong();
    }

    private String journalBody(LocalDate date, String description, String key, String debit, String credit)
            throws Exception {
        Map<String, Object> request = new java.util.HashMap<>();
        request.put("fechaContable", date.toString());
        request.put("descripcion", description);
        request.put("origenModulo", "MANUAL");
        request.put("origenTipo", "TEST");
        request.put("claveIdempotencia", key);
        request.put("partidas", List.of(
            Map.of("cuentaId", debitAccount, "debe", new BigDecimal(debit), "haber", BigDecimal.ZERO),
            Map.of("cuentaId", creditAccount, "debe", BigDecimal.ZERO, "haber", new BigDecimal(credit))
        ));
        return json.writeValueAsString(request);
    }

    private JsonNode lineFor(JsonNode report, long accountId) {
        for (JsonNode line : report.path("lines")) {
            if (line.path("accountId").asLong() == accountId) {
                return line;
            }
        }
        throw new AssertionError("Report did not include account " + accountId);
    }

    private String token(String... authorities) throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "");
        String password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class,
            "T_" + key.substring(0, 20).toUpperCase(Locale.ROOT), "Test");
        for (String code : authorities) {
            db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",
                role, code);
        }
        db.update("insert into usuarios(username,correo,password_hash) values (?,?,?)",
            key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) "
                + "select u.id,? from usuarios u where u.username=?",
            role, key);
        String response = http.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("identificador", key, "password", password))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
