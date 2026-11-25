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
import java.util.concurrent.CountDownLatch;
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
class AnalyticalReportsApiIT extends PostgresIntegrationSupport {
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

    @Test void inventarioPoliticasFiltrosYExportaciones() throws Exception {
        String reader=token("REPORTERIA_INVENTARIO_LEER","REPORTERIA_MINIMOS_CONFIGURAR"), denied=token(SALES);
        String key="AN"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category=id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",key,key);
        long product=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key,key,category);
        long warehouse=id("insert into almacenes(codigo,nombre) values (?,?) returning id",key,key);
        String policy=json.writeValueAsString(Map.of("productId",product,"warehouseId",warehouse,"minimum",3));
        http.perform(put("/api/v1/reports/inventory/minimums").header("Authorization","Bearer "+reader).contentType("application/json").content(policy))
            .andExpect(status().isOk());
        http.perform(put("/api/v1/reports/inventory/minimums").header("Authorization","Bearer "+denied).contentType("application/json").content(policy))
            .andExpect(status().isForbidden());
        http.perform(get("/api/v1/reports/inventory/low-stock").header("Authorization","Bearer "+reader).param("warehouseId",Long.toString(warehouse)))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].available").value(0)).andExpect(jsonPath("content[0].reorderPoint").value(3));
        http.perform(get("/api/v1/reports/inventory/inactive-products").header("Authorization","Bearer "+reader).param("warehouseId",Long.toString(warehouse)).param("asOf","2026-01-01").param("days","30"))
            .andExpect(status().isOk()).andExpect(jsonPath("content[0].status").value("NUNCA_REGISTRADO"));
        for(String path:List.of("low-stock","inactive-products","turnover","abc")) {
            var req=get("/api/v1/reports/inventory/"+path).param("from","2026-01-01").param("to","2026-01-31").param("asOf","2026-01-31").param("days","30");
            http.perform(req).andExpect(status().isUnauthorized());
            http.perform(req.header("Authorization","Bearer "+denied)).andExpect(status().isForbidden());
            http.perform(get("/api/v1/reports/inventory/"+path).header("Authorization","Bearer "+reader)
                .param("from","2026-01-01").param("to","2026-01-31").param("asOf","2026-01-31").param("days","30").param("warehouseId",Long.toString(warehouse)))
                .andDo(r->{if(r.getResolvedException()!=null) throw new AssertionError(r.getResolvedException());}).andExpect(status().isOk());
            http.perform(get("/api/v1/reports/inventory/"+path).header("Authorization","Bearer "+reader)
                .param("from","2026-01-01").param("to","2026-01-31").param("asOf","2026-01-31").param("days","30").param("sort","id; drop table productos"))
                .andExpect(status().isBadRequest());
        }
        long before=id("select count(*) from eventos_auditoria where accion='REPORT_FILE_GENERATED'");
        for(String format:List.of("csv","pdf","xlsx")) {
            http.perform(get("/api/v1/reports/export").param("report","inventory/low-stock").param("format",format))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Content-Disposition"));
            var response=http.perform(get("/api/v1/reports/export").header("Authorization","Bearer "+reader).param("report","inventory/low-stock")
                .param("format",format).param("warehouseId",Long.toString(warehouse)))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse();
            byte[] bytes=response.getContentAsByteArray();
            if(format.equals("pdf")) {
                try(var pdf=org.apache.pdfbox.Loader.loadPDF(bytes)) {
                    assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(pdf)).contains("reorderPoint", "resultado.totalElements: 1");
                }
            } else if(format.equals("xlsx")) {
                try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(bytes))) {
                    assertThat(book.getSheetAt(0).getLastRowNum()).isGreaterThan(10);
                    assertThat(book.getSheetAt(0).getRow(1).getCell(1).getStringCellValue()).isEqualTo("inventory/low-stock");
                }
            } else assertThat(response.getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).contains("reorderPoint",Long.toString(warehouse));
            http.perform(get("/api/v1/reports/export").header("Authorization","Bearer "+denied).param("report","inventory/low-stock").param("format",format))
                .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Content-Disposition"));
        }
        assertThat(id("select count(*) from eventos_auditoria where accion='REPORT_FILE_GENERATED'")).isEqualTo(before+3);
        http.perform(get("/api/v1/reports/inventory/low-stock").header("Authorization","Bearer "+reader).param("warehouseId",Long.toString(warehouse)).param("page","100"))
            .andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty()).andExpect(jsonPath("totalElements").value(1));
    }
    @Test void auditoriaFallidaRevierteMinimoYNoEntregaExportacion() throws Exception {
        String actor=token("REPORTERIA_INVENTARIO_LEER","REPORTERIA_MINIMOS_CONFIGURAR");
        String key="AR"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category=id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",key,key);
        long product=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key,key,category);
        long warehouse=id("insert into almacenes(codigo,nombre) values (?,?) returning id",key,key);
        db.execute("alter table eventos_auditoria add constraint ck_test_reporting_audit check (accion not in ('REPORT_MINIMUM_CONFIGURED','REPORT_FILE_GENERATED')) not valid");
        try {
            http.perform(put("/api/v1/reports/inventory/minimums").header("Authorization","Bearer "+actor)
                    .contentType("application/json").content(json.writeValueAsString(Map.of("productId",product,"warehouseId",warehouse,"minimum",3))))
                .andExpect(status().isConflict());
            assertThat(id("select count(*) from minimos_producto_almacen where producto_id=? and almacen_id=?",product,warehouse)).isZero();
            http.perform(get("/api/v1/reports/export").header("Authorization","Bearer "+actor)
                    .param("report","inventory/low-stock").param("format","csv").param("warehouseId",Long.toString(warehouse)))
                .andExpect(status().isConflict()).andExpect(header().doesNotExist("Content-Disposition"));
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_reporting_audit"); }
    }
    @Test void clasificacionesConcurrentesNoSeSolapanYAnulacionEsIdempotente() throws Exception {
        String actor=token("CONTABILIDAD_MARGEN_CLASIFICAR"), denied=token(SALES);
        String key="CL"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long account=id("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?,?,'INGRESO','ACREEDORA') returning id",key,key);
        String path="/api/v1/reports/finance/operating-classifications";
        String payload=json.writeValueAsString(Map.of("accountId",account,"classification","INGRESO_OPERATIVO","from","2026-01-01","to","2026-12-31"));
        http.perform(post(path).header("Authorization","Bearer "+denied).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        var gate=new CountDownLatch(1);
        var pool=Executors.newFixedThreadPool(2);
        try {
            var first=pool.submit(()->{gate.await(); return http.perform(post(path).header("Authorization","Bearer "+actor).contentType("application/json").content(payload)).andReturn().getResponse().getStatus();});
            var second=pool.submit(()->{gate.await(); return http.perform(post(path).header("Authorization","Bearer "+actor).contentType("application/json").content(payload)).andReturn().getResponse().getStatus();});
            gate.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } finally {pool.shutdownNow();}
        assertThat(id("select count(*) from clasificaciones_margen_cuenta where cuenta_contable_id=? and anulada_en is null",account)).isEqualTo(1);
        long classification=id("select id from clasificaciones_margen_cuenta where cuenta_contable_id=?",account);
        String voidPath=path+"/"+classification+"/void", reason=json.writeValueAsString(Map.of("reason","Corrección aprobada"));
        for(int i=0;i<2;i++) http.perform(post(voidPath).header("Authorization","Bearer "+actor).contentType("application/json").content(reason)).andExpect(status().isNoContent());
        assertThat(id("select count(*) from eventos_auditoria where accion='OPERATING_CLASSIFICATION_VOIDED' and entidad_id=?",classification)).isEqualTo(1);
        assertThat(id("select count(*) from clasificaciones_margen_cuenta where id=? and anulada_en is not null",classification)).isEqualTo(1);
        http.perform(post(path).header("Authorization","Bearer "+actor).contentType("application/json").content(payload)).andExpect(status().isCreated());
        assertThat(id("select count(*) from clasificaciones_margen_cuenta where cuenta_contable_id=?",account)).isEqualTo(2);
    }
    @Test void ventasExigeAmbosPermisosYMargenNoInfiereClasificacion() throws Exception {
        String both=token(SALES,FINANCE,"CONTABILIDAD_MARGEN_CLASIFICAR"), sales=token(SALES);
        String finance=token(FINANCE);
        http.perform(get("/api/v1/reports/sales/customer-profitability").header("Authorization","Bearer "+sales).param("from","0800-01-01").param("to","0800-01-31"))
            .andExpect(status().isForbidden());
        http.perform(get("/api/v1/reports/sales/customer-profitability").header("Authorization","Bearer "+both).param("from","0800-01-01").param("to","0800-01-31"))
            .andExpect(status().isOk()).andExpect(jsonPath("content").isEmpty());
        for (String format : List.of("csv", "pdf", "xlsx")) {
            for (String partial : List.of(sales, finance)) {
                http.perform(get("/api/v1/reports/export").header("Authorization","Bearer "+partial)
                    .param("report","sales/customer-profitability").param("format",format).param("from","0800-01-01").param("to","0800-01-31"))
                    .andExpect(status().isForbidden()).andExpect(header().doesNotExist("Content-Disposition"));
            }
        }
        long period=id("insert into periodos_contables(fecha_inicio,fecha_fin,estado) values ('0802-01-01','0802-01-31','ABIERTO') returning id");
        http.perform(get("/api/v1/reports/finance/operating-margin").header("Authorization","Bearer "+both).param("periodId",Long.toString(period)))
            .andExpect(status().isOk()).andExpect(jsonPath("availability").value("NO_DISPONIBLE")).andExpect(jsonPath("value").isEmpty());
        http.perform(get("/api/v1/reports/finance/operating-margin").header("Authorization","Bearer "+both).param("periodId",Long.toString(Long.MAX_VALUE)))
            .andExpect(status().isNotFound());
    }
}
