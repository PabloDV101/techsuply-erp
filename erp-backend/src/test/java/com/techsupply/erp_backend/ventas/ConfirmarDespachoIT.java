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
class ConfirmarDespachoIT extends PostgresIntegrationSupport {
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
    @Test void despachoParcialYTotalConCostoYFuenteEnKardex() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","INVENTARIO_MOVIMIENTOS_LEER");Fixture f=fixture(u,"CONFIRMADO");
        long first=dispatch(u,f,"3"),second=dispatch(u,f,"2");
        http.perform(post("/api/v1/sales/dispatches/{id}/confirm",first).header("Authorization","Bearer "+u.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADO")).andExpect(jsonPath("$.detalles[0].costoUnitario").value(7));
        assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("PARCIALMENTE_DESPACHADO");
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("7");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("2");
        assertThat(db.queryForObject("select cantidad_despachada from detalles_pedido where id=?",BigDecimal.class,f.line())).isEqualByComparingTo("3");
        long detail=id("select id from detalles_despacho where despacho_id=?",first);
        http.perform(get("/api/v1/inventory/movements").header("Authorization","Bearer "+u.token()).param("productoId",Long.toString(f.product())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].detalleDespachoId").value(detail)).andExpect(jsonPath("$.content[0].motivo").value("DESPACHO_VENTA"));
        assertThat(confirm(u,first)).isEqualTo(200);
        db.update("update existencias set costo_promedio=9,version=version+1 where producto_id=?",f.product());
        assertThat(confirm(u,second)).isEqualTo(200);
        assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("DESPACHADO");
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("5");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("0");
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isEqualTo(3);
        assertThat(db.queryForObject("select costo_unitario from detalles_despacho where despacho_id=?",BigDecimal.class,first)).isEqualByComparingTo("7");
        assertThat(db.queryForObject("select costo_unitario from detalles_despacho where despacho_id=?",BigDecimal.class,second)).isEqualByComparingTo("9");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=? and motivo='DESPACHO_VENTA'",Integer.class,f.product())).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='SALES_DISPATCH_CONFIRMED' and entidad_id=?",Integer.class,first)).isEqualTo(1);
    }
    @Test void confirmacionesConcurrentesSonIdempotentes() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR");Fixture f=fixture(u,"CONFIRMADO");long dispatch=dispatch(u,f,"3");
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {Callable<Integer> call=()->{gate.await();return confirm(u,dispatch);};var a=pool.submit(call);var b=pool.submit(call);gate.countDown();
            assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally {pool.shutdownNow();}
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isEqualTo(1);
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("2");
    }
    @Test void criterioCa03CuatroDeDiezUnidades() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR");Fixture f=fixture(u,"CONFIRMADO");
        db.update("update detalles_pedido set cantidad=10,cantidad_reservada=10 where id=?",f.line());
        db.update("update existencias set cantidad_reservada=10 where producto_id=?",f.product());
        long dispatch=dispatch(u,f,"4");assertThat(confirm(u,dispatch)).isEqualTo(200);
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("6");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("6");
        assertThat(db.queryForObject("select cantidad_despachada from detalles_pedido where id=?",BigDecimal.class,f.line())).isEqualByComparingTo("4");
        assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("PARCIALMENTE_DESPACHADO");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=? and cantidad=4 and motivo='DESPACHO_VENTA'",Integer.class,f.product())).isEqualTo(1);
    }
    @Test void conservaCostoMaximoYEscalaDeCuatroDecimales() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR");Fixture f=fixture(u,"CONFIRMADO");
        db.update("update existencias set costo_promedio=999999999999.9999,version=version+1 where producto_id=?",f.product());
        long dispatch=dispatch(u,f,"1");assertThat(confirm(u,dispatch)).isEqualTo(200);
        assertThat(db.queryForObject("select costo_unitario from detalles_despacho where despacho_id=?",BigDecimal.class,dispatch)).isEqualByComparingTo("999999999999.9999");
        assertThat(db.queryForObject("select costo_unitario from movimientos_inventario where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("999999999999.9999");
    }
    @Test void despachosDistintosConcurrentesActualizanTodoSinPerdidas() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR");Fixture f=fixture(u,"CONFIRMADO");long first=dispatch(u,f,"3"),second=dispatch(u,f,"2");
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {var a=pool.submit(()->{gate.await();return confirm(u,first);});var b=pool.submit(()->{gate.await();return confirm(u,second);});gate.countDown();
            assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally {pool.shutdownNow();}
        assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("DESPACHADO");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isEqualTo(2);
    }
    @Test void auditoriaFallidaRevierteCostoStockYEstado() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR");Fixture f=fixture(u,"CONFIRMADO");long dispatch=dispatch(u,f,"3");
        db.execute("alter table eventos_auditoria add constraint ck_test_confirm_dispatch_failure check (accion <> 'SALES_DISPATCH_CONFIRMED') not valid");
        try {assertThat(confirm(u,dispatch)).isEqualTo(409);
            assertThat(db.queryForObject("select estado from despachos where id=?",String.class,dispatch)).isEqualTo("BORRADOR");
            assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,f.order())).isEqualTo("CONFIRMADO");
            assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("10");
            assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("5");
            assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isZero();
            assertThat(db.queryForObject("select count(*) from detalles_despacho where despacho_id=? and costo_unitario is not null",Integer.class,dispatch)).isZero();
        } finally {db.execute("alter table eventos_auditoria drop constraint ck_test_confirm_dispatch_failure");}
        assertThat(confirm(u,dispatch)).isEqualTo(200);
    }
    @Test void referenciasYPermisosImpedirSalida() throws Exception {
        User registrar=user("VENTAS_DESPACHOS_REGISTRAR"), confirmer=user("VENTAS_DESPACHOS_CONFIRMAR");Fixture f=fixture(registrar,"CONFIRMADO");long dispatch=dispatch(registrar,f,"3");
        http.perform(post("/api/v1/sales/dispatches/{id}/confirm",dispatch)).andExpect(status().isUnauthorized());
        assertThat(confirm(registrar,dispatch)).isEqualTo(403); assertThat(confirm(confirmer,Long.MAX_VALUE)).isEqualTo(404);
        db.update("update productos set activo=false where id=?",f.product());assertThat(confirm(confirmer,dispatch)).isEqualTo(409);
        db.update("update productos set activo=true where id=?",f.product());
        db.update("update detalles_pedido set cantidad_reservada=2 where id=?",f.line());assertThat(confirm(confirmer,dispatch)).isEqualTo(409);
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isZero();
    }
    @Test void inventarioNoPermiteSalidaSinConfirmarElDespacho() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR");Fixture f=fixture(u,"CONFIRMADO");long dispatch=dispatch(u,f,"3");
        long detail=id("select id from detalles_despacho where despacho_id=?",dispatch);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new SecurityActor(u.id(),"prueba"),null,
            List.of(new SimpleGrantedAuthority("VENTAS_DESPACHOS_CONFIRMAR"))));
        try {assertThatThrownBy(()->tx.executeWithoutResult(status->inventory.registrarSalidaDespacho(detail))).isInstanceOf(IllegalStateException.class);}
        finally {SecurityContextHolder.clearContext();}
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("10");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isZero();
    }
    @Test void fallaEnSegundaLineaRevierteLaPrimera() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR");Fixture f=fixture(u,"CONFIRMADO");
        long category=id("select categoria_id from productos where id=?",f.product());
        long product=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key(),"Segundo",category);
        long line=id("insert into detalles_pedido(pedido_id,producto_id,cantidad,cantidad_reservada,precio_unitario,tasa_impuesto) values (?,?,2,2,10,16) returning id",f.order(),product);
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada,costo_promedio) values (?,?,2,2,7)",product,f.warehouse());
        var body=Map.of("pedidoId",f.order(),"claveIdempotencia",key(),"detalles",List.of(Map.of("detallePedidoId",f.line(),"cantidad",3),Map.of("detallePedidoId",line,"cantidad",2)));
        String response=http.perform(post("/api/v1/sales/dispatches").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();long dispatch=json.readTree(response).get("id").asLong();
        db.update("update productos set activo=false where id=?",product);
        assertThat(confirm(u,dispatch)).isEqualTo(409);
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("10");
        assertThat(db.queryForObject("select version from existencias where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id in (?,?)",Integer.class,f.product(),product)).isZero();
        assertThat(db.queryForObject("select estado from despachos where id=?",String.class,dispatch)).isEqualTo("BORRADOR");
    }
    @Test void recepcionYDespachoConcurrentesConservanStockYCostos() throws Exception {
        User seller=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR"),receiver=user("COMPRAS_RECEPCIONES_CONFIRMAR");
        Fixture f=fixture(seller,"CONFIRMADO");long dispatch=dispatch(seller,f,"3");String k=key();
        long supplier=id("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id",k,"Proveedor");
        long department=id("insert into departamentos(codigo,nombre) values (?,?) returning id",k,"Departamento");
        long purchase=id("insert into ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values (?,?,?,?,'ENVIADA') returning id",k,supplier,department,receiver.id());
        long line=id("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,1,10,0) returning id",purchase,f.product());
        long receipt=id("insert into recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,'PENDIENTE') returning id",k,purchase,f.warehouse(),receiver.id(),k);
        db.update("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,1,1,0,10)",receipt,line);
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{gate.await();return confirm(seller,dispatch);});
            var b=pool.submit(()->{gate.await();return http.perform(post("/api/v1/purchase-receipts/{id}/confirm",receipt).header("Authorization","Bearer "+receiver.token())).andReturn().getResponse().getStatus();});
            gate.countDown();assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally {pool.shutdownNow();}
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("8");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("2");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isEqualTo(2);
        assertThat(db.queryForObject("select costo_unitario from detalles_despacho where despacho_id=?",BigDecimal.class,dispatch))
            .isIn(new BigDecimal("7.0000"),new BigDecimal("7.2727"));
    }
    long crearAjuste(User creator,User approver,Fixture f,Map<String,Object> line) throws Exception {
        var body=Map.of("almacenId",f.warehouse(),"motivo","Prueba","claveIdempotencia",key(),"detalles",List.of(line));
        String response=http.perform(post("/api/v1/inventory/adjustments").header("Authorization","Bearer "+creator.token()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();long adjustment=json.readTree(response).get("id").asLong();
        http.perform(post("/api/v1/inventory/adjustments/{id}/approve",adjustment).header("Authorization","Bearer "+approver.token())).andExpect(status().isOk());
        return adjustment;
    }
    @Test void ajusteNegativoYDespachoConcurrentesConservanCantidades() throws Exception {
        User seller=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR"),creator=user("INVENTARIO_AJUSTES_REGISTRAR"),
            approver=user("INVENTARIO_AJUSTES_APROBAR"),applier=user("INVENTARIO_AJUSTES_APLICAR");
        Fixture f=fixture(seller,"CONFIRMADO");long dispatch=dispatch(seller,f,"3");
        long adjustment=crearAjuste(creator,approver,f,Map.of("productoId",f.product(),"tipo","NEGATIVO","cantidad",5));
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{gate.await();return confirm(seller,dispatch);});
            var b=pool.submit(()->{gate.await();return http.perform(post("/api/v1/inventory/adjustments/{id}/apply",adjustment).header("Authorization","Bearer "+applier.token())).andReturn().getResponse().getStatus();});
            gate.countDown();assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally {pool.shutdownNow();}
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("2");
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("2");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isEqualTo(2);
    }
    @Test void despachoInvalidaConteoAnteriorParaAjuste() throws Exception {
        User seller=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR"),creator=user("INVENTARIO_CONTEOS_ADMIN","INVENTARIO_AJUSTES_REGISTRAR"),
            approver=user("INVENTARIO_AJUSTES_APROBAR"),applier=user("INVENTARIO_AJUSTES_APLICAR");
        Fixture f=fixture(seller,"CONFIRMADO");long dispatch=dispatch(seller,f,"3");
        var body=Map.of("almacenId",f.warehouse(),"fechaProgramada",LocalDate.now(ZoneId.of("America/Mexico_City")).toString(),"clase","CICLICO");
        String created=http.perform(post("/api/v1/inventory/counts").header("Authorization","Bearer "+creator.token()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();long count=json.readTree(created).get("id").asLong();
        String captured=http.perform(put("/api/v1/inventory/counts/{id}/lines/{productoId}",count,f.product()).header("Authorization","Bearer "+creator.token()).contentType("application/json").content("{\"cantidadContada\":11}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();long detail=json.readTree(captured).get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/inventory/counts/{id}/close",count).header("Authorization","Bearer "+creator.token())).andExpect(status().isOk());
        long adjustment=crearAjuste(creator,approver,f,Map.of("productoId",f.product(),"tipo","POSITIVO","cantidad",1,"costoUnitario",10,"detalleConteoId",detail));
        assertThat(confirm(seller,dispatch)).isEqualTo(200);
        http.perform(post("/api/v1/inventory/adjustments/{id}/apply",adjustment).header("Authorization","Bearer "+applier.token())).andExpect(status().isConflict());
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("7");
    }
    @Test void recorridoCompletoDesdeCotizacionHastaKardex() throws Exception {
        User u=user("VENTAS_COTIZACIONES_REGISTRAR","VENTAS_COTIZACIONES_ADMIN","VENTAS_PEDIDOS_REGISTRAR","VENTAS_PEDIDOS_CONFIRMAR",
            "VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","INVENTARIO_MOVIMIENTOS_LEER");
        Fixture f=fixture(u,"CONFIRMADO");long customer=id("select cliente_id from pedidos_venta where id=?",f.order());
        var quotePayload=Map.of("clienteId",customer,"vigenciaHasta",LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(1).toString(),"claveIdempotencia",key(),
            "detalles",List.of(Map.of("productoId",f.product(),"cantidad",3,"precioUnitario",10,"descuentoPct",10,"tasaImpuesto",16)));
        String quoteBody=http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(quotePayload)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();long quote=json.readTree(quoteBody).get("id").asLong();
        http.perform(post("/api/v1/sales/quotes/{id}/issue",quote).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        http.perform(post("/api/v1/sales/quotes/{id}/accept",quote).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        var orderPayload=Map.of("cotizacionId",quote,"almacenId",f.warehouse(),"modalidad","CONTADO","claveIdempotencia",key());
        String orderBody=http.perform(post("/api/v1/sales/orders").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(orderPayload)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long order=json.readTree(orderBody).get("id").asLong(),line=json.readTree(orderBody).get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/sales/orders/{id}/confirm",order).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        var payload=Map.of("pedidoId",order,"claveIdempotencia",key(),"detalles",List.of(Map.of("detallePedidoId",line,"cantidad",2)));
        String dispatchBody=http.perform(post("/api/v1/sales/dispatches").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(payload)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();long dispatch=json.readTree(dispatchBody).get("id").asLong();
        assertThat(confirm(u,dispatch)).isEqualTo(200);
        assertThat(db.queryForObject("select estado from pedidos_venta where id=?",String.class,order)).isEqualTo("PARCIALMENTE_DESPACHADO");
        http.perform(get("/api/v1/inventory/movements").header("Authorization","Bearer "+u.token()).param("productoId",Long.toString(f.product())))
            .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].motivo").value("DESPACHO_VENTA"));
    }
}
