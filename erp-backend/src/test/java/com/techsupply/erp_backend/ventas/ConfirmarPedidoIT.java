package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import com.techsupply.erp_backend.security.infrastructure.security.SecurityActor;
import com.techsupply.erp_backend.inventario.application.InventarioService;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ConfirmarPedidoIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db; @Autowired MockMvc http; @Autowired PasswordEncoder passwords; @Autowired ObjectMapper json;
    @Autowired InventarioService inventory; @Autowired TransactionTemplate tx;
    long id(String sql,Object... args) { return db.queryForObject(sql,Long.class,args); }
    static String key() { return "R"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT); }
    record User(long id,String token) {}
    record Fixture(long customer,long product,long warehouse,long quote,long order) {}
    User user(String... permissions) throws Exception {
        String k=key(), name=k.toLowerCase(Locale.ROOT), password=UUID.randomUUID().toString();
        long role=id("insert into roles(codigo,nombre) values (?,?) returning id","R"+k,"Reserva");
        for (String permission:permissions) assertThat(db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",role,permission)).isEqualTo(1);
        long user=id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",name,name+"@example.test",passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",user,role);
        String body=http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador",name,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new User(user,json.readTree(body).get("accessToken").asText());
    }
    LocalDate today() { return LocalDate.now(ZoneId.of("America/Mexico_City")); }
    Fixture fixture(User user,BigDecimal physical,BigDecimal reserved) {
        String k=key();
        long customer=id("insert into clientes(codigo,nombre_razon_social) values (?,?) returning id",k,"Cliente");
        long category=id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",k,"Categoria");
        long product=id("insert into productos(sku,nombre,categoria_id,precio_lista,tasa_impuesto) values (?,?,?,99,16) returning id",k,"Producto",category);
        long warehouse=id("insert into almacenes(codigo,nombre) values (?,?) returning id",k,"Almacen");
        long quote=id("insert into cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia,subtotal,impuestos,total) values (?,?,?,?,?,?,27,4.32,31.32) returning id",k,customer,user.id(),today().plusDays(1),"ACEPTADA",k);
        db.update("insert into detalles_cotizacion(cotizacion_id,producto_id,cantidad,precio_unitario,descuento_pct,tasa_impuesto) values (?,?,3,10,10,16)",quote,product);
        long order=id("insert into pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,clave_idempotencia,subtotal,impuestos,total) values (?,?,?,?,?,?,'INTERNO','CONTADO','BORRADOR',?,27,4.32,31.32) returning id",k,customer,user.id(),quote,warehouse,user.id(),k);
        db.update("insert into detalles_pedido(pedido_id,producto_id,cantidad,precio_unitario,descuento_pct,tasa_impuesto) values (?,?,3,10,10,16)",order,product);
        if (physical!=null) db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada) values (?,?,?,?)",product,warehouse,physical,reserved);
        return new Fixture(customer,product,warehouse,quote,order);
    }
    int confirm(User u,long order) throws Exception { return http.perform(post("/api/v1/sales/orders/{id}/confirm",order).header("Authorization","Bearer "+u.token())).andReturn().getResponse().getStatus(); }
    long anotherOrder(User u, Fixture f) {
        String k=key();
        long quote=id("insert into cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia,subtotal,impuestos,total) values (?,?,?,?,?,?,27,4.32,31.32) returning id",k,f.customer(),u.id(),today().plusDays(1),"ACEPTADA",k);
        db.update("insert into detalles_cotizacion(cotizacion_id,producto_id,cantidad,precio_unitario,descuento_pct,tasa_impuesto) values (?,?,3,10,10,16)",quote,f.product());
        long order=id("insert into pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,clave_idempotencia,subtotal,impuestos,total) values (?,?,?,?,?,?,'INTERNO','CONTADO','BORRADOR',?,27,4.32,31.32) returning id",k,f.customer(),u.id(),quote,f.warehouse(),u.id(),k);
        db.update("insert into detalles_pedido(pedido_id,producto_id,cantidad,precio_unitario,descuento_pct,tasa_impuesto) values (?,?,3,10,10,16)",order,f.product());
        return order;
    }
    @Test void pedidosConcurrentesNoSobrevenden() throws Exception {
        User u=user("VENTAS_PEDIDOS_CONFIRMAR"); Fixture f=fixture(u,new BigDecimal("10"),new BigDecimal("6")); long second=anotherOrder(u,f);
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            var first=pool.submit(()->{gate.await();return confirm(u,f.order());});
            var other=pool.submit(()->{gate.await();return confirm(u,second);}); gate.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),other.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally {pool.shutdownNow();}
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("9");
        assertThat(db.queryForObject("select count(*) from pedidos_venta where id in (?,?) and estado='CONFIRMADO'",Integer.class,f.order(),second)).isEqualTo(1);
    }
    @Test void confirmacionesSimultaneasDelMismoPedidoNoDuplicanReserva() throws Exception {
        User u=user("VENTAS_PEDIDOS_CONFIRMAR"); Fixture f=fixture(u,new BigDecimal("10"),BigDecimal.ZERO);
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            Callable<Integer> call=()->{gate.await();return confirm(u,f.order());};
            var first=pool.submit(call);var second=pool.submit(call);gate.countDown();
            assertThat(List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally {pool.shutdownNow();}
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("3");
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='SALES_ORDER_CONFIRMED' and entidad_id=?",Integer.class,f.order())).isEqualTo(1);
    }
    @Test void faltanteNoCreaExistenciaNiConfirma() throws Exception {
        User u=user("VENTAS_PEDIDOS_CONFIRMAR"); Fixture f=fixture(u,null,null);
        assertThat(confirm(u,f.order())).isEqualTo(409);
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("BORRADOR");
    }
    @Test void laReservaEsAtomicaYAumentaVersion() throws Exception {
        User u=user("VENTAS_PEDIDOS_CONFIRMAR"); Fixture f=fixture(u,new BigDecimal("10"),new BigDecimal("6"));
        assertThat(confirm(u,f.order())).isEqualTo(200);
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("9");
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isEqualTo(1);
        assertThat(db.queryForObject("select cantidad_reservada from detalles_pedido where pedido_id=?",BigDecimal.class,f.order())).isEqualByComparingTo("3");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isZero();
        db.update("update cotizaciones set vigencia_hasta=? where id=?",today().minusDays(1),f.quote());
        assertThat(confirm(u,f.order())).isEqualTo(200);
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("9");
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='SALES_ORDER_CONFIRMED' and entidad_id=?",Integer.class,f.order())).isEqualTo(1);
    }
    @Test void fallaAuditoriaRevierteReservaYEstado() throws Exception {
        User u=user("VENTAS_PEDIDOS_CONFIRMAR"); Fixture f=fixture(u,new BigDecimal("10"),BigDecimal.ZERO);
        db.execute("alter table eventos_auditoria add constraint ck_test_order_confirm_failure check (accion <> 'SALES_ORDER_CONFIRMED') not valid");
        try {
            assertThat(confirm(u,f.order())).isEqualTo(409);
            assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("0");
            assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isZero();
            assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("BORRADOR");
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_order_confirm_failure"); }
    }
    @Test void llamadaInternaNoPuedeDejarReservaSinPedidoConfirmado() throws Exception {
        User u=user("VENTAS_PEDIDOS_CONFIRMAR"); Fixture f=fixture(u,new BigDecimal("10"),BigDecimal.ZERO);
        long detail=id("select id from detalles_pedido where pedido_id=?",f.order());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new SecurityActor(u.id(),"prueba"),null,
            List.of(new SimpleGrantedAuthority("VENTAS_PEDIDOS_CONFIRMAR"))));
        try { assertThatThrownBy(()->tx.executeWithoutResult(status->inventory.reservarLineaPedido(detail))).isInstanceOf(IllegalStateException.class); }
        finally { SecurityContextHolder.clearContext(); }
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("0");
        assertThat(db.queryForObject("select cantidad_reservada from detalles_pedido where id=?",BigDecimal.class,detail)).isEqualByComparingTo("0");
    }
    @Test void flujoHttpCotizacionPedidoYReserva() throws Exception {
        User u=user("VENTAS_COTIZACIONES_REGISTRAR","VENTAS_COTIZACIONES_ADMIN","VENTAS_PEDIDOS_REGISTRAR","VENTAS_PEDIDOS_CONFIRMAR");
        Fixture f=fixture(u,new BigDecimal("10"),BigDecimal.ZERO);
        String quotePayload=json.writeValueAsString(Map.of("clienteId",f.customer(),"vigenciaHasta",today().plusDays(1).toString(),"claveIdempotencia",key(),
            "detalles",List.of(Map.of("productoId",f.product(),"cantidad",3,"precioUnitario",10,"descuentoPct",10,"tasaImpuesto",16))));
        String quoteBody=http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+u.token()).contentType("application/json").content(quotePayload))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long quote=json.readTree(quoteBody).get("id").asLong();
        http.perform(post("/api/v1/sales/quotes/{id}/issue",quote).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        http.perform(post("/api/v1/sales/quotes/{id}/accept",quote).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        String orderPayload=json.writeValueAsString(Map.of("cotizacionId",quote,"almacenId",f.warehouse(),"modalidad","CONTADO","claveIdempotencia",key()));
        String orderBody=http.perform(post("/api/v1/sales/orders").header("Authorization","Bearer "+u.token()).contentType("application/json").content(orderPayload))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long order=json.readTree(orderBody).get("id").asLong();
        http.perform(post("/api/v1/sales/orders/{id}/confirm",order).header("Authorization","Bearer "+u.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADO"))
            .andExpect(jsonPath("$.detalles[0].cantidadReservada").value(3)).andExpect(jsonPath("$.total").value(31.32));
    }
    @Test void fallaEnSegundaLineaRevierteLaPrimera() throws Exception {
        User u=user("VENTAS_PEDIDOS_CONFIRMAR"); Fixture f=fixture(u,new BigDecimal("10"),BigDecimal.ZERO);
        long category=id("select categoria_id from productos where id=?",f.product());
        long product=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key(),"Segundo",category);
        db.update("insert into detalles_cotizacion(cotizacion_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,2,10,16)",f.quote(),product);
        db.update("insert into detalles_pedido(pedido_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,2,10,16)",f.order(),product);
        db.update("update cotizaciones set subtotal=47,impuestos=7.52,total=54.52 where id=?",f.quote());
        db.update("update pedidos_venta set subtotal=47,impuestos=7.52,total=54.52 where id=?",f.order());
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica) values (?,?,1)",product,f.warehouse());
        assertThat(confirm(u,f.order())).isEqualTo(409);
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("0");
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select count(*) from detalles_pedido where pedido_id=? and cantidad_reservada<>0",Integer.class,f.order())).isZero();
    }
    @Test void permisosYReferenciasSeValidan() throws Exception {
        User u=user(); Fixture f=fixture(u,new BigDecimal("10"),BigDecimal.ZERO);
        http.perform(post("/api/v1/sales/orders/{id}/confirm",f.order())).andExpect(status().isUnauthorized());
        assertThat(confirm(u,f.order())).isEqualTo(403);
        User allowed=user("VENTAS_PEDIDOS_CONFIRMAR");
        assertThat(confirm(allowed,Long.MAX_VALUE)).isEqualTo(404);
        for(String table:List.of("clientes","almacenes","productos")) {
            long entity=switch(table) {case "clientes" -> f.customer();case "almacenes" -> f.warehouse();default -> f.product();};
            db.update("update "+table+" set activo=false where id=?",entity);
            assertThat(confirm(allowed,f.order())).isEqualTo(409);
            db.update("update "+table+" set activo=true where id=?",entity);
        }
    }
    @Test void vencimientoYCondicionesComercialesSeValidan() throws Exception {
        User u=user("VENTAS_PEDIDOS_CONFIRMAR"); Fixture f=fixture(u,new BigDecimal("10"),BigDecimal.ZERO);
        db.update("update cotizaciones set vigencia_hasta=? where id=?",today().minusDays(1),f.quote());
        assertThat(confirm(u,f.order())).isEqualTo(409);
        db.update("update cotizaciones set vigencia_hasta=? where id=?",today().plusDays(1),f.quote());
        db.update("update detalles_pedido set precio_unitario=11 where pedido_id=?",f.order());
        assertThat(confirm(u,f.order())).isEqualTo(409);
        db.update("update detalles_pedido set precio_unitario=10 where pedido_id=?",f.order());
        db.update("update pedidos_venta set total=99 where id=?",f.order());
        assertThat(confirm(u,f.order())).isEqualTo(409);
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("0");
    }
    long crearAjuste(User creator,User approver,Fixture f,Map<String,Object> line) throws Exception {
        var body=Map.of("almacenId",f.warehouse(),"motivo","Prueba","claveIdempotencia",key(),"detalles",List.of(line));
        String result=http.perform(post("/api/v1/inventory/adjustments").header("Authorization","Bearer "+creator.token()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long adjustment=json.readTree(result).get("id").asLong();
        http.perform(post("/api/v1/inventory/adjustments/{id}/approve",adjustment).header("Authorization","Bearer "+approver.token())).andExpect(status().isOk());
        return adjustment;
    }
    @Test void ajusteNegativoYReservaConcurrentesNoViolanDisponibilidad() throws Exception {
        User seller=user("VENTAS_PEDIDOS_CONFIRMAR"), creator=user("INVENTARIO_AJUSTES_REGISTRAR"), approver=user("INVENTARIO_AJUSTES_APROBAR"), applier=user("INVENTARIO_AJUSTES_APLICAR");
        Fixture f=fixture(seller,new BigDecimal("4"),BigDecimal.ZERO);
        long adjustment=crearAjuste(creator,approver,f,Map.of("productoId",f.product(),"tipo","NEGATIVO","cantidad",2));
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{gate.await();return confirm(seller,f.order());});
            var b=pool.submit(()->{gate.await();return http.perform(post("/api/v1/inventory/adjustments/{id}/apply",adjustment).header("Authorization","Bearer "+applier.token())).andReturn().getResponse().getStatus();});
            gate.countDown(); assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally {pool.shutdownNow();}
        assertThat(db.queryForObject("select cantidad_fisica-cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product()).signum()).isNotNegative();
    }
    @Test void reservaInvalidaFotografiaDeConteoParaAjuste() throws Exception {
        User seller=user("VENTAS_PEDIDOS_CONFIRMAR"), creator=user("INVENTARIO_CONTEOS_ADMIN","INVENTARIO_AJUSTES_REGISTRAR"), approver=user("INVENTARIO_AJUSTES_APROBAR"), applier=user("INVENTARIO_AJUSTES_APLICAR");
        Fixture f=fixture(seller,new BigDecimal("10"),BigDecimal.ZERO);
        var body=Map.of("almacenId",f.warehouse(),"fechaProgramada",today().toString(),"clase","CICLICO");
        String created=http.perform(post("/api/v1/inventory/counts").header("Authorization","Bearer "+creator.token()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long count=json.readTree(created).get("id").asLong();
        String captured=http.perform(put("/api/v1/inventory/counts/{id}/lines/{productoId}",count,f.product()).header("Authorization","Bearer "+creator.token()).contentType("application/json").content("{\"cantidadContada\":11}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long detail=json.readTree(captured).get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/inventory/counts/{id}/close",count).header("Authorization","Bearer "+creator.token())).andExpect(status().isOk());
        long adjustment=crearAjuste(creator,approver,f,Map.of("productoId",f.product(),"tipo","POSITIVO","cantidad",1,"costoUnitario",10,"detalleConteoId",detail));
        assertThat(confirm(seller,f.order())).isEqualTo(200);
        http.perform(post("/api/v1/inventory/adjustments/{id}/apply",adjustment).header("Authorization","Bearer "+applier.token())).andExpect(status().isConflict());
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("10");
    }
    @Test void recepcionYReservaConcurrentesConservanExistencia() throws Exception {
        User seller=user("VENTAS_PEDIDOS_CONFIRMAR"), receiver=user("COMPRAS_RECEPCIONES_CONFIRMAR");
        Fixture f=fixture(seller,new BigDecimal("2"),BigDecimal.ZERO);String k=key();
        long supplier=id("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id",k,"Proveedor");
        long department=id("insert into departamentos(codigo,nombre) values (?,?) returning id",k,"Departamento");
        long purchase=id("insert into ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values (?,?,?,?,'ENVIADA') returning id",k,supplier,department,receiver.id());
        long line=id("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,1,10,0) returning id",purchase,f.product());
        long receipt=id("insert into recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,'PENDIENTE') returning id",k,purchase,f.warehouse(),receiver.id(),k);
        db.update("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,1,1,0,10)",receipt,line);
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{gate.await();return confirm(seller,f.order());});
            var b=pool.submit(()->{gate.await();return http.perform(post("/api/v1/purchase-receipts/{id}/confirm",receipt).header("Authorization","Bearer "+receiver.token())).andReturn().getResponse().getStatus();});
            gate.countDown();int reservation=a.get(25,TimeUnit.SECONDS), reception=b.get(25,TimeUnit.SECONDS);
            assertThat(reception).isEqualTo(200);assertThat(reservation).isIn(200,409);
            assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("3");
            assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo(reservation==200?"3":"0");
        } finally {pool.shutdownNow();}
    }
}
