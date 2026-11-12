package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
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
class DespachoApiIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db; @Autowired MockMvc http; @Autowired PasswordEncoder passwords; @Autowired ObjectMapper json;
    long id(String sql,Object... args) {return db.queryForObject(sql,Long.class,args);}
    static String key() {return "D"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);}
    record User(long id,String token) {}
    record Fixture(long order,long line,long product,long warehouse) {}
    User user(String... permissions) throws Exception {
        String k=key(),name=k.toLowerCase(Locale.ROOT),password=UUID.randomUUID().toString();
        long role=id("insert into roles(codigo,nombre) values (?,?) returning id","R"+k,"Despacho");
        for(String permission:permissions) assertThat(db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",role,permission)).isEqualTo(1);
        long user=id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",name,name+"@example.test",passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",user,role);
        String response=http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador",name,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new User(user,json.readTree(response).get("accessToken").asText());
    }
    Fixture fixture(User u,String state) {
        String k=key();
        long customer=id("insert into clientes(codigo,nombre_razon_social) values (?,?) returning id",k,"Cliente");
        long category=id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",k,"Categoria");
        long product=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",k,"Producto",category);
        long warehouse=id("insert into almacenes(codigo,nombre) values (?,?) returning id",k,"Almacen");
        long quote=id("insert into cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia) values (?,?,?,?,?,?) returning id",k,customer,u.id(),LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(2),"ACEPTADA",k);
        long order=id("insert into pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,clave_idempotencia) values (?,?,?,?,?,?,'INTERNO','CONTADO',?,?) returning id",k,customer,u.id(),quote,warehouse,u.id(),state,k);
        long line=id("insert into detalles_pedido(pedido_id,producto_id,cantidad,cantidad_reservada,precio_unitario,tasa_impuesto) values (?,?,5,5,10,16) returning id",order,product);
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada,costo_promedio) values (?,?,10,5,7)",product,warehouse);
        return new Fixture(order,line,product,warehouse);
    }
    String payload(Fixture f,String key,String quantity) throws Exception {return json.writeValueAsString(Map.of("pedidoId",f.order(),"claveIdempotencia",key,
        "detalles",List.of(Map.of("detallePedidoId",f.line(),"cantidad",new BigDecimal(quantity)))));}
    int create(User user,String body) throws Exception {return http.perform(post("/api/v1/sales/dispatches").header("Authorization","Bearer "+user.token()).contentType("application/json").content(body)).andReturn().getResponse().getStatus();}
    @Test void creaConsultaYReintentaSinCambiarInventario() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_LEER");Fixture f=fixture(u,"CONFIRMADO");String k=key();
        String result=http.perform(post("/api/v1/sales/dispatches").header("Authorization","Bearer "+u.token()).contentType("application/json").content(payload(f,k,"2")))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("$.estado").value("BORRADOR"))
            .andExpect(jsonPath("$.almacenId").value(f.warehouse())).andExpect(jsonPath("$.detalles[0].costoUnitario").doesNotExist())
            .andReturn().getResponse().getContentAsString();
        long dispatch=json.readTree(result).get("id").asLong();
        assertThat(json.readTree(result).get("detalles").get(0).has("costoUnitario")).isTrue();
        assertThat(create(u,payload(f,k,"2.000"))).isEqualTo(200);
        assertThat(create(u,payload(f,k,"3"))).isEqualTo(409);
        assertThat(create(user("VENTAS_DESPACHOS_REGISTRAR"),payload(f,k,"2"))).isEqualTo(409);
        http.perform(get("/api/v1/sales/dispatches/{id}",dispatch).header("Authorization","Bearer "+u.token())).andExpect(status().isOk()).andExpect(jsonPath("$.detalles[0].detallePedidoId").value(f.line()));
        http.perform(get("/api/v1/sales/dispatches").header("Authorization","Bearer "+u.token()).param("pedidoId",Long.toString(f.order())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("10");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("5");
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='SALES_DISPATCH_CREATED' and entidad_id=?",Integer.class,dispatch)).isEqualTo(1);
    }
    @Test void borradoresNoAsignanMasDeLaReserva() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR");Fixture f=fixture(u,"CONFIRMADO");
        assertThat(create(u,payload(f,key(),"3"))).isEqualTo(201);
        assertThat(create(u,payload(f,key(),"3"))).isEqualTo(409);
        assertThat(create(u,payload(f,key(),"2"))).isEqualTo(201);
        assertThat(create(u,payload(f,key(),"0.001"))).isEqualTo(409);
        assertThat(db.queryForObject("select count(*) from despachos where pedido_id=?",Integer.class,f.order())).isEqualTo(2);
    }
    @Test void admiteSaldoDePedidoParcialmenteDespachado() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR");Fixture f=fixture(u,"PARCIALMENTE_DESPACHADO");
        db.update("update detalles_pedido set cantidad_despachada=3,cantidad_reservada=2 where id=?",f.line());
        db.update("update existencias set cantidad_fisica=7,cantidad_reservada=2 where producto_id=?",f.product());
        assertThat(create(u,payload(f,key(),"2.001"))).isEqualTo(409);
        assertThat(create(u,payload(f,key(),"2"))).isEqualTo(201);
    }
    @Test void reintentoConLineasReordenadasYEscalasEquivalentes() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR");Fixture f=fixture(u,"CONFIRMADO");String k=key();
        long category=id("select categoria_id from productos where id=?",f.product());
        long secondProduct=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key(),"Segundo",category);
        long second=id("insert into detalles_pedido(pedido_id,producto_id,cantidad,cantidad_reservada,precio_unitario,tasa_impuesto) values (?,?,4,4,10,16) returning id",f.order(),secondProduct);
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada) values (?,?,4,4)",secondProduct,f.warehouse());
        var first=Map.of("detallePedidoId",f.line(),"cantidad",new BigDecimal("2"));
        var last=Map.of("detallePedidoId",second,"cantidad",new BigDecimal("1"));
        String original=json.writeValueAsString(Map.of("pedidoId",f.order(),"claveIdempotencia",k,"detalles",List.of(first,last)));
        String retry=json.writeValueAsString(Map.of("pedidoId",f.order(),"claveIdempotencia",k,"detalles",List.of(
            Map.of("detallePedidoId",second,"cantidad",new BigDecimal("1.000")),Map.of("detallePedidoId",f.line(),"cantidad",new BigDecimal("2.00")))));
        assertThat(create(u,original)).isEqualTo(201);assertThat(create(u,retry)).isEqualTo(200);
        assertThat(db.queryForObject("select count(*) from despachos where clave_idempotencia=?",Integer.class,k)).isEqualTo(1);
    }
    @Test void concurrenciaDeClavesYDeReservasSeSerializa() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR"); Fixture f=fixture(u,"CONFIRMADO");String content=payload(f,key(),"3");
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {Callable<Integer> call=()->{gate.await();return create(u,content);};var a=pool.submit(call);var b=pool.submit(call);gate.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,200);
        } finally {pool.shutdownNow();}
        Fixture other=fixture(u,"CONFIRMADO");var threads=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {Callable<Integer> call=()->{start.await();return create(u,payload(other,key(),"3"));};var a=threads.submit(call);var b=threads.submit(call);start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } finally {threads.shutdownNow();}
    }
    @Test void validacionesPermisosYEstados() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR");Fixture f=fixture(u,"BORRADOR");
        assertThat(create(u,payload(f,key(),"1"))).isEqualTo(409);
        db.update("update pedidos_venta set estado='CONFIRMADO' where id=?",f.order());
        assertThat(create(u,payload(f,key(),"6"))).isEqualTo(409);
        Fixture other=fixture(u,"CONFIRMADO");
        String mismatched=json.writeValueAsString(Map.of("pedidoId",f.order(),"claveIdempotencia",key(),"detalles",List.of(Map.of("detallePedidoId",other.line(),"cantidad",1))));
        assertThat(create(u,mismatched)).isEqualTo(409);
        String duplicate=json.writeValueAsString(Map.of("pedidoId",f.order(),"claveIdempotencia",key(),"detalles",List.of(Map.of("detallePedidoId",f.line(),"cantidad",1),Map.of("detallePedidoId",f.line(),"cantidad",1))));
        assertThat(create(u,duplicate)).isEqualTo(409);
        http.perform(post("/api/v1/sales/dispatches").header("Authorization","Bearer "+u.token()).contentType("application/json").content(payload(f,key(),"0"))).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/sales/dispatches")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/sales/dispatches").header("Authorization","Bearer "+u.token())).andExpect(status().isForbidden());
        User reader=user("VENTAS_DESPACHOS_LEER");
        http.perform(get("/api/v1/sales/dispatches/{id}",Long.MAX_VALUE).header("Authorization","Bearer "+reader.token())).andExpect(status().isNotFound());
        http.perform(get("/api/v1/sales/dispatches").header("Authorization","Bearer "+reader.token()).param("pedidoId","0")).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/sales/dispatches").header("Authorization","Bearer "+reader.token()).param("size","101")).andExpect(status().isBadRequest());
    }
    @Test void fallaAuditoriaRevierteDocumentoYDetalle() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR");Fixture f=fixture(u,"CONFIRMADO");String key=key();
        db.execute("alter table eventos_auditoria add constraint ck_test_dispatch_failure check (accion <> 'SALES_DISPATCH_CREATED') not valid");
        try {assertThat(create(u,payload(f,key,"1"))).isEqualTo(409);
            assertThat(db.queryForObject("select count(*) from despachos where clave_idempotencia=?",Integer.class,key)).isZero();
        } finally {db.execute("alter table eventos_auditoria drop constraint ck_test_dispatch_failure");}
        assertThat(create(u,payload(f,key,"1"))).isEqualTo(201);
    }
    @Test void flujoDesdeCotizacionHastaDespachoBorrador() throws Exception {
        User u=user("VENTAS_COTIZACIONES_REGISTRAR","VENTAS_COTIZACIONES_ADMIN","VENTAS_PEDIDOS_REGISTRAR","VENTAS_PEDIDOS_CONFIRMAR","VENTAS_DESPACHOS_REGISTRAR");
        Fixture f=fixture(u,"CONFIRMADO");
        long customer=id("select cliente_id from pedidos_venta where id=?",f.order());
        var quotePayload=Map.of("clienteId",customer,"vigenciaHasta",LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(1).toString(),"claveIdempotencia",key(),
            "detalles",List.of(Map.of("productoId",f.product(),"cantidad",3,"precioUnitario",10,"descuentoPct",10,"tasaImpuesto",16)));
        String quoteBody=http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(quotePayload)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long quote=json.readTree(quoteBody).get("id").asLong();
        http.perform(post("/api/v1/sales/quotes/{id}/issue",quote).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        http.perform(post("/api/v1/sales/quotes/{id}/accept",quote).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        var orderPayload=Map.of("cotizacionId",quote,"almacenId",f.warehouse(),"modalidad","CONTADO","claveIdempotencia",key());
        String orderBody=http.perform(post("/api/v1/sales/orders").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(orderPayload)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long order=json.readTree(orderBody).get("id").asLong(),line=json.readTree(orderBody).get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/sales/orders/{id}/confirm",order).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        var request=Map.of("pedidoId",order,"claveIdempotencia",key(),"detalles",List.of(Map.of("detallePedidoId",line,"cantidad",2)));
        http.perform(post("/api/v1/sales/dispatches").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(request)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.estado").value("BORRADOR"));
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("10");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("8");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isZero();
    }
}
