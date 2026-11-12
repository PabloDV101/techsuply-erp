package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.time.LocalDate;
import java.time.ZoneId;
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
class CotizacionApiIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired MockMvc http;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private static String key() { return "Q" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT); }
    private String token(String... permissions) throws Exception {
        String k = key(), username = k.toLowerCase(Locale.ROOT), password = UUID.randomUUID().toString();
        long role = id("insert into roles(codigo,nombre) values (?,?) returning id", "R" + k, "Cotizaciones");
        for (String permission : permissions) assertThat(db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",role,permission)).isEqualTo(1);
        long user = id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",username,username+"@example.test",passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",user,role);
        String body = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador",username,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
    private record Fixture(long client, long product) {}
    private Fixture fixture() {
        String k = key();
        long client = id("insert into clientes(codigo,nombre_razon_social) values (?,?) returning id",k,"Cliente");
        long category = id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",k,"Categoría");
        long product = id("insert into productos(sku,nombre,categoria_id,precio_lista,tasa_impuesto) values (?,?,?,10,16) returning id",k,"Producto",category);
        return new Fixture(client,product);
    }
    private static LocalDate today() { return LocalDate.now(ZoneId.of("America/Mexico_City")); }
    private String payload(Fixture f, String quoteKey, LocalDate until, String quantity, String price, String discount, String tax) throws Exception {
        return json.writeValueAsString(Map.of("clienteId",f.client(),"vigenciaHasta",until.toString(),"claveIdempotencia",quoteKey,
            "detalles",List.of(Map.of("productoId",f.product(),"cantidad",new java.math.BigDecimal(quantity),
                "precioUnitario",new java.math.BigDecimal(price),"descuentoPct",new java.math.BigDecimal(discount),"tasaImpuesto",new java.math.BigDecimal(tax)))));
    }
    private long create(String auth, String body) throws Exception {
        String result=http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }
    @Test void crearEmitirAceptarConPrecioCongelado() throws Exception {
        String auth=token("VENTAS_COTIZACIONES_REGISTRAR","VENTAS_COTIZACIONES_ADMIN","VENTAS_COTIZACIONES_LEER");
        Fixture f=fixture(); String k=key(); String body=payload(f,k,today().plusDays(1),"3","10","10","16");
        long quote=create(auth,body);
        http.perform(get("/api/v1/sales/quotes/{id}",quote).header("Authorization","Bearer "+auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("BORRADOR"))
            .andExpect(jsonPath("$.subtotal").value(27.00)).andExpect(jsonPath("$.impuestos").value(4.32))
            .andExpect(jsonPath("$.total").value(31.32));
        db.update("update productos set precio_lista=99 where id=?",f.product());
        http.perform(post("/api/v1/sales/quotes/{id}/issue",quote).header("Authorization","Bearer "+auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("VIGENTE"));
        http.perform(post("/api/v1/sales/quotes/{id}/accept",quote).header("Authorization","Bearer "+auth))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("ACEPTADA"))
            .andExpect(jsonPath("$.detalles[0].precioUnitario").value(10.00));
        http.perform(post("/api/v1/sales/quotes/{id}/cancel",quote).header("Authorization","Bearer "+auth)).andExpect(status().isConflict());
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad='COTIZACION' and entidad_id=?",Integer.class,quote)).isEqualTo(3);
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isZero();
    }
    @Test void reintentosYCarrerasNoDuplicanYConflictosSeDetectan() throws Exception {
        String auth=token("VENTAS_COTIZACIONES_REGISTRAR"); Fixture f=fixture(); String k=key();
        LocalDate until=today().plusDays(2); String body=payload(f,k,until,"2","10","0","16");
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            Callable<Integer> call=()->{gate.await();return http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json").content(body)).andReturn().getResponse().getStatus();};
            var a=pool.submit(call);var b=pool.submit(call);gate.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,201);
        } finally { pool.shutdownNow(); }
        assertThat(db.queryForObject("select count(*) from cotizaciones where clave_idempotencia=?",Integer.class,k)).isEqualTo(1);
        http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json")
            .content(payload(f,k,until,"2.000","10.00","0.00","16.0"))).andExpect(status().isOk());
        http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json")
            .content(payload(f,k,until,"2","11","0","16"))).andExpect(status().isConflict());
        String another=token("VENTAS_COTIZACIONES_REGISTRAR");
        http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+another).contentType("application/json").content(body)).andExpect(status().isConflict());
    }
    @Test void reintentoConLineasReordenadasMantieneElDocumento() throws Exception {
        String auth=token("VENTAS_COTIZACIONES_REGISTRAR"); Fixture f=fixture(); String k=key();
        long category=id("select categoria_id from productos where id=?",f.product());
        long other=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key(),"Otro",category);
        Map<String,Object> first=Map.of("productoId",f.product(),"cantidad",1,"precioUnitario",10,"descuentoPct",0,"tasaImpuesto",16);
        Map<String,Object> second=Map.of("productoId",other,"cantidad",2,"precioUnitario",20,"descuentoPct",0,"tasaImpuesto",16);
        Map<String,Object> original=Map.of("clienteId",f.client(),"vigenciaHasta",today().plusDays(1).toString(),"claveIdempotencia",k,"detalles",List.of(first,second));
        long quote=create(auth,json.writeValueAsString(original));
        Map<String,Object> reordered=Map.of("clienteId",f.client(),"vigenciaHasta",original.get("vigenciaHasta"),"claveIdempotencia",k,"detalles",List.of(second,first));
        http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json")
            .content(json.writeValueAsString(reordered))).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(quote));
        Map<String,Object> duplicate=Map.of("clienteId",f.client(),"vigenciaHasta",original.get("vigenciaHasta"),"claveIdempotencia",key(),"detalles",List.of(first,first));
        http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json")
            .content(json.writeValueAsString(duplicate))).andExpect(status().isConflict());
    }
    @Test void concurrenciaDeEmisionNoDuplicaTransicionNiAuditoria() throws Exception {
        String auth=token("VENTAS_COTIZACIONES_REGISTRAR","VENTAS_COTIZACIONES_ADMIN"); Fixture f=fixture();
        long quote=create(auth,payload(f,key(),today().plusDays(1),"1","10","0","16"));
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            Callable<Integer> call=()->{ gate.await(); return http.perform(post("/api/v1/sales/quotes/{id}/issue",quote)
                .header("Authorization","Bearer "+auth)).andReturn().getResponse().getStatus(); };
            var first=pool.submit(call); var second=pool.submit(call); gate.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
            assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad_id=? and accion='SALES_QUOTE_ISSUED'",Integer.class,quote)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    @Test void validacionesYEstadosBloqueanEntradasInvalidas() throws Exception {
        String auth=token("VENTAS_COTIZACIONES_REGISTRAR","VENTAS_COTIZACIONES_ADMIN"); Fixture f=fixture();
        http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json")
            .content(payload(f,key(),today().minusDays(1),"1","1","0","16"))).andExpect(status().isConflict());
        http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json")
            .content(payload(f,key(),today().plusDays(1),"2","999999999999.99","0","0"))).andExpect(status().isBadRequest());
        http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json")
            .content("{\"clienteId\":1,\"vigenciaHasta\":\"2099-01-01\",\"claveIdempotencia\":\"invalid\",\"detalles\":[]}"))
            .andExpect(status().isBadRequest());
        db.update("update clientes set activo=false where id=?", f.client());
        http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json")
            .content(payload(f,key(),today().plusDays(1),"1","1","0","16"))).andExpect(status().isConflict());
        db.update("update clientes set activo=true where id=?", f.client());
        db.update("update productos set activo=false where id=?",f.product());
        http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json")
            .content(payload(f,key(),today().plusDays(1),"1","1","0","16"))).andExpect(status().isConflict());
        db.update("update productos set activo=true where id=?",f.product());
        long quote=create(auth,payload(f,key(),today().plusDays(1),"1","1","0","16"));
        http.perform(post("/api/v1/sales/quotes/{id}/accept",quote).header("Authorization","Bearer "+auth)).andExpect(status().isConflict());
        db.update("update cotizaciones set vigencia_hasta=? where id=?",today().minusDays(1),quote);
        http.perform(post("/api/v1/sales/quotes/{id}/issue",quote).header("Authorization","Bearer "+auth)).andExpect(status().isConflict());
        http.perform(post("/api/v1/sales/quotes/{id}/cancel",quote).header("Authorization","Bearer "+auth)).andExpect(status().isOk());
        http.perform(post("/api/v1/sales/quotes/{id}/cancel",quote).header("Authorization","Bearer "+auth)).andExpect(status().isConflict());
        long expired=create(auth,payload(f,key(),today().plusDays(1),"1","1","0","16"));
        http.perform(post("/api/v1/sales/quotes/{id}/issue",expired).header("Authorization","Bearer "+auth)).andExpect(status().isOk());
        db.update("update cotizaciones set vigencia_hasta=? where id=?",today().minusDays(1),expired);
        http.perform(post("/api/v1/sales/quotes/{id}/accept",expired).header("Authorization","Bearer "+auth)).andExpect(status().isConflict());
        assertThat(db.queryForObject("select estado from cotizaciones where id=?",String.class,expired)).isEqualTo("VIGENTE");
        http.perform(get("/api/v1/sales/quotes")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/sales/quotes").header("Authorization","Bearer "+auth)).andExpect(status().isForbidden());
    }
    @Test void fallaDeAuditoriaRevierteCreacionYTransicion() throws Exception {
        String auth=token("VENTAS_COTIZACIONES_REGISTRAR","VENTAS_COTIZACIONES_ADMIN"); Fixture f=fixture();
        String k=key();
        db.execute("alter table eventos_auditoria add constraint ck_test_quote_create_failure check (accion <> 'SALES_QUOTE_CREATED') not valid");
        try {
            http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+auth).contentType("application/json")
                .content(payload(f,k,today().plusDays(1),"1","10","0","16"))).andExpect(status().isConflict());
            assertThat(db.queryForObject("select count(*) from cotizaciones where clave_idempotencia=?",Integer.class,k)).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_quote_create_failure"); }
        long quote=create(auth,payload(f,key(),today().plusDays(1),"1","10","0","16"));
        db.execute("alter table eventos_auditoria add constraint ck_test_quote_issue_failure check (accion <> 'SALES_QUOTE_ISSUED') not valid");
        try {
            http.perform(post("/api/v1/sales/quotes/{id}/issue",quote).header("Authorization","Bearer "+auth)).andExpect(status().isConflict());
            assertThat(db.queryForObject("select estado from cotizaciones where id=?",String.class,quote)).isEqualTo("BORRADOR");
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_quote_issue_failure"); }
    }
}
