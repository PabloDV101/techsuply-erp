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
class PedidoApiIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db; @Autowired MockMvc http; @Autowired PasswordEncoder passwords; @Autowired ObjectMapper json;
    long id(String sql,Object... args) { return db.queryForObject(sql,Long.class,args); }
    static String key() { return "P"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT); }
    record User(long id,String token) {}
    record Fixture(long customer,long product,long warehouse,long quote) {}
    User user(String... permissions) throws Exception {
        String k=key(), name=k.toLowerCase(Locale.ROOT), password=UUID.randomUUID().toString();
        long role=id("insert into roles(codigo,nombre) values (?,?) returning id","R"+k,"Pedido");
        for (String permission:permissions) assertThat(db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",role,permission)).isEqualTo(1);
        long user=id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",name,name+"@example.test",passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",user,role);
        String body=http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador",name,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new User(user,json.readTree(body).get("accessToken").asText());
    }
    Fixture fixture(User vendor,String state,LocalDate until) {
        String k=key();
        long customer=id("insert into clientes(codigo,nombre_razon_social) values (?,?) returning id",k,"Cliente");
        long category=id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",k,"Categoria");
        long product=id("insert into productos(sku,nombre,categoria_id,precio_lista,tasa_impuesto) values (?,?,?,99,16) returning id",k,"Producto",category);
        long warehouse=id("insert into almacenes(codigo,nombre) values (?,?) returning id",k,"Almacen");
        long quote=id("insert into cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia,subtotal,impuestos,total) values (?,?,?,?,?,?,27,4.32,31.32) returning id",k,customer,vendor.id(),until,state,k);
        db.update("insert into detalles_cotizacion(cotizacion_id,producto_id,cantidad,precio_unitario,descuento_pct,tasa_impuesto) values (?,?,3,10,10,16)",quote,product);
        return new Fixture(customer,product,warehouse,quote);
    }
    LocalDate today() { return LocalDate.now(ZoneId.of("America/Mexico_City")); }
    String body(Fixture f,String k) throws Exception { return json.writeValueAsString(Map.of("cotizacionId",f.quote(),"almacenId",f.warehouse(),"modalidad","CONTADO","claveIdempotencia",k)); }
    int create(User u,String body) throws Exception { return http.perform(post("/api/v1/sales/orders").header("Authorization","Bearer "+u.token()).contentType("application/json").content(body)).andReturn().getResponse().getStatus(); }
    @Test void flujoDesdeCotizacionHastaPedido() throws Exception {
        User u=user("VENTAS_COTIZACIONES_REGISTRAR","VENTAS_COTIZACIONES_ADMIN","VENTAS_PEDIDOS_REGISTRAR");
        Fixture f=fixture(u,"BORRADOR",today().plusDays(1));
        String payload=json.writeValueAsString(Map.of("clienteId",f.customer(),"vigenciaHasta",today().plusDays(1).toString(),
            "claveIdempotencia",key(),"detalles",List.of(Map.of("productoId",f.product(),"cantidad",3,"precioUnitario",10,"descuentoPct",10,"tasaImpuesto",16))));
        String quoteBody=http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+u.token()).contentType("application/json").content(payload))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long quote=json.readTree(quoteBody).get("id").asLong();
        http.perform(post("/api/v1/sales/quotes/{id}/issue",quote).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        http.perform(post("/api/v1/sales/quotes/{id}/accept",quote).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        Fixture accepted=new Fixture(f.customer(),f.product(),f.warehouse(),quote);
        http.perform(post("/api/v1/sales/orders").header("Authorization","Bearer "+u.token()).contentType("application/json").content(body(accepted,key())))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.total").value(31.32)).andExpect(jsonPath("$.detalles[0].precioUnitario").value(10));
    }
    @Test void convierteSnapshotConsultaYNoMueveStock() throws Exception {
        User vendor=user(), actor=user("VENTAS_PEDIDOS_REGISTRAR","VENTAS_PEDIDOS_LEER"); Fixture f=fixture(vendor,"ACEPTADA",today().plusDays(1)); String k=key();
        String response=http.perform(post("/api/v1/sales/orders").header("Authorization","Bearer "+actor.token()).contentType("application/json").content(body(f,k)))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("$.estado").value("BORRADOR"))
            .andExpect(jsonPath("$.vendedorUsuarioId").value(vendor.id())).andExpect(jsonPath("$.creadoPorUsuarioId").value(actor.id()))
            .andExpect(jsonPath("$.total").value(31.32)).andExpect(jsonPath("$.detalles[0].precioUnitario").value(10))
            .andExpect(jsonPath("$.detalles[0].cantidadReservada").value(0)).andReturn().getResponse().getContentAsString();
        long order=json.readTree(response).get("id").asLong();
        http.perform(get("/api/v1/sales/orders/{id}",order).header("Authorization","Bearer "+actor.token())).andExpect(status().isOk()).andExpect(jsonPath("$.detalles[0].descuentoPct").value(10));
        http.perform(get("/api/v1/sales/orders").header("Authorization","Bearer "+actor.token()).param("clienteId",Long.toString(f.customer())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(order));
        http.perform(get("/api/v1/sales/orders/{id}",Long.MAX_VALUE).header("Authorization","Bearer "+actor.token())).andExpect(status().isNotFound());
        http.perform(get("/api/v1/sales/orders").header("Authorization","Bearer "+actor.token()).param("size","101")).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='SALES_ORDER_CREATED' and entidad_id=?",Integer.class,order)).isEqualTo(1);
        db.update("update cotizaciones set vigencia_hasta=? where id=?",today().minusDays(1),f.quote());
        assertThat(create(actor,body(f,k))).isEqualTo(200);
        assertThat(create(actor,body(f,key()))).isEqualTo(409);
        User other=user("VENTAS_PEDIDOS_REGISTRAR"); assertThat(create(other,body(f,k))).isEqualTo(409);
        String changed=json.writeValueAsString(Map.of("cotizacionId",f.quote(),"almacenId",f.warehouse()+1000000,"modalidad","CONTADO","claveIdempotencia",k));
        assertThat(create(actor,changed)).isEqualTo(409);
    }
    @Test void conversionesConcurrentesSeSerializan() throws Exception {
        User u=user("VENTAS_PEDIDOS_REGISTRAR"); Fixture f=fixture(u,"ACEPTADA",today().plusDays(1)); String body=body(f,key());
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            Callable<Integer> action=()->{gate.await();return create(u,body);}; var a=pool.submit(action); var b=pool.submit(action); gate.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,201);
        } finally { pool.shutdownNow(); }
        Fixture other=fixture(u,"ACEPTADA",today().plusDays(1)); var p=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Integer> first=()->{start.await();return create(u,body(other,key()));}; var a=p.submit(first);var b=p.submit(first);start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } finally {p.shutdownNow();}
        assertThat(db.queryForObject("select count(*) from pedidos_venta where cotizacion_id=?",Integer.class,other.quote())).isEqualTo(1);
    }
    @Test void rechazaEstadosYReferenciasInvalidas() throws Exception {
        User u=user("VENTAS_PEDIDOS_REGISTRAR"); Fixture f=fixture(u,"BORRADOR",today().plusDays(1));
        assertThat(create(u,body(f,key()))).isEqualTo(409);
        db.update("update cotizaciones set estado='ACEPTADA',vigencia_hasta=? where id=?",today().minusDays(1),f.quote());
        assertThat(create(u,body(f,key()))).isEqualTo(409);
        db.update("update cotizaciones set vigencia_hasta=? where id=?",today().plusDays(1),f.quote());
        for (String table:List.of("clientes","almacenes","productos")) {
            long entity=switch(table) {case "clientes" -> f.customer(); case "almacenes" -> f.warehouse(); default -> f.product();};
            db.update("update "+table+" set activo=false where id=?",entity);
            assertThat(create(u,body(f,key()))).isEqualTo(409);
            db.update("update "+table+" set activo=true where id=?",entity);
        }
        db.update("delete from detalles_cotizacion where cotizacion_id=?",f.quote());
        assertThat(create(u,body(f,key()))).isEqualTo(409);
        http.perform(post("/api/v1/sales/orders").header("Authorization","Bearer "+u.token()).contentType("application/json").content("{\"cotizacionId\":0,\"almacenId\":1,\"modalidad\":\"CONTADO\",\"claveIdempotencia\":\"x\"}"))
            .andExpect(status().isBadRequest());
        http.perform(get("/api/v1/sales/orders")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/sales/orders").header("Authorization","Bearer "+u.token())).andExpect(status().isForbidden());
    }
    @Test void errorDeAuditoriaReviertePedidoYDetalles() throws Exception {
        User u=user("VENTAS_PEDIDOS_REGISTRAR"); Fixture f=fixture(u,"ACEPTADA",today().plusDays(1)); String k=key();
        db.execute("alter table eventos_auditoria add constraint ck_test_order_failure check (accion <> 'SALES_ORDER_CREATED') not valid");
        try { assertThat(create(u,body(f,k))).isEqualTo(409);
            assertThat(db.queryForObject("select count(*) from pedidos_venta where clave_idempotencia=?",Integer.class,k)).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_order_failure"); }
        assertThat(create(u,body(f,k))).isEqualTo(201);
    }
}
