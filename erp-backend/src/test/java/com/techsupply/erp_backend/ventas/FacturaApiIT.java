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
class FacturaApiIT extends PostgresIntegrationSupport {
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
    void complete(User user,Fixture f) throws Exception {
        db.update("update pedidos_venta set subtotal=50,impuestos=8,total=58 where id=?",f.order());
        long d=dispatch(user,f,"5");assertThat(confirm(user,d)).isEqualTo(200);
    }
    String invoiceBody(Fixture f,String key) throws Exception {return json.writeValueAsString(Map.of("pedidoId",f.order(),"claveIdempotencia",key));}
    int invoice(User user,String body) throws Exception {return http.perform(post("/api/v1/sales/invoices").header("Authorization","Bearer "+user.token()).contentType("application/json").content(body)).andReturn().getResponse().getStatus();}
    @Test void emiteConsultaYConservaSnapshotSinCobroNiCambiarStock() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_FACTURAS_EMITIR","VENTAS_FACTURAS_LEER");Fixture f=fixture(u,"CONFIRMADO");String k=key();
        assertThat(invoice(u,invoiceBody(f,k))).isEqualTo(409);complete(u,f);
        db.update("update productos set precio_lista=99,activo=false where id=?",f.product());
        db.update("update clientes set activo=false where id=(select cliente_id from pedidos_venta where id=?)",f.order());
        String result=http.perform(post("/api/v1/sales/invoices").header("Authorization","Bearer "+u.token()).contentType("application/json").content(invoiceBody(f,k)))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("$.estado").value("EMITIDA"))
            .andExpect(jsonPath("$.total").value(58)).andExpect(jsonPath("$.detalles[0].precioUnitario").value(10))
            .andReturn().getResponse().getContentAsString();
        var node=json.readTree(result);long id=node.get("id").asLong();
        assertThat(node.get("fechaVencimiento").asText()).isEqualTo(node.get("fechaEmision").asText());
        assertThat(invoice(u,invoiceBody(f,k))).isEqualTo(200);assertThat(invoice(u,invoiceBody(f,key()))).isEqualTo(409);
        assertThat(invoice(user("VENTAS_FACTURAS_EMITIR"),invoiceBody(f,k))).isEqualTo(409);
        http.perform(get("/api/v1/sales/invoices/{id}",id).header("Authorization","Bearer "+u.token())).andExpect(status().isOk()).andExpect(jsonPath("$.detalles[0].total").value(58));
        long customer=id("select cliente_id from pedidos_venta where id=?",f.order());
        http.perform(get("/api/v1/sales/invoices").header("Authorization","Bearer "+u.token()).param("clienteId",Long.toString(customer)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?",BigDecimal.class,f.product())).isEqualByComparingTo("5");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,f.product())).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='SALES_INVOICE_ISSUED' and entidad_id=?",Integer.class,id)).isEqualTo(1);
        assertThatThrownBy(()->db.update("update facturas_venta set total=0 where id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("delete from detalles_factura_venta where factura_venta_id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("delete from facturas_venta where id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("update detalles_factura_venta set precio_unitario=11 where factura_venta_id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("insert into detalles_factura_venta(factura_venta_id,detalle_pedido_id,producto_id,cantidad,precio_unitario,descuento_pct,tasa_impuesto,subtotal,impuestos,total) values (?,?,?,5,10,0,16,50,8,58)",id,f.line(),f.product())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void noFacturaEstadosNiDespachoSinKardex() throws Exception {
        User u=user("VENTAS_FACTURAS_EMITIR");Fixture f=fixture(u,"CONFIRMADO");
        db.update("update pedidos_venta set estado='DESPACHADO',subtotal=50,impuestos=8,total=58 where id=?",f.order());
        db.update("update detalles_pedido set cantidad_reservada=0,cantidad_despachada=5 where id=?",f.line());
        assertThat(invoice(u,invoiceBody(f,key()))).isEqualTo(409);
        assertThat(db.queryForObject("select count(*) from facturas_venta where pedido_id=?",Integer.class,f.order())).isZero();
        http.perform(get("/api/v1/sales/invoices")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/sales/invoices").header("Authorization","Bearer "+u.token())).andExpect(status().isForbidden());
        http.perform(post("/api/v1/sales/invoices").contentType("application/json").content(invoiceBody(f,key()))).andExpect(status().isUnauthorized());
        User denied=user();assertThat(invoice(denied,invoiceBody(f,key()))).isEqualTo(403);
        assertThat(invoice(u,"{\"pedidoId\":0,\"claveIdempotencia\":\"x\"}")).isEqualTo(400);
    }
    @Test void concurrenciaMismaClaveYOtraClaveNoDuplicanFactura() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_FACTURAS_EMITIR");Fixture f=fixture(u,"CONFIRMADO");complete(u,f);String body=invoiceBody(f,key());
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {Callable<Integer> call=()->{gate.await();return invoice(u,body);};var a=pool.submit(call);var b=pool.submit(call);gate.countDown();
            assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,200);
        } finally {pool.shutdownNow();}
        Fixture other=fixture(u,"CONFIRMADO");complete(u,other);var threads=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {Callable<Integer> call=()->{start.await();return invoice(u,invoiceBody(other,key()));};var a=threads.submit(call);var b=threads.submit(call);start.countDown();
            assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        } finally {threads.shutdownNow();}
        assertThat(db.queryForObject("select count(*) from facturas_venta where pedido_id=?",Integer.class,other.order())).isEqualTo(1);
    }
    @Test void auditoriaFallidaRevierteFacturaYPermiteReintento() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_FACTURAS_EMITIR");Fixture f=fixture(u,"CONFIRMADO");complete(u,f);String body=invoiceBody(f,key());
        db.execute("alter table eventos_auditoria add constraint ck_test_invoice_failure check (accion <> 'SALES_INVOICE_ISSUED') not valid");
        try {assertThat(invoice(u,body)).isEqualTo(409);assertThat(db.queryForObject("select count(*) from facturas_venta where pedido_id=?",Integer.class,f.order())).isZero();}
        finally {db.execute("alter table eventos_auditoria drop constraint ck_test_invoice_failure");}
        assertThat(invoice(u,body)).isEqualTo(201);
    }
    @Test void totalesDePedidoIncoherentesImpidenEmision() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_FACTURAS_EMITIR");Fixture f=fixture(u,"CONFIRMADO");complete(u,f);
        db.update("update pedidos_venta set total=57 where id=?",f.order());
        assertThat(invoice(u,invoiceBody(f,key()))).isEqualTo(409);
        assertThat(db.queryForObject("select count(*) from facturas_venta where pedido_id=?",Integer.class,f.order())).isZero();
    }
    @Test void borradorTransitorioNoPuedeConfirmarseSinEmitir() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR");Fixture f=fixture(u,"CONFIRMADO");complete(u,f);
        assertThatThrownBy(()->tx.executeWithoutResult(status->db.update("insert into facturas_venta(folio,pedido_id,cliente_id,emitida_por_usuario_id,fecha_emision,fecha_vencimiento,subtotal,impuestos,total,estado,clave_idempotencia) select ?,id,cliente_id,?,current_date,current_date,subtotal,impuestos,total,'BORRADOR',? from pedidos_venta where id=?",key(),u.id(),key(),f.order())))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(db.queryForObject("select count(*) from facturas_venta where pedido_id=?",Integer.class,f.order())).isZero();
    }
    @Test void variasLineasConservanRedondeoPorLinea() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_FACTURAS_EMITIR");Fixture f=fixture(u,"CONFIRMADO");
        long category=id("select categoria_id from productos where id=?",f.product());
        long product=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key(),"Segundo",category);
        long line=id("insert into detalles_pedido(pedido_id,producto_id,cantidad,cantidad_reservada,precio_unitario,tasa_impuesto) values (?,?,0.005,0.005,1,16) returning id",f.order(),product);
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada,costo_promedio) values (?,?,1,0.005,1)",product,f.warehouse());
        db.update("update pedidos_venta set subtotal=50.01,impuestos=8,total=58.01 where id=?",f.order());
        var body=Map.of("pedidoId",f.order(),"claveIdempotencia",key(),"detalles",List.of(Map.of("detallePedidoId",f.line(),"cantidad",5),Map.of("detallePedidoId",line,"cantidad",new BigDecimal("0.005"))));
        String result=http.perform(post("/api/v1/sales/dispatches").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();assertThat(confirm(u,json.readTree(result).get("id").asLong())).isEqualTo(200);
        http.perform(post("/api/v1/sales/invoices").header("Authorization","Bearer "+u.token()).contentType("application/json").content(invoiceBody(f,key())))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.subtotal").value(50.01)).andExpect(jsonPath("$.total").value(58.01))
            .andExpect(jsonPath("$.detalles[1].subtotal").value(0.01)).andExpect(jsonPath("$.detalles[1].impuestos").value(0));
    }
    @Test void carreraEntreDespachoFinalYEmisionNoFacturaAnticipadamente() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_FACTURAS_EMITIR");Fixture f=fixture(u,"CONFIRMADO");
        db.update("update pedidos_venta set subtotal=50,impuestos=8,total=58 where id=?",f.order());long d=dispatch(u,f,"5");String body=invoiceBody(f,key());
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        try {var a=pool.submit(()->{gate.await();return confirm(u,d);});var b=pool.submit(()->{gate.await();return invoice(u,body);});gate.countDown();
            assertThat(a.get(25,TimeUnit.SECONDS)).isEqualTo(200);int emitted=b.get(25,TimeUnit.SECONDS);assertThat(emitted).isIn(201,409);
            if(emitted==409)assertThat(invoice(u,body)).isEqualTo(201);
        } finally {pool.shutdownNow();}
        assertThat(db.queryForObject("select count(*) from facturas_venta where pedido_id=?",Integer.class,f.order())).isEqualTo(1);
    }
    @Test void flujoHttpDesdeCotizacionHastaFactura() throws Exception {
        User u=user("MAESTROS_CLIENTES_ADMIN","VENTAS_COTIZACIONES_REGISTRAR","VENTAS_COTIZACIONES_ADMIN","VENTAS_PEDIDOS_REGISTRAR","VENTAS_PEDIDOS_CONFIRMAR",
            "VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_FACTURAS_EMITIR");
        Fixture f=fixture(u,"CONFIRMADO");long customer=id("select cliente_id from pedidos_venta where id=?",f.order());
        String customerBody=json.writeValueAsString(Map.of("codigo",key(),"nombreRazonSocial","Cliente integrado",
            "limiteCredito",BigDecimal.ZERO,"plazoCreditoDias",0));
        String customerResponse=http.perform(post("/api/v1/customers").header("Authorization","Bearer "+u.token())
                .contentType("application/json").content(customerBody))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andReturn().getResponse().getContentAsString();
        long createdCustomer=json.readTree(customerResponse).get("id").asLong();
        assertThat(createdCustomer).isNotEqualTo(customer);
        var quoteRequest=Map.of("clienteId",createdCustomer,"vigenciaHasta",LocalDate.now(ZoneId.of("America/Mexico_City")).plusDays(1).toString(),"claveIdempotencia",key(),
            "detalles",List.of(Map.of("productoId",f.product(),"cantidad",3,"precioUnitario",10,"descuentoPct",10,"tasaImpuesto",16)));
        String quoteBody=http.perform(post("/api/v1/sales/quotes").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(quoteRequest)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();long quote=json.readTree(quoteBody).get("id").asLong();
        http.perform(post("/api/v1/sales/quotes/{id}/issue",quote).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        http.perform(post("/api/v1/sales/quotes/{id}/accept",quote).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        var orderRequest=Map.of("cotizacionId",quote,"almacenId",f.warehouse(),"modalidad","CONTADO","claveIdempotencia",key());
        String orderBody=http.perform(post("/api/v1/sales/orders").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(orderRequest)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long order=json.readTree(orderBody).get("id").asLong(),line=json.readTree(orderBody).get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/sales/orders/{id}/confirm",order).header("Authorization","Bearer "+u.token())).andExpect(status().isOk());
        var shipmentRequest=Map.of("pedidoId",order,"claveIdempotencia",key(),"detalles",List.of(Map.of("detallePedidoId",line,"cantidad",3)));
        String shipmentBody=http.perform(post("/api/v1/sales/dispatches").header("Authorization","Bearer "+u.token()).contentType("application/json").content(json.writeValueAsString(shipmentRequest)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();assertThat(confirm(u,json.readTree(shipmentBody).get("id").asLong())).isEqualTo(200);
        String invoiceRequest=json.writeValueAsString(Map.of("pedidoId",order,"claveIdempotencia",key()));
        http.perform(post("/api/v1/sales/invoices").header("Authorization","Bearer "+u.token()).contentType("application/json").content(invoiceRequest))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.pedidoId").value(order)).andExpect(jsonPath("$.clienteId").value(createdCustomer))
            .andExpect(jsonPath("$.subtotal").value(27)).andExpect(jsonPath("$.impuestos").value(4.32)).andExpect(jsonPath("$.total").value(31.32));
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=? and almacen_id=?",BigDecimal.class,f.product(),f.warehouse()))
            .isEqualByComparingTo("7");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario m join detalles_despacho dd on dd.id=m.detalle_despacho_id join despachos d on d.id=dd.despacho_id where d.pedido_id=?",Integer.class,order))
            .isEqualTo(1);
    }
}
