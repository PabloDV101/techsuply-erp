package com.techsupply.erp_backend.contabilidad;

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
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ReportesContablesApiIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    private record Auth(String token, long userId) {}

    private Auth token(String... permissions) throws Exception {
        String key = UUID.randomUUID().toString(); String password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class,
            "REP_" + key.substring(0, 20).toUpperCase(Locale.ROOT), "Test");
        for (String code : permissions)
            db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, code);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",
            Long.class, key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        String response = http.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identificador", key, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Auth(json.readTree(response).get("accessToken").asText(), user);
    }

    private long period(String start, String end) {
        return db.queryForObject("insert into periodos_contables(fecha_inicio,fecha_fin,estado) values (?,?,'ABIERTO') returning id",
            Long.class, java.sql.Date.valueOf(start), java.sql.Date.valueOf(end));
    }

    private long account(String prefix) {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase(Locale.ROOT);
        return db.queryForObject("""
            insert into cuentas_contables(codigo,nombre,tipo,naturaleza,nivel,acepta_movimientos,activa)
            values (?,?,'ACTIVO','DEUDORA',1,true,true) returning id
            """, Long.class, prefix + suffix, "Cuenta " + suffix);
    }

    @Test void estadosFinancierosConcilianYExcluyenBorradores() throws Exception {
        Auth accountant=token("CONTABILIDAD_REPORTES_LEER","CONTABILIDAD_ASIENTOS_REGISTRAR","CONTABILIDAD_ASIENTOS_CONTABILIZAR","CONTABILIDAD_ASIENTOS_REVERTIR","CONTABILIDAD_FLUJO_CLASIFICAR");
        Auth reader=token("CONTABILIDAD_ASIENTOS_LEER");
        long period=period("1901-01-01","1901-01-31");
        http.perform(get("/api/v1/reports/finance/cash-flow").header("Authorization","Bearer "+accountant.token()).param("periodId",Long.toString(period)))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("NO_DISPONIBLE"));
        long funds=account("EF");
        String suffix=UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT);
        long revenue=db.queryForObject("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?,?,'INGRESO','ACREEDORA') returning id",Long.class,"IN"+suffix,"Ingresos");
        long expense=db.queryForObject("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?,?,'GASTO','DEUDORA') returning id",Long.class,"GA"+suffix,"Gastos");
        long revenueEntry=entry(accountant,period,"1901-01-10",funds,revenue,"100",suffix+"A",true);
        long expenseEntry=entry(accountant,period,"1901-01-11",expense,funds,"25",suffix+"B",true);
        http.perform(post("/api/v1/accounting/cash-flow-classifications/accounts").header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("cuentaId",funds,"clasificacion","EFECTIVO","desde","1901-01-01","hasta","1901-12-31")))).andExpect(status().isOk());
        http.perform(post("/api/v1/accounting/cash-flow-classifications/accounts").header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("cuentaId",funds,"clasificacion","INVERSION","desde","1901-06-01","hasta","1902-01-01")))).andExpect(status().isConflict());
        http.perform(post("/api/v1/accounting/cash-flow-classifications/accounts").header("Authorization","Bearer "+reader.token()).contentType("application/json").content(json.writeValueAsString(Map.of("cuentaId",funds,"clasificacion","EFECTIVO","desde","1902-01-01","hasta","1902-12-31")))).andExpect(status().isForbidden());
        long line=db.queryForObject("select id from partidas_asiento where asiento_id=? and cuenta_contable_id=?",Long.class,expenseEntry,expense);
        http.perform(post("/api/v1/accounting/cash-flow-classifications/lines").header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("partidaId",line,"clasificacion","OPERACION")))).andExpect(status().isOk());
        http.perform(post("/api/v1/accounting/cash-flow-classifications/lines").header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("partidaId",line,"clasificacion","INVERSION")))).andExpect(status().isConflict());
        long classification=db.queryForObject("select id from clasificaciones_flujo_partida where partida_asiento_id=? and anulada_en is null",Long.class,line);
        http.perform(post("/api/v1/accounting/cash-flow-classifications/lines/{id}/void",classification).header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("motivo","Corrección")))).andExpect(status().isOk());
        http.perform(post("/api/v1/accounting/cash-flow-classifications/lines").header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("partidaId",line,"clasificacion","OPERACION")))).andExpect(status().isOk());
        assertThat(db.queryForObject("select count(*) from clasificaciones_flujo_partida where partida_asiento_id=?",Integer.class,line)).isEqualTo(2);
        entry(accountant,period,"1901-01-12",funds,revenue,"50",suffix+"C",false);
        http.perform(get("/api/v1/reports/finance/income-statement").header("Authorization","Bearer "+accountant.token()).param("periodId",Long.toString(period)))
            .andExpect(status().isOk()).andExpect(jsonPath("rubros[0].importe").value(100)).andExpect(jsonPath("rubros[2].importe").value(25)).andExpect(jsonPath("rubros[3].importe").value(75));
        http.perform(get("/api/v1/reports/finance/balance-sheet").header("Authorization","Bearer "+accountant.token()).param("periodId",Long.toString(period)))
            .andExpect(status().isOk()).andExpect(jsonPath("rubros[5].importe").value(0));
        http.perform(get("/api/v1/reports/finance/cash-flow").header("Authorization","Bearer "+accountant.token()).param("periodId",Long.toString(period)))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("NO_DISPONIBLE"));
        for(long account: new long[]{revenue,expense})
            http.perform(post("/api/v1/accounting/cash-flow-classifications/accounts").header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("cuentaId",account,"clasificacion","RESULTADO","desde","1901-01-01","hasta","1901-12-31")))).andExpect(status().isOk());
        long revenueLine=db.queryForObject("select id from partidas_asiento where asiento_id=? and cuenta_contable_id=?",Long.class,revenueEntry,revenue);
        http.perform(post("/api/v1/accounting/cash-flow-classifications/lines").header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("partidaId",revenueLine,"clasificacion","OPERACION")))).andExpect(status().isOk());
        http.perform(get("/api/v1/reports/finance/cash-flow").header("Authorization","Bearer "+accountant.token()).param("periodId",Long.toString(period)))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("DISPONIBLE")).andExpect(jsonPath("rubros[2].importe").value(75)).andExpect(jsonPath("rubros[5].importe").value(75)).andExpect(jsonPath("rubros[7].importe").value(75));
        long equipment=account("EQ");
        long debt=db.queryForObject("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?,?,'PASIVO','ACREEDORA') returning id",Long.class,"PA"+suffix,"Deuda");
        for(var mapping:Map.of(equipment,"INVERSION",debt,"FINANCIACION").entrySet())
            http.perform(post("/api/v1/accounting/cash-flow-classifications/accounts").header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("cuentaId",mapping.getKey(),"clasificacion",mapping.getValue(),"desde","1901-01-01","hasta","1901-12-31")))).andExpect(status().isOk());
        long investment=entry(accountant,period,"1901-01-13",equipment,funds,"20",suffix+"INV",true);
        long loan=entry(accountant,period,"1901-01-14",funds,debt,"40",suffix+"FIN",true);
        long noncash=entry(accountant,period,"1901-01-15",equipment,debt,"15",suffix+"NC",true);
        for(var mapping:Map.of(investment,"INVERSION",loan,"FINANCIACION").entrySet()) {
            long part=db.queryForObject("select id from partidas_asiento where asiento_id=? and cuenta_contable_id<>?",Long.class,mapping.getKey(),funds);
            http.perform(post("/api/v1/accounting/cash-flow-classifications/lines").header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("partidaId",part,"clasificacion",mapping.getValue())))).andExpect(status().isOk());
        }
        for(long part:db.queryForList("select id from partidas_asiento where asiento_id=?",Long.class,noncash))
            http.perform(post("/api/v1/accounting/cash-flow-classifications/lines").header("Authorization","Bearer "+accountant.token()).contentType("application/json").content(json.writeValueAsString(Map.of("partidaId",part,"clasificacion","NO_MONETARIO")))).andExpect(status().isOk());
        http.perform(get("/api/v1/reports/finance/cash-flow").header("Authorization","Bearer "+accountant.token()).param("periodId",Long.toString(period)))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("DISPONIBLE")).andExpect(jsonPath("rubros[2].importe").value(75)).andExpect(jsonPath("rubros[3].importe").value(-20)).andExpect(jsonPath("rubros[4].importe").value(40)).andExpect(jsonPath("rubros[5].importe").value(95));
        http.perform(post("/api/v1/accounting/entries/{id}/reverse",expenseEntry).header("Authorization","Bearer "+accountant.token())
            .contentType("application/json").content(json.writeValueAsString(Map.of("fechaContable","1901-01-20","motivo","Corrección","claveIdempotencia",suffix+"REV")))).andExpect(status().isOk());
        http.perform(get("/api/v1/reports/finance/income-statement").header("Authorization","Bearer "+accountant.token()).param("periodId",Long.toString(period)))
            .andExpect(status().isOk()).andExpect(jsonPath("rubros[2].importe").value(0)).andExpect(jsonPath("rubros[3].importe").value(100));
        http.perform(get("/api/v1/reports/finance/balance-sheet").header("Authorization","Bearer "+accountant.token()).param("periodId",Long.toString(period)))
            .andExpect(status().isOk()).andExpect(jsonPath("rubros[5].importe").value(0));
        http.perform(get("/api/v1/reports/finance/income-statement").header("Authorization","Bearer "+reader.token()).param("periodId",Long.toString(period))).andExpect(status().isForbidden());
    }

    private long entry(Auth auth, long period, String date, long debitAccount, long creditAccount,
            String amount, String key, boolean post) throws Exception {
        String body = """
            {"periodoId":%d,"fechaContable":"%s","descripcion":"Movimiento de prueba",
             "claveIdempotencia":"%s","partidas":[
              {"cuentaId":%d,"debe":%s,"haber":0,"descripcion":"Cargo"},
              {"cuentaId":%d,"debe":0,"haber":%s,"descripcion":"Abono"}]}
            """.formatted(period, date, key, debitAccount, amount, creditAccount, amount);
        String response = http.perform(post("/api/v1/accounting/entries").header("Authorization", "Bearer " + auth.token())
                .contentType("application/json").content(body))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        if (post) http.perform(post("/api/v1/accounting/entries/{id}/post", id)
                .header("Authorization", "Bearer " + auth.token())).andExpect(status().isOk());
        return id;
    }

    @Test void mayorYBalancePruebaConciliablesIncluyenContraasientoYExcluyenBorrador() throws Exception {
        Auth accountant = token("CONTABILIDAD_REPORTES_LEER", "CONTABILIDAD_ASIENTOS_REGISTRAR",
            "CONTABILIDAD_ASIENTOS_CONTABILIZAR", "CONTABILIDAD_ASIENTOS_REVERTIR");
        Auth journalReader = token("CONTABILIDAD_ASIENTOS_LEER");
        long debit = account("MA"), credit = account("MB");
        // Fechas aisladas para que otras integraciones contables no contaminen el total del reporte.
        // Mantener el periodo antes de los movimientos de los demás casos de integración:
        // el balance incluye saldos de apertura de toda la compañía.
        int year = 1000 + Math.floorMod(UUID.randomUUID().hashCode(), 1000);
        String januaryStart = "%04d-01-01".formatted(year);
        String januaryEnd = "%04d-01-31".formatted(year);
        String februaryStart = "%04d-02-01".formatted(year);
        String februaryEnd = "%04d-02-28".formatted(year);
        long january = period(januaryStart, januaryEnd);
        long february = period(februaryStart, februaryEnd);
        long original = entry(accountant, january, "%04d-01-15".formatted(year), debit, credit, "100.00",
            "mayor-original-" + UUID.randomUUID(), true);
        entry(accountant, february, "%04d-02-10".formatted(year), debit, credit, "40.00",
            "mayor-periodo-" + UUID.randomUUID(), true);
        entry(accountant, february, "%04d-02-12".formatted(year), debit, credit, "999.00",
            "mayor-borrador-" + UUID.randomUUID(), false);
        String reversal = """
            {"fechaContable":"%04d-02-20","motivo":"Corrección de prueba","claveIdempotencia":"mayor-reversa-%s"}
            """.formatted(year, UUID.randomUUID());
        http.perform(post("/api/v1/accounting/entries/{id}/reverse", original)
                .header("Authorization", "Bearer " + accountant.token()).contentType("application/json").content(reversal))
            .andExpect(status().isOk());

        String ledger = "/api/v1/accounting/periods/" + february + "/accounts/" + debit + "/ledger";
        http.perform(get(ledger)).andExpect(status().isUnauthorized());
        http.perform(get(ledger).header("Authorization", "Bearer " + journalReader.token()))
            .andExpect(status().isForbidden());
        http.perform(get(ledger).header("Authorization", "Bearer " + accountant.token()).param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("saldoInicial").value(100.0))
            .andExpect(jsonPath("movimientoDebe").value(40.0)).andExpect(jsonPath("movimientoHaber").value(100.0))
            .andExpect(jsonPath("saldoFinal").value(40.0)).andExpect(jsonPath("totalElements").value(2))
            .andExpect(jsonPath("content.length()").value(1)).andExpect(jsonPath("content[0].saldo").value(140.0));
        http.perform(get(ledger).header("Authorization", "Bearer " + accountant.token())
                .param("page", "1").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("content[0].haber").value(100.0))
            .andExpect(jsonPath("content[0].saldo").value(40.0));

        http.perform(get("/api/v1/reports/finance/trial-balance").header("Authorization", "Bearer " + accountant.token())
                .param("periodId", Long.toString(february)).param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
            .andExpect(jsonPath("totalAperturaDeudora").value(100.0)).andExpect(jsonPath("totalAperturaAcreedora").value(100.0))
            .andExpect(jsonPath("totalMovimientosDebe").value(140.0)).andExpect(jsonPath("totalMovimientosHaber").value(140.0))
            .andExpect(jsonPath("totalCierreDeudor").value(40.0)).andExpect(jsonPath("totalCierreAcreedor").value(40.0))
            .andExpect(jsonPath("content.length()").value(1)).andExpect(jsonPath("content[0].codigoCuenta").value(org.hamcrest.Matchers.startsWith("MA")))
            .andExpect(jsonPath("content[0].aperturaDeudora").value(100.0))
            .andExpect(jsonPath("content[0].movimientosDebe").value(40.0))
            .andExpect(jsonPath("content[0].movimientosHaber").value(100.0))
            .andExpect(jsonPath("content[0].cierreDeudor").value(40.0));
        http.perform(get("/api/v1/reports/finance/trial-balance").header("Authorization", "Bearer " + journalReader.token())
                .param("periodId", Long.toString(february))).andExpect(status().isForbidden());
        http.perform(get("/api/v1/reports/finance/trial-balance").header("Authorization", "Bearer " + accountant.token())
                .param("periodId", "999999999")).andExpect(status().isNotFound());
        http.perform(get(ledger).header("Authorization", "Bearer " + accountant.token()).param("size", "101"))
            .andExpect(status().isBadRequest());
        assertThat(db.queryForObject("select estado from asientos_contables where id=?", String.class, original)).isEqualTo("REVERSADO");
    }
}
