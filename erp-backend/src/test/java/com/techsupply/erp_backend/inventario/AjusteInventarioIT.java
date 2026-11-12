package com.techsupply.erp_backend.inventario;

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
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AjusteInventarioIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http; @Autowired JdbcTemplate db; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    private record Fixture(String key,long warehouse,long first,long second) {}
    private String token(String... permissions) throws Exception {
        String key=UUID.randomUUID().toString(),password=UUID.randomUUID().toString();
        long role=db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id",Long.class,"T_"+key.substring(0,20).toUpperCase(Locale.ROOT),"Test");
        for(String permission:permissions) db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",role,permission);
        long user=db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",Long.class,key,key+"@example.test",passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",user,role);
        String body=http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador",key,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
    private Fixture fixture() {
        String key="A"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category=db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id",Long.class,key,"Prueba");
        long first=db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",Long.class,key+"A","Prueba",category);
        long second=db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",Long.class,key+"B","Prueba",category);
        long warehouse=db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id",Long.class,key,"Prueba");
        return new Fixture(key,warehouse,first,second);
    }
    private MvcResult create(String token,String body) throws Exception {
        return http.perform(post("/api/v1/inventory/adjustments").header("Authorization","Bearer "+token).contentType("application/json").content(body)).andReturn();
    }
    private static String body(long warehouse,String key,String lines) {
        return "{\"almacenId\":"+warehouse+",\"motivo\":\" Ajuste por conteo \",\"claveIdempotencia\":\""+key+"\",\"detalles\":["+lines+"]}";
    }
    private static String positive(long product,String quantity,String extra) {
        return "{\"productoId\":"+product+",\"tipo\":\"POSITIVO\",\"cantidad\":"+quantity+",\"costoUnitario\":10"+extra+"}";
    }
    private static String negative(long product,String quantity,String extra) {
        return "{\"productoId\":"+product+",\"tipo\":\"NEGATIVO\",\"cantidad\":"+quantity+extra+"}";
    }
    @Test void crearLeerValidarYReintentarNoAfectaStock() throws Exception {
        Fixture f=fixture(); String admin=token("INVENTARIO_AJUSTES_REGISTRAR","INVENTARIO_AJUSTES_LEER"), read=token("INVENTARIO_AJUSTES_LEER"), none=token("INVENTARIO_CONTEOS_LEER");
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada,costo_promedio,version) values (?,?,7,2,4,1)",f.first(),f.warehouse());
        String first=positive(f.first(),"1.000",",\"motivoLinea\":\" detalle \""),second=negative(f.second(),"2","");
        String request=body(f.warehouse(),"  "+f.key()+"  ",first+","+second);
        http.perform(post("/api/v1/inventory/adjustments").contentType("application/json").content(request)).andExpect(status().isUnauthorized());
        assertThat(create(read,request).getResponse().getStatus()).isEqualTo(403);
        http.perform(get("/api/v1/inventory/adjustments").header("Authorization","Bearer "+none)).andExpect(status().isForbidden());
        MvcResult created=create(admin,request); assertThat(created.getResponse().getStatus()).isEqualTo(201);
        long id=json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/inventory/adjustments/"+id);
        http.perform(get("/api/v1/inventory/adjustments/{id}",id).header("Authorization","Bearer "+read))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("PENDIENTE")).andExpect(jsonPath("motivo").value("Ajuste por conteo"))
            .andExpect(jsonPath("detalles[0].productoId").value(f.first())).andExpect(jsonPath("detalles[0].motivoLinea").value("detalle"))
            .andExpect(jsonPath("detalles[1].productoId").value(f.second())).andExpect(jsonPath("detalles[1].costoUnitario").value(nullValue()));
        String same=body(f.warehouse(),f.key(),negative(f.second(),"2.000",",\"costoUnitario\":null,\"detalleConteoId\":null,\"motivoLinea\":null")+","+positive(f.first(),"1",",\"motivoLinea\":\"detalle\""));
        db.update("update productos set activo=false where id=?",f.first());
        assertThat(create(admin,body(f.warehouse(),f.key()+"INACTIVE",first)).getResponse().getStatus()).isEqualTo(409);
        MvcResult repeat=create(admin,same); assertThat(repeat.getResponse().getStatus()).isEqualTo(200);
        assertThat(json.readTree(repeat.getResponse().getContentAsString()).get("id").asLong()).isEqualTo(id);
        db.update("update productos set activo=true where id=?",f.first());
        assertThat(create(admin,body(f.warehouse(),f.key(),positive(f.first(),"3",",\"motivoLinea\":\"detalle\"")+","+second)).getResponse().getStatus()).isEqualTo(409);
        http.perform(get("/api/v1/inventory/adjustments").header("Authorization","Bearer "+read).param("almacenId",Long.toString(f.warehouse())).param("estado","PENDIENTE"))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(id));
        http.perform(get("/api/v1/inventory/adjustments").header("Authorization","Bearer "+read).param("almacenId",Long.toString(f.warehouse()))).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        http.perform(get("/api/v1/inventory/adjustments").header("Authorization","Bearer "+read).param("estado","PENDIENTE")).andExpect(status().isOk());
        http.perform(get("/api/v1/inventory/adjustments").header("Authorization","Bearer "+read)).andExpect(status().isOk());
        http.perform(get("/api/v1/inventory/adjustments/{id}",999999999999L).header("Authorization","Bearer "+read)).andExpect(status().isNotFound());
        http.perform(get("/api/v1/inventory/adjustments").header("Authorization","Bearer "+read).param("estado","MAL")).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/inventory/adjustments").header("Authorization","Bearer "+read).param("size","101")).andExpect(status().isBadRequest());
        assertThat(create(admin,body(f.warehouse(),f.key()+"D",first+","+first)).getResponse().getStatus()).isEqualTo(409);
        assertThat(create(admin,body(f.warehouse(),f.key()+"X",negative(f.first(),"1",",\"costoUnitario\":10"))).getResponse().getStatus()).isEqualTo(400);
        assertThat(create(admin,body(f.warehouse(),f.key()+"N",negative(f.first(),"1",",\"costoUnitario\":null"))).getResponse().getStatus()).isEqualTo(201);
        assertThat(create(admin,body(f.warehouse(),f.key()+"NO", "{\"productoId\":"+f.first()+",\"tipo\":\"POSITIVO\",\"cantidad\":1}")).getResponse().getStatus()).isEqualTo(400);
        assertThat(create(admin,body(f.warehouse(),f.key()+"NULL", "{\"productoId\":"+f.first()+",\"tipo\":\"POSITIVO\",\"cantidad\":1,\"costoUnitario\":null}")).getResponse().getStatus()).isEqualTo(400);
        String maxCost="{\"productoId\":"+f.first()+",\"tipo\":\"POSITIVO\",\"cantidad\":1,\"costoUnitario\":999999999999.9999}";
        assertThat(create(admin,body(f.warehouse(),f.key()+"MAX",maxCost)).getResponse().getStatus()).isEqualTo(201);
        assertThat(db.queryForObject("select d.costo_unitario from detalles_ajuste_inventario d join ajustes_inventario a on a.id=d.ajuste_inventario_id where a.clave_idempotencia=?",java.math.BigDecimal.class,f.key()+"MAX")).isEqualByComparingTo("999999999999.9999");
        assertThat(create(admin,body(f.warehouse(),f.key()+"Z",positive(f.first(),"1.0001",""))).getResponse().getStatus()).isEqualTo(400);
        assertThat(create(admin,body(f.warehouse(),f.key()+"U",positive(f.first(),"1",",\"extra\":true"))).getResponse().getStatus()).isEqualTo(400);
        assertThat(create(admin,body(f.warehouse(),f.key()+"M",positive(f.first(),"1",",\"detalleConteoId\":null"))).getResponse().getStatus()).isEqualTo(201);
        db.update("update almacenes set activo=false where id=?",f.warehouse());
        assertThat(create(admin,body(f.warehouse(),f.key()+"I",first)).getResponse().getStatus()).isEqualTo(409);
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",java.math.BigDecimal.class,f.first())).isEqualByComparingTo("7");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",java.math.BigDecimal.class,f.first())).isEqualByComparingTo("2");
        assertThat(db.queryForObject("select costo_promedio from existencias where producto_id=?",java.math.BigDecimal.class,f.first())).isEqualByComparingTo("4");
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.first())).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,f.second())).isZero();
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id in (?,?)",Integer.class,f.first(),f.second())).isZero();
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_CREATED' and entidad_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void validaDetalleDeConteoCerradoYCoincidencia() throws Exception {
        Fixture f=fixture(),other=fixture(); String admin=token("INVENTARIO_AJUSTES_REGISTRAR","INVENTARIO_AJUSTES_LEER","INVENTARIO_CONTEOS_ADMIN");
        String c=http.perform(post("/api/v1/inventory/counts").header("Authorization","Bearer "+admin).contentType("application/json")
            .content(json.writeValueAsString(Map.of("almacenId",f.warehouse(),"fechaProgramada","2026-11-10","clase","GENERAL")))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long count=json.readTree(c).get("id").asLong();
        String captured=http.perform(put("/api/v1/inventory/counts/{id}/lines/{productoId}",count,f.first()).header("Authorization","Bearer "+admin).contentType("application/json").content("{\"cantidadContada\":5}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long detail=json.readTree(captured).get("detalles").get(0).get("id").asLong();
        String zeroCaptured=http.perform(put("/api/v1/inventory/counts/{id}/lines/{productoId}",count,f.second()).header("Authorization","Bearer "+admin).contentType("application/json").content("{\"cantidadContada\":0}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long zeroDetail=json.readTree(zeroCaptured).get("detalles").get(1).get("id").asLong();
        String ref=",\"detalleConteoId\":"+detail;
        assertThat(create(admin,body(f.warehouse(),f.key()+"O",positive(f.first(),"5",ref))).getResponse().getStatus()).isEqualTo(409);
        http.perform(post("/api/v1/inventory/counts/{id}/close",count).header("Authorization","Bearer "+admin)).andExpect(status().isOk());
        assertThat(create(admin,body(other.warehouse(),f.key()+"W",positive(f.first(),"5",ref))).getResponse().getStatus()).isEqualTo(409);
        assertThat(create(admin,body(f.warehouse(),f.key()+"P",positive(f.second(),"5",ref))).getResponse().getStatus()).isEqualTo(409);
        assertThat(create(admin,body(f.warehouse(),f.key()+"Q",positive(f.first(),"4",ref))).getResponse().getStatus()).isEqualTo(409);
        assertThat(create(admin,body(f.warehouse(),f.key()+"T",negative(f.first(),"5",ref))).getResponse().getStatus()).isEqualTo(409);
        assertThat(create(admin,body(f.warehouse(),f.key()+"ZERO",positive(f.second(),"1",",\"detalleConteoId\":"+zeroDetail))).getResponse().getStatus()).isEqualTo(409);
        assertThat(create(admin,body(f.warehouse(),f.key()+"R",positive(f.first(),"5",",\"detalleConteoId\":999999999999"))).getResponse().getStatus()).isEqualTo(404);
        MvcResult ok=create(admin,body(f.warehouse(),f.key()+"OK",positive(f.first(),"5",ref)));
        assertThat(ok.getResponse().getStatus()).isEqualTo(201);
        assertThat(json.readTree(ok.getResponse().getContentAsString()).get("detalles").get(0).get("detalleConteoId").asLong()).isEqualTo(detail);
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,f.first())).isZero();
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.first())).isZero();
    }
    @Test void reintentosConcurrentesSerializadosPorClave() throws Exception {
        Fixture f=fixture(); String admin=token("INVENTARIO_AJUSTES_REGISTRAR"),request=body(f.warehouse(),f.key(),positive(f.first(),"1",""));
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            Callable<MvcResult> task=()->{gate.await();return create(admin,request);}; var a=pool.submit(task);var b=pool.submit(task);gate.countDown();
            MvcResult x=a.get(20,TimeUnit.SECONDS),y=b.get(20,TimeUnit.SECONDS);
            assertThat(List.of(x.getResponse().getStatus(),y.getResponse().getStatus())).containsExactlyInAnyOrder(201,200);
            assertThat(json.readTree(x.getResponse().getContentAsString()).get("id").asLong()).isEqualTo(json.readTree(y.getResponse().getContentAsString()).get("id").asLong());
        } finally { pool.shutdownNow(); }
        var race=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1); String key=f.key()+"DIFF";
        try {
            var a=race.submit(()->{start.await();return create(admin,body(f.warehouse(),key,positive(f.first(),"1",""))).getResponse().getStatus();});
            var b=race.submit(()->{start.await();return create(admin,body(f.warehouse(),key,positive(f.first(),"2",""))).getResponse().getStatus();});
            start.countDown();assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } finally { race.shutdownNow(); }
        assertThat(db.queryForObject("select count(*) from ajustes_inventario where clave_idempotencia in (?,?)",Integer.class,f.key(),key)).isEqualTo(2);
    }
    @Test void falloDeAuditoriaRevierteSolicitud() throws Exception {
        Fixture f=fixture(); String admin=token("INVENTARIO_AJUSTES_REGISTRAR");
        long before=db.queryForObject("select last_value from ajuste_inventario_folio_seq",Long.class);
        boolean called=db.queryForObject("select is_called from ajuste_inventario_folio_seq",Boolean.class);
        db.execute("alter table eventos_auditoria add constraint ck_test_adjust_audit_failure check (accion <> 'INVENTORY_ADJUSTMENT_CREATED') not valid");
        try {
            assertThat(create(admin,body(f.warehouse(),f.key(),positive(f.first(),"1",""))).getResponse().getStatus()).isEqualTo(409);
            long after=db.queryForObject("select last_value from ajuste_inventario_folio_seq",Long.class);
            assertThat(after).isEqualTo(called?before+1:before);
            assertThat(db.queryForObject("select count(*) from ajustes_inventario where clave_idempotencia=?",Integer.class,f.key())).isZero();
            assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,f.first())).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_adjust_audit_failure"); }
    }
}
