package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.application.AjusteInventarioService;
import com.techsupply.erp_backend.inventario.application.dto.RechazarAjusteRequest;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AutorizarAjusteIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http; @Autowired JdbcTemplate db; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    @Autowired AjusteInventarioService service;
    record Account(long id,String token) {}
    record Fixture(String key,long product,long warehouse) {}
    private Account account(String... permissions) throws Exception {
        String key=UUID.randomUUID().toString(),password=UUID.randomUUID().toString();
        long role=db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id",Long.class,"T_"+key.substring(0,20).toUpperCase(Locale.ROOT),"Test");
        for(String permission:permissions) db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",role,permission);
        long user=db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",Long.class,key,key+"@example.test",passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",user,role);
        String response=http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador",key,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(user,json.readTree(response).get("accessToken").asText());
    }
    private Fixture fixture() {
        String key="Q"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category=db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id",Long.class,key,"Test");
        long product=db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",Long.class,key,"Test",category);
        long warehouse=db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id",Long.class,key,"Test");
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada,costo_promedio,version) values (?,?,7,2,4,1)",product,warehouse);
        return new Fixture(key,product,warehouse);
    }
    private long create(Fixture f,Account creator,String suffix) throws Exception {
        var line=Map.of("productoId",f.product(),"tipo","POSITIVO","cantidad",1,"costoUnitario",10);
        var body=Map.of("almacenId",f.warehouse(),"motivo","Solicitud","claveIdempotencia",f.key()+suffix,"detalles",List.of(line));
        String result=http.perform(post("/api/v1/inventory/adjustments").header("Authorization","Bearer "+creator.token()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }
    private int approve(Account actor,long id) throws Exception {
        return http.perform(post("/api/v1/inventory/adjustments/{id}/approve",id).header("Authorization","Bearer "+actor.token())).andReturn().getResponse().getStatus();
    }
    private int reject(Account actor,long id,String jsonBody) throws Exception {
        return http.perform(post("/api/v1/inventory/adjustments/{id}/reject",id).header("Authorization","Bearer "+actor.token()).contentType("application/json").content(jsonBody)).andReturn().getResponse().getStatus();
    }
    private void stockUnchanged(Fixture f) {
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("7");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("2");
        assertThat(db.queryForObject("select costo_promedio from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("4");
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isZero();
    }
    @Test void autorizacionSegregacionEstadosYAuditoria() throws Exception {
        Fixture f=fixture(); Account creator=account("INVENTARIO_AJUSTES_REGISTRAR","INVENTARIO_AJUSTES_APROBAR","INVENTARIO_AJUSTES_LEER");
        Account approver=account("INVENTARIO_AJUSTES_APROBAR"), denied=account("INVENTARIO_AJUSTES_LEER"); long id=create(f,creator,"A");
        http.perform(post("/api/v1/inventory/adjustments/{id}/approve",id)).andExpect(status().isUnauthorized());
        long before=db.queryForObject("select count(*) from eventos_auditoria where accion='AUTHORIZATION_DENIED' and usuario_id=?",Long.class,denied.id());
        assertThat(approve(denied,id)).isEqualTo(403);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='AUTHORIZATION_DENIED' and usuario_id=?",Long.class,denied.id())).isEqualTo(before+1);
        assertThat(approve(creator,id)).isEqualTo(409);
        assertThat(reject(creator,id,"{\"motivo\":\"Recontar\"}")).isEqualTo(409);
        assertThat(db.queryForObject("select estado from ajustes_inventario where id=?",String.class,id)).isEqualTo("PENDIENTE");
        assertThat(approve(approver,999999999999L)).isEqualTo(404);
        assertThat(approve(approver,0)).isEqualTo(400);
        http.perform(post("/api/v1/inventory/adjustments/{id}/approve",id).header("Authorization","Bearer "+approver.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("APROBADO"))
            .andExpect(jsonPath("autorizadoPorUsuarioId").value(approver.id())).andExpect(jsonPath("autorizadoEn").isString())
            .andExpect(jsonPath("motivoRechazo").value(nullValue())).andExpect(jsonPath("aplicadoEn").value(nullValue()));
        http.perform(get("/api/v1/inventory/adjustments/{id}",id).header("Authorization","Bearer "+creator.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("APROBADO"))
            .andExpect(jsonPath("autorizadoPorUsuarioId").value(approver.id())).andExpect(jsonPath("autorizadoEn").isString());
        var retryLine=Map.of("productoId",f.product(),"tipo","POSITIVO","cantidad",1,"costoUnitario",10);
        var retryBody=Map.of("almacenId",f.warehouse(),"motivo","Solicitud","claveIdempotencia",f.key()+"A","detalles",List.of(retryLine));
        http.perform(post("/api/v1/inventory/adjustments").header("Authorization","Bearer "+creator.token()).contentType("application/json").content(json.writeValueAsString(retryBody)))
            .andExpect(status().isOk()).andExpect(jsonPath("id").value(id)).andExpect(jsonPath("estado").value("APROBADO"));
        assertThat(approve(approver,id)).isEqualTo(409);
        assertThat(reject(approver,id,"{\"motivo\":\"Recontar\"}")).isEqualTo(409);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_APPROVED' and entidad_id=? and usuario_id=? and detalle->>'estadoAnterior'='PENDIENTE' and detalle->>'estadoNuevo'='APROBADO'",Integer.class,id,approver.id())).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_REJECTED' and entidad_id=?",Integer.class,id)).isZero();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test",null,List.of()));
        try {
            assertThatThrownBy(()->service.aprobar(id)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
            assertThatThrownBy(()->service.rechazar(id,new RechazarAjusteRequest("No"))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
        stockUnchanged(f);
    }
    @Test void rechazoRequiereMotivoYNoExponeTextoEnAuditoria() throws Exception {
        Fixture f=fixture(); Account creator=account("INVENTARIO_AJUSTES_REGISTRAR"),approver=account("INVENTARIO_AJUSTES_APROBAR"); long id=create(f,creator,"R");
        assertThat(reject(approver,id,"{}")).isEqualTo(400);
        assertThat(reject(approver,id,"{\"motivo\":\"   \"}")).isEqualTo(400);
        assertThat(reject(approver,id,json.writeValueAsString(Map.of("motivo","x".repeat(1001))))).isEqualTo(400);
        assertThat(reject(approver,id,"{\"motivo\":\"Prueba\",\"extra\":true}")).isEqualTo(400);
        http.perform(post("/api/v1/inventory/adjustments/{id}/reject",id).header("Authorization","Bearer "+approver.token()).contentType("application/json")
            .content("{\"motivo\":\"  Recontar producto  \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("RECHAZADO")).andExpect(jsonPath("motivoRechazo").value("Recontar producto"))
            .andExpect(jsonPath("autorizadoPorUsuarioId").value(approver.id())).andExpect(jsonPath("autorizadoEn").isString());
        assertThat(reject(approver,id,"{\"motivo\":\"Recontar producto\"}")).isEqualTo(409);
        String audit=db.queryForObject("select detalle::text from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_REJECTED' and entidad_id=?",String.class,id);
        assertThat(audit).contains("PENDIENTE","RECHAZADO").doesNotContain("Recontar producto");
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_REJECTED' and entidad_id=?",Integer.class,id)).isEqualTo(1);
        stockUnchanged(f);
    }
    @Test void aprobarYRechazarConcurrentesProducenUnaSolaDecision() throws Exception {
        Fixture f=fixture(); Account creator=account("INVENTARIO_AJUSTES_REGISTRAR"),a=account("INVENTARIO_AJUSTES_APROBAR"),b=account("INVENTARIO_AJUSTES_APROBAR"); long id=create(f,creator,"C");
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var yes=pool.submit(()->{start.await();return approve(a,id);});
            var no=pool.submit(()->{start.await();return reject(b,id,"{\"motivo\":\"Recontar\"}");});
            start.countDown(); assertThat(List.of(yes.get(20,TimeUnit.SECONDS),no.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
        String state=db.queryForObject("select estado from ajustes_inventario where id=?",String.class,id);
        assertThat(state).isIn("APROBADO","RECHAZADO");
        assertThat(db.queryForObject("select autorizado_por_usuario_id from ajustes_inventario where id=?",Long.class,id)).isEqualTo(state.equals("APROBADO")?a.id():b.id());
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad_id=? and accion in ('INVENTORY_ADJUSTMENT_APPROVED','INVENTORY_ADJUSTMENT_REJECTED')",Integer.class,id)).isEqualTo(1);
        stockUnchanged(f);
    }
    @Test void fallaDeAuditoriaRevierteAprobacion() throws Exception {
        Fixture f=fixture(); Account creator=account("INVENTARIO_AJUSTES_REGISTRAR"),approver=account("INVENTARIO_AJUSTES_APROBAR"); long id=create(f,creator,"F");
        db.execute("alter table eventos_auditoria add constraint ck_test_approval_audit_failure check (accion <> 'INVENTORY_ADJUSTMENT_APPROVED') not valid");
        try {
            assertThat(approve(approver,id)).isEqualTo(409);
            assertThat(db.queryForObject("select estado from ajustes_inventario where id=?",String.class,id)).isEqualTo("PENDIENTE");
            assertThat(db.queryForObject("select count(*) from ajustes_inventario where id=? and autorizado_por_usuario_id is null and autorizado_en is null",Integer.class,id)).isEqualTo(1);
            assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_APPROVED' and entidad_id=?",Integer.class,id)).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_approval_audit_failure"); }
        assertThat(approve(approver,id)).isEqualTo(200);
        stockUnchanged(f);
    }
}
