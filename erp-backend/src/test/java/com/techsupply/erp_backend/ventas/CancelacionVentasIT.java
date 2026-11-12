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
import org.springframework.dao.DataIntegrityViolationException;
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
class CancelacionVentasIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db; @Autowired MockMvc http; @Autowired PasswordEncoder passwords; @Autowired ObjectMapper json;
    @Autowired InventarioService inventory; @Autowired TransactionTemplate tx;
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
    long dispatch(User user,Fixture f,String quantity) throws Exception {
        String body=http.perform(post("/api/v1/sales/dispatches").header("Authorization","Bearer "+user.token()).contentType("application/json").content(payload(f,key(),quantity)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return json.readTree(body).get("id").asLong();
    }
    int confirm(User user,long dispatch) throws Exception {return http.perform(post("/api/v1/sales/dispatches/{id}/confirm",dispatch).header("Authorization","Bearer "+user.token())).andReturn().getResponse().getStatus();}
    int cancelOrder(User user,long order,String reason) throws Exception {
        return http.perform(post("/api/v1/sales/orders/{id}/cancel",order).header("Authorization","Bearer "+user.token()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("motivo",reason)))).andReturn().getResponse().getStatus();
    }
    int cancelDispatch(User user,long dispatch) throws Exception {
        return http.perform(post("/api/v1/sales/dispatches/{id}/cancel",dispatch).header("Authorization","Bearer "+user.token())).andReturn().getResponse().getStatus();
    }
    @Test void cancelaBorradorSinTocarInventarioYRegistraMotivo() throws Exception {
        User u=user("VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"BORRADOR");
        db.update("update detalles_pedido set cantidad_reservada=0 where id=?",f.line());
        db.update("update existencias set cantidad_reservada=0 where producto_id=?",f.product());
        http.perform(post("/api/v1/sales/orders/{id}/cancel",f.order()).header("Authorization","Bearer "+u.token()).contentType("application/json").content("{\"motivo\":\"  Cliente desistió  \"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CANCELADO")).andExpect(jsonPath("$.motivoCancelacion").value("Cliente desistió"))
            .andExpect(jsonPath("$.canceladoPorUsuarioId").value(u.id())).andExpect(jsonPath("$.canceladoEn").isString());
        assertThat(cancelOrder(u,f.order(),"Cliente desistió")).isEqualTo(200);
        User other=user("VENTAS_PEDIDOS_CANCELAR");
        assertThat(cancelOrder(other,f.order(),"Cliente desistió")).isEqualTo(200);
        assertThat(db.queryForObject("select cancelado_por_usuario_id from pedidos_venta where id=?",Long.class,f.order())).isEqualTo(u.id());
        assertThat(cancelOrder(u,f.order(),"Otro motivo")).isEqualTo(409);
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='SALES_ORDER_CANCELLED' and entidad_id=?",Integer.class,f.order())).isEqualTo(1);
        assertThat(db.queryForObject("select detalle->>'estadoAnterior' from eventos_auditoria where accion='SALES_ORDER_CANCELLED' and entidad_id=?",String.class,f.order())).isEqualTo("BORRADOR");
        assertThat(db.queryForObject("select detalle::text from eventos_auditoria where accion='SALES_ORDER_CANCELLED' and entidad_id=?",String.class,f.order())).doesNotContain("Cliente desistió");
    }
    @Test void cancelaPedidoConfirmadoLiberaSoloSuReservaInclusoSiCatalogoInactivo() throws Exception {
        User u=user("VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");
        db.update("update existencias set cantidad_reservada=7 where producto_id=?",f.product());
        db.update("update productos set activo=false where id=?",f.product());db.update("update almacenes set activo=false where id=?",f.warehouse());
        assertThat(cancelOrder(u,f.order(),"Cancelar venta")).isEqualTo(200);
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("10");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("2");
        assertThat(db.queryForObject("select cantidad_reservada from detalles_pedido where id=?",BigDecimal.class,f.line())).isEqualByComparingTo("0");
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isEqualTo(1);
        assertThatThrownBy(()->db.update("update detalles_pedido set cantidad=6 where id=?",f.line())).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("update pedidos_venta set motivo_cancelacion='Cambiado' where id=?",f.order())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(cancelOrder(u,f.order(),"Cancelar venta")).isEqualTo(200);
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isEqualTo(1);
    }
    @Test void parcialConservaDespachoYLiberaSoloSaldo() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");
        long d=dispatch(u,f,"3");assertThat(confirm(u,d)).isEqualTo(200);assertThat(cancelOrder(u,f.order(),"Saldo pendiente")).isEqualTo(200);
        assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("CANCELADO");
        assertThat(db.queryForObject("select estado from despachos where id=?",String.class,d)).isEqualTo("CONFIRMADO");
        assertThat(db.queryForObject("select cantidad_despachada from detalles_pedido where id=?",BigDecimal.class,f.line())).isEqualByComparingTo("3");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("0");
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("7");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=? and motivo='DESPACHO_VENTA'",Integer.class,f.product())).isEqualTo(1);
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isEqualTo(2);
        assertThat(db.queryForObject("select detalle->>'estadoAnterior' from eventos_auditoria where accion='SALES_ORDER_CANCELLED' and entidad_id=?",String.class,f.order())).isEqualTo("PARCIALMENTE_DESPACHADO");
        assertThat(cancelDispatch(u,d)).isEqualTo(409);
    }
    @Test void borradorBloqueaCancelacionPeroPuedeCancelarseYReasignarse() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");long d=dispatch(u,f,"5");
        assertThat(cancelOrder(u,f.order(),"Fin")).isEqualTo(409);
        assertThat(cancelDispatch(u,d)).isEqualTo(200);assertThat(cancelDispatch(u,d)).isEqualTo(200);
        assertThatThrownBy(()->db.update("update despachos set estado='BORRADOR' where id=?",d)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("update detalles_despacho set cantidad=4 where despacho_id=?",d)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("insert into detalles_despacho(despacho_id,detalle_pedido_id,cantidad) values (?,?,1)",d,f.line())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(confirm(u,d)).isEqualTo(409);
        long replacement=dispatch(u,f,"5");assertThat(cancelDispatch(u,replacement)).isEqualTo(200);
        assertThat(cancelOrder(u,f.order(),"Fin")).isEqualTo(200);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='SALES_DISPATCH_CANCELLED' and entidad_id=?",Integer.class,d)).isEqualTo(1);
    }
    @Test void noCancelaPedidoCompletamenteDespachado() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");
        long d=dispatch(u,f,"5");assertThat(confirm(u,d)).isEqualTo(200);assertThat(cancelOrder(u,f.order(),"Fin")).isEqualTo(409);
        assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("DESPACHADO");
    }
    @Test void autorizacionMotivoYCancelacionHistorica() throws Exception {
        User u=user(),allowed=user("VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");
        http.perform(post("/api/v1/sales/orders/{id}/cancel",f.order()).contentType("application/json").content("{\"motivo\":\"X\"}")).andExpect(status().isUnauthorized());
        assertThat(cancelOrder(u,f.order(),"X")).isEqualTo(403);assertThat(cancelOrder(allowed,Long.MAX_VALUE,"X")).isEqualTo(404);
        assertThat(cancelOrder(allowed,f.order(),"   ")).isEqualTo(400);
        db.update("update pedidos_venta set estado='CANCELADO' where id=?",f.order());
        assertThat(cancelOrder(allowed,f.order(),"X")).isEqualTo(409);
    }
    @Test void falloAuditoriaRevierteLiberacionYDespachoCancelado() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");long d=dispatch(u,f,"3");
        db.execute("alter table eventos_auditoria add constraint ck_test_cancel_dispatch_failure check (accion <> 'SALES_DISPATCH_CANCELLED') not valid");
        try {assertThat(cancelDispatch(u,d)).isEqualTo(409);assertThat(db.queryForObject("select estado from despachos where id=?",String.class,d)).isEqualTo("BORRADOR");}
        finally {db.execute("alter table eventos_auditoria drop constraint ck_test_cancel_dispatch_failure");}
        assertThat(cancelDispatch(u,d)).isEqualTo(200);
        db.execute("alter table eventos_auditoria add constraint ck_test_cancel_order_failure check (accion <> 'SALES_ORDER_CANCELLED') not valid");
        try {assertThat(cancelOrder(u,f.order(),"Fin")).isEqualTo(409);
            assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("5");
            assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isZero();
            assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("CONFIRMADO");
        } finally {db.execute("alter table eventos_auditoria drop constraint ck_test_cancel_order_failure");}
        assertThat(cancelOrder(u,f.order(),"Fin")).isEqualTo(200);
    }
    @Test void carreraEntreCancelacionYConfirmacionDeDespachoSeSerializa() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR");Fixture f=fixture(u,"CONFIRMADO");long d=dispatch(u,f,"3");
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {var a=pool.submit(()->{gate.await();return confirm(u,d);});var b=pool.submit(()->{gate.await();return cancelDispatch(u,d);});gate.countDown();
            assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally {pool.shutdownNow();}
        String state=db.queryForObject("select estado from despachos where id=?",String.class,d);
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isEqualTo(state.equals("CONFIRMADO")?1:0);
    }
    @Test void carreraEntreCancelacionPedidoYDespachoConservaHistorial() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");long d=dispatch(u,f,"3");
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {var a=pool.submit(()->{gate.await();return confirm(u,d);});var b=pool.submit(()->{gate.await();return cancelOrder(u,f.order(),"Fin");});gate.countDown();
            assertThat(a.get(25,TimeUnit.SECONDS)).isEqualTo(200);assertThat(b.get(25,TimeUnit.SECONDS)).isIn(200,409);
        } finally {pool.shutdownNow();}
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=? and motivo='DESPACHO_VENTA'",Integer.class,f.product())).isEqualTo(1);
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("7");
        String state=db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order());
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo(state.equals("CANCELADO")?"0":"2");
    }
    @Test void llamadaInternaNoLiberaReservaSinCancelarPedido() throws Exception {
        User u=user("VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new SecurityActor(u.id(),"prueba"),null,
            List.of(new SimpleGrantedAuthority("VENTAS_PEDIDOS_CANCELAR"))));
        try {assertThatThrownBy(()->tx.executeWithoutResult(status->inventory.liberarReservaPedido(f.line()))).isInstanceOf(IllegalStateException.class);}
        finally {SecurityContextHolder.clearContext();}
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("5");
    }
    @Test void falloEnSegundaLineaReviertePrimeraLiberacion() throws Exception {
        User u=user("VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");
        long category=id("select categoria_id from productos where id=?",f.product());
        long product=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key(),"Segundo",category);
        long line=id("insert into detalles_pedido(pedido_id,producto_id,cantidad,cantidad_reservada,precio_unitario,tasa_impuesto) values (?,?,2,2,10,16) returning id",f.order(),product);
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada) values (?,?,2,1)",product,f.warehouse());
        assertThat(cancelOrder(u,f.order(),"Fin")).isEqualTo(409);
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("5");
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select cantidad_reservada from detalles_pedido where id=?",BigDecimal.class,line)).isEqualByComparingTo("2");
        assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("CONFIRMADO");
    }
    @Test void dosCancelacionesConcurrentesConMismoMotivoLiberanUnaVez() throws Exception {
        User u=user("VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {Callable<Integer> call=()->{gate.await();return cancelOrder(u,f.order(),"Fin");};var a=pool.submit(call);var b=pool.submit(call);gate.countDown();
            assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally {pool.shutdownNow();}
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='SALES_ORDER_CANCELLED' and entidad_id=?",Integer.class,f.order())).isEqualTo(1);
    }
    @Test void crearDespachoYCancelarPedidoSeSerializan() throws Exception {
        User u=user("VENTAS_PEDIDOS_CANCELAR","VENTAS_DESPACHOS_REGISTRAR");Fixture f=fixture(u,"CONFIRMADO");String body=payload(f,key(),"3");
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{gate.await();return create(u,body);});
            var b=pool.submit(()->{gate.await();return cancelOrder(u,f.order(),"Fin");});gate.countDown();
            int created=a.get(25,TimeUnit.SECONDS),cancelled=b.get(25,TimeUnit.SECONDS);
            assertThat(created==201 && cancelled==409 || created==409 && cancelled==200).isTrue();
        } finally {pool.shutdownNow();}
        String state=db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order());
        assertThat(db.queryForObject("select count(*) from despachos where pedido_id=? and estado='BORRADOR'",Integer.class,f.order())).isEqualTo(state.equals("CANCELADO")?0:1);
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo(state.equals("CANCELADO")?"0":"5");
    }
    @Test void autorizacionParaCancelarDespacho() throws Exception {
        User registrar=user("VENTAS_DESPACHOS_REGISTRAR"),denied=user();Fixture f=fixture(registrar,"CONFIRMADO");long dispatch=dispatch(registrar,f,"3");
        http.perform(post("/api/v1/sales/dispatches/{id}/cancel",dispatch)).andExpect(status().isUnauthorized());
        assertThat(cancelDispatch(denied,dispatch)).isEqualTo(403);
        assertThat(cancelDispatch(registrar,Long.MAX_VALUE)).isEqualTo(404);
        assertThat(db.queryForObject("select estado from despachos where id=?",String.class,dispatch)).isEqualTo("BORRADOR");
    }
    @Test void versionAgotadaNoLiberaReservaNiCancela() throws Exception {
        User u=user("VENTAS_PEDIDOS_CANCELAR");Fixture f=fixture(u,"CONFIRMADO");
        db.update("update existencias set version=2147483647 where producto_id=?",f.product());
        assertThat(cancelOrder(u,f.order(),"Fin")).isEqualTo(409);
        assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("CONFIRMADO");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("5");
    }
    @Test void cancelarPedidoInvalidaConteoAnteriorParaAjuste() throws Exception {
        User seller=user("VENTAS_PEDIDOS_CANCELAR"),creator=user("INVENTARIO_CONTEOS_ADMIN","INVENTARIO_AJUSTES_REGISTRAR"),
            approver=user("INVENTARIO_AJUSTES_APROBAR"),applier=user("INVENTARIO_AJUSTES_APLICAR");Fixture f=fixture(seller,"CONFIRMADO");
        var countRequest=Map.of("almacenId",f.warehouse(),"fechaProgramada",LocalDate.now(ZoneId.of("America/Mexico_City")).toString(),"clase","CICLICO");
        String created=http.perform(post("/api/v1/inventory/counts").header("Authorization","Bearer "+creator.token()).contentType("application/json").content(json.writeValueAsString(countRequest)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();long count=json.readTree(created).get("id").asLong();
        String captured=http.perform(put("/api/v1/inventory/counts/{id}/lines/{productoId}",count,f.product()).header("Authorization","Bearer "+creator.token()).contentType("application/json").content("{\"cantidadContada\":11}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();long detail=json.readTree(captured).get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/inventory/counts/{id}/close",count).header("Authorization","Bearer "+creator.token())).andExpect(status().isOk());
        var adjustmentRequest=Map.of("almacenId",f.warehouse(),"motivo","Prueba","claveIdempotencia",key(),"detalles",
            List.of(Map.of("productoId",f.product(),"tipo","POSITIVO","cantidad",1,"costoUnitario",10,"detalleConteoId",detail)));
        String adjustmentBody=http.perform(post("/api/v1/inventory/adjustments").header("Authorization","Bearer "+creator.token()).contentType("application/json").content(json.writeValueAsString(adjustmentRequest)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();long adjustment=json.readTree(adjustmentBody).get("id").asLong();
        http.perform(post("/api/v1/inventory/adjustments/{id}/approve",adjustment).header("Authorization","Bearer "+approver.token())).andExpect(status().isOk());
        assertThat(cancelOrder(seller,f.order(),"Fin")).isEqualTo(200);
        http.perform(post("/api/v1/inventory/adjustments/{id}/apply",adjustment).header("Authorization","Bearer "+applier.token())).andExpect(status().isConflict());
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("10");
    }
}
