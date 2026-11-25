package com.techsupply.erp_backend.reporteria;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import com.techsupply.erp_backend.reporteria.application.DashboardService;
import com.techsupply.erp_backend.security.infrastructure.security.SecurityActor;
import java.sql.Connection;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class DashboardApiIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;
    @Autowired DataSource dataSource;
    @Autowired DashboardService service;
    private static final String PURCHASES = "COMPRAS_FACTURAS_PROVEEDOR_LEER";
    private static final String SALES = "VENTAS_FACTURAS_LEER";
    private static final String STOCK = "INVENTARIO_ALERTAS_LEER";
    private static final String FINANCE = "CONTABILIDAD_REPORTES_LEER";

    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private String token(String... permissions) throws Exception {
        String key=UUID.randomUUID().toString(), password=UUID.randomUUID().toString();
        long role=id("insert into roles(codigo,nombre) values (?,?) returning id", "DB_"+key.substring(0,20).toUpperCase(Locale.ROOT), "Dashboard test");
        for (String permission : permissions)
            assertThat(db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, permission)).isEqualTo(1);
        long user=id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", key, key+"@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        String body=http.perform(post("/api/v1/auth/login").contentType("application/json")
                .content(json.writeValueAsString(Map.of("identificador",key,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
    private JsonNode dashboard(String token, String periodId) throws Exception {
        var request=get("/api/v1/reports/dashboard").header("Authorization","Bearer "+token)
            .param("from","0800-01-01").param("to","0800-01-31");
        if(periodId!=null) request.param("periodId",periodId);
        return json.readTree(http.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private JsonNode metric(JsonNode dashboard, String code) {
        for (JsonNode metric : dashboard.get("indicators")) if(code.equals(metric.get("code").asText())) return metric;
        throw new AssertionError("Indicador ausente: "+code);
    }
    private long summaryCount(String token) throws Exception {
        String body=http.perform(get("/api/v1/reports/inventory/low-stock-summary").header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("productosEnAlerta").asLong();
    }

    @Test void filtrosPermisosParcialesPeriodosYDisponibilidad() throws Exception {
        String all=token(PURCHASES,SALES,STOCK,FINANCE), sales=token(SALES), finance=token(FINANCE), denied=token("VENTAS_PEDIDOS_LEER");
        long period=id("insert into periodos_contables(fecha_inicio,fecha_fin,estado) values ('0801-01-01','0801-01-31','ABIERTO') returning id");
        JsonNode result=dashboard(all,Long.toString(period));
        assertThat(result.get("from").asText()).isEqualTo("0800-01-01");
        assertThat(result.get("to").asText()).isEqualTo("0800-01-31");
        assertThat(result.get("indicators").size()).isEqualTo(4);
        for(String code:List.of("COMPRAS_FACTURADO_SIN_IMPUESTOS","VENTAS_FACTURADO_SIN_IMPUESTOS")) {
            JsonNode m=metric(result,code);
            assertThat(m.get("availability").asText()).isEqualTo("DISPONIBLE");
            assertThat(m.get("from").asText()).isEqualTo("0800-01-01");
            assertThat(m.get("to").asText()).isEqualTo("0800-01-31");
            assertThat(m.get("value").decimalValue()).isEqualByComparingTo("0");
            assertThat(m.get("asOf").isNull()).isTrue();
            assertThat(m.get("unavailabilityReason").isNull()).isTrue();
            assertThat(m.get("unit").asText()).isEqualTo("IMPORTE");
        }
        JsonNode stock=metric(result,"INVENTARIO_PRODUCTOS_ALERTA");
        assertThat(stock.get("value").asLong()).isEqualTo(summaryCount(all));
        assertThat(stock.get("unit").asText()).isEqualTo("PRODUCTOS");
        assertThat(stock.get("from").isNull()).isTrue(); assertThat(stock.get("to").isNull()).isTrue();
        assertThat(Instant.parse(stock.get("asOf").asText())).isNotNull();
        JsonNode financial=metric(result,"FINANZAS_RESULTADO");
        assertThat(financial.get("from").asText()).isEqualTo("0801-01-01");
        assertThat(financial.get("to").asText()).isEqualTo("0801-01-31");
        assertThat(financial.get("value").decimalValue()).isEqualByComparingTo("0");
        JsonNode onlySales=dashboard(sales,Long.toString(Long.MAX_VALUE));
        assertThat(onlySales.get("indicators").size()).isEqualTo(1);
        assertThat(onlySales.get("indicators").get(0).get("code").asText()).isEqualTo("VENTAS_FACTURADO_SIN_IMPUESTOS");
        JsonNode noPeriod=metric(dashboard(finance,null),"FINANZAS_RESULTADO");
        assertThat(noPeriod.get("availability").asText()).isEqualTo("NO_DISPONIBLE");
        assertThat(noPeriod.get("value").isNull()).isTrue();
        assertThat(noPeriod.get("from").isNull()).isTrue(); assertThat(noPeriod.get("to").isNull()).isTrue();
        assertThat(noPeriod.get("unavailabilityReason").asText()).isNotBlank();
        http.perform(get("/api/v1/reports/dashboard").param("from","0800-01-01").param("to","0800-01-31"))
            .andExpect(status().isUnauthorized()).andExpect(jsonPath("indicators").doesNotExist());
        http.perform(get("/api/v1/reports/dashboard").header("Authorization","Bearer "+denied)
                .param("from","0800-01-01").param("to","0800-01-31"))
            .andExpect(status().isForbidden()).andExpect(jsonPath("indicators").doesNotExist());
        http.perform(get("/api/v1/reports/dashboard").header("Authorization","Bearer "+finance)
                .param("from","0800-01-01").param("to","0800-01-31").param("periodId",Long.toString(Long.MAX_VALUE)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("indicators").doesNotExist());
        http.perform(get("/api/v1/reports/dashboard").header("Authorization","Bearer "+all)
                .param("from","0800-01-01").param("to","0800-01-31").param("periodId",Long.toString(Long.MAX_VALUE)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("indicators").doesNotExist());
        for(var params:List.of(Map.of("from","0800-02-01","to","0800-01-31"), Map.of("from","bad","to","0800-01-31"),
                Map.of("from","0800-01-01"), Map.of("from","0800-01-01","to","0800-01-31","periodId","0"))) {
            var request=get("/api/v1/reports/dashboard").header("Authorization","Bearer "+all);
            params.forEach(request::param); http.perform(request).andExpect(status().isBadRequest());
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
            new SecurityActor(1L,"test"), null, List.of()));
        try { assertThatThrownBy(() -> service.consultar(LocalDate.of(800,1,1),LocalDate.of(800,1,31),null))
            .isInstanceOf(org.springframework.security.access.AccessDeniedException.class); }
        finally { SecurityContextHolder.clearContext(); }
    }

    @Test void exportacionConPermisosDisponibilidadYAuditoria() throws Exception {
        String reader=token(SALES,FINANCE), denied=token("VENTAS_PEDIDOS_LEER");
        long before=id("select count(*) from eventos_auditoria where accion='DASHBOARD_CSV_GENERATED'");
        var response=http.perform(get("/api/v1/reports/dashboard/export.csv")
                .header("Authorization","Bearer "+reader).param("from","0800-01-01").param("to","0800-01-31"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(header().string("Content-Disposition","attachment; filename=dashboard.csv"))
            .andExpect(content().contentType("text/csv;charset=UTF-8")).andReturn().getResponse();
        String csv=response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(csv.split("\r\n")).hasSize(3);
        assertThat(csv).contains("VENTAS_FACTURADO_SIN_IMPUESTOS", "FINANZAS_RESULTADO", "NO_DISPONIBLE")
            .doesNotContain("COMPRAS_FACTURADO", "INVENTARIO_PRODUCTOS_ALERTA");
        assertThat(csv).contains("\"DISPONIBLE\",\"0\"");
        assertThat(csv).contains("\"NO_DISPONIBLE\",,\"IMPORTE\"");
        assertThat(id("select count(*) from eventos_auditoria where accion='DASHBOARD_CSV_GENERATED' and usuario_id is not null"))
            .isEqualTo(before+1);
        http.perform(get("/api/v1/reports/dashboard/export.csv").param("from","0800-01-01").param("to","0800-01-31"))
            .andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/reports/dashboard/export.csv").header("Authorization","Bearer "+denied)
            .param("from","0800-01-01").param("to","0800-01-31")).andExpect(status().isForbidden());
        http.perform(get("/api/v1/reports/dashboard/export.csv").header("Authorization","Bearer "+reader)
            .param("from","0800-02-01").param("to","0800-01-31")).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/reports/dashboard/export.csv").header("Authorization","Bearer "+reader)
            .param("from","0800-01-01").param("to","0800-01-31").param("periodId",Long.toString(Long.MAX_VALUE)))
            .andExpect(status().isNotFound());
        assertThat(id("select count(*) from eventos_auditoria where accion='DASHBOARD_CSV_GENERATED'")).isEqualTo(before+1);
    }

    @Test void lecturasCompartenInstantaneaDuranteCambioConcurrente() throws Exception {
        String reader=token(STOCK);
        String key="SN"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category=id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",key,"Instantánea");
        long product=id("insert into productos(sku,nombre,categoria_id,punto_reorden,activo) values (?,?,?,1,true) returning id",key,"Instantánea",category);
        long before=summaryCount(reader);
        var pool=Executors.newSingleThreadExecutor();
        try(Connection lock=dataSource.getConnection()) {
            lock.setAutoCommit(false);
            try(var statement=lock.createStatement()) { statement.execute("LOCK TABLE productos IN ACCESS EXCLUSIVE MODE"); }
            try {
                var future=pool.submit(() -> dashboard(reader,null));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(15);
                boolean waiting=false;
                while(System.nanoTime()<deadline) {
                    Integer blocked=db.queryForObject("""
                        select count(*) from pg_stat_activity where datname=current_database() and wait_event_type='Lock'
                          and lower(query) like '%with alertas as materialized%'
                        """,Integer.class);
                    if(blocked!=null && blocked>0) { waiting=true; break; }
                    Thread.sleep(30);
                }
                assertThat(waiting).as("el dashboard debe alcanzar la lectura bloqueada").isTrue();
                try(var update=lock.prepareStatement("update productos set activo=false where id=?")) {
                    update.setLong(1,product); update.executeUpdate();
                }
                lock.commit();
                JsonNode captured=metric(future.get(20,TimeUnit.SECONDS),"INVENTARIO_PRODUCTOS_ALERTA");
                assertThat(captured.get("value").asLong()).isEqualTo(before);
                assertThat(summaryCount(reader)).isEqualTo(before-1);
            } finally { if(!lock.getAutoCommit()) lock.rollback(); }
        } finally { pool.shutdownNow(); }
    }
}
