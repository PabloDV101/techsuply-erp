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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.TestMethodOrder;
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

@SpringBootTest @AutoConfigureMockMvc @TestMethodOrder(MethodOrderer.MethodName.class)
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
        configureInventoryAccounting(db,user.id());
        String body=http.perform(post("/api/v1/sales/dispatches").header("Authorization","Bearer "+user.token()).contentType("application/json").content(payload(f,key(),quantity)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();return json.readTree(body).get("id").asLong();
    }
    long cuentaContable(String nombre,String tipo,String naturaleza) {
        String k=key();
        return id("insert into cuentas_contables(codigo,nombre,tipo,naturaleza,nivel,acepta_movimientos,activa) values (?,?,?, ?,1,true,true) returning id",
            k,nombre,tipo,naturaleza);
    }
    String configurarCuentas(User user) throws Exception {
        long receivable=cuentaContable("Clientes por cobrar","ACTIVO","DEUDORA");
        long sales=cuentaContable("Ventas","INGRESO","ACREEDORA");
        long taxes=cuentaContable("Impuestos trasladados","PASIVO","ACREEDORA");
        String body=json.writeValueAsString(Map.of("clientesPorCobrarCuentaId",receivable,"ventasCuentaId",sales,"impuestosTrasladadosCuentaId",taxes));
        http.perform(put("/api/v1/accounting/integrations/sales-invoices/accounts")
                .header("Authorization","Bearer "+user.token()).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("$.configurada").value(true));
        return ""+receivable+":"+sales+":"+taxes;
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
        assertThat(db.queryForObject("select count(*) from integracion_factura_venta_contabilidad where factura_venta_id=? and estado='PENDIENTE'",Integer.class,id)).isEqualTo(1);
        assertThatThrownBy(()->db.update("update facturas_venta set total=0 where id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("delete from detalles_factura_venta where factura_venta_id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("delete from facturas_venta where id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("update detalles_factura_venta set precio_unitario=11 where factura_venta_id=?",id)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("insert into detalles_factura_venta(factura_venta_id,detalle_pedido_id,producto_id,cantidad,precio_unitario,descuento_pct,tasa_impuesto,subtotal,impuestos,total) values (?,?,?,5,10,0,16,50,8,58)",id,f.line(),f.product())).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void zzzOutboxAtomicoYReintentoContabilizaFacturaSinDuplicar() throws Exception {
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_FACTURAS_EMITIR",
            "CONTABILIDAD_INTEGRACIONES_LEER","CONTABILIDAD_INTEGRACIONES_ADMIN","CONTABILIDAD_INTEGRACIONES_REINTENTAR",
            "CONTABILIDAD_ASIENTOS_REGISTRAR","CONTABILIDAD_ASIENTOS_CONTABILIZAR","CONTABILIDAD_PERIODOS_CERRAR",
            "CONTABILIDAD_CXC_LEER");
        Fixture f=fixture(u,"CONFIRMADO");complete(u,f);
        User denied=user();
        http.perform(get("/api/v1/accounting/integrations/sales-invoices")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/accounting/integrations/sales-invoices").header("Authorization","Bearer "+denied.token()))
            .andExpect(status().isForbidden());
        String result=http.perform(post("/api/v1/sales/invoices").header("Authorization","Bearer "+u.token())
                .contentType("application/json").content(invoiceBody(f,key())))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long invoiceId=json.readTree(result).get("id").asLong();
        long customerId=id("select cliente_id from pedidos_venta where id=?",f.order());
        http.perform(get("/api/v1/accounting/accounts-receivable")).andExpect(status().isUnauthorized());
        http.perform(get("/api/v1/accounting/accounts-receivable").header("Authorization","Bearer "+denied.token()))
            .andExpect(status().isForbidden());
        http.perform(get("/api/v1/accounting/accounts-receivable").header("Authorization","Bearer "+u.token())
                .param("clienteId",Long.toString(customerId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        assertThat(db.queryForObject("select count(*) from integracion_factura_venta_contabilidad where factura_venta_id=?",Integer.class,invoiceId)).isEqualTo(1);
        http.perform(get("/api/v1/accounting/integrations/sales-invoices/{id}",invoiceId).header("Authorization","Bearer "+u.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("PENDIENTE")).andExpect(jsonPath("$.intentos").value(0));
        LocalDate issueDate=LocalDate.parse(json.readTree(result).get("fechaEmision").asText());
        db.update("insert into periodos_contables(fecha_inicio,fecha_fin,estado) select ?,?,'ABIERTO' where not exists (select 1 from periodos_contables where fecha_inicio<=? and fecha_fin>=?)",
            java.sql.Date.valueOf(issueDate),java.sql.Date.valueOf(issueDate),java.sql.Date.valueOf(issueDate),java.sql.Date.valueOf(issueDate));
        http.perform(post("/api/v1/accounting/integrations/sales-invoices/{id}/retry",invoiceId)
                .header("Authorization","Bearer "+u.token()).contentType("application/json").content("{}"))
            .andExpect(status().isConflict());
        assertThat(db.queryForObject("select estado from integracion_factura_venta_contabilidad where factura_venta_id=?",String.class,invoiceId)).isEqualTo("PENDIENTE");
        assertThat(db.queryForObject("select ultimo_codigo_error from integracion_factura_venta_contabilidad where factura_venta_id=?",String.class,invoiceId))
            .isEqualTo("CONFIGURACION_INCOMPLETA");
        String ids=configurarCuentas(u);String[] accountIds=ids.split(":");
        db.execute("alter table eventos_auditoria add constraint ck_test_receivable_audit_failure check (accion <> 'ACCOUNTING_RECEIVABLE_CREATED') not valid");
        try {
            assertThat(http.perform(post("/api/v1/accounting/integrations/sales-invoices/{id}/retry",invoiceId)
                    .header("Authorization","Bearer "+u.token()).contentType("application/json").content("{}"))
                .andReturn().getResponse().getStatus()).isEqualTo(500);
            assertThat(db.queryForObject("select estado from integracion_factura_venta_contabilidad where factura_venta_id=?",String.class,invoiceId)).isEqualTo("PENDIENTE");
            assertThat(db.queryForObject("select count(*) from cuentas_por_cobrar where factura_venta_id=?",Integer.class,invoiceId)).isZero();
            assertThat(db.queryForObject("select count(*) from asientos_contables where origen_modulo='VENTAS' and origen_tipo='FACTURA_VENTA' and origen_id=?",Integer.class,invoiceId)).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_receivable_audit_failure"); }
        assertThat(db.queryForObject("select ultimo_codigo_error from integracion_factura_venta_contabilidad where factura_venta_id=?",String.class,invoiceId))
            .isEqualTo("ERROR_CONTABLE");
        record RetryResult(int status,String body) {}
        var pool=Executors.newFixedThreadPool(2);var gate=new CountDownLatch(1);
        List<RetryResult> retries;
        try {
            Callable<RetryResult> retry=()->{gate.await();var response=http.perform(post("/api/v1/accounting/integrations/sales-invoices/{id}/retry",invoiceId)
                .header("Authorization","Bearer "+u.token()).contentType("application/json").content("{}")).andReturn().getResponse();
                return new RetryResult(response.getStatus(),response.getContentAsString());};
            var a=pool.submit(retry);var b=pool.submit(retry);gate.countDown();retries=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertThat(retries).extracting(RetryResult::status).containsExactlyInAnyOrder(200,200);
        String posted=retries.getFirst().body();
        assertThat(json.readTree(posted).get("estado").asText()).isEqualTo("CONTABILIZADA");
        assertThat(json.readTree(retries.get(1).body()).get("estado").asText()).isEqualTo("CONTABILIZADA");
        assertThat(json.readTree(retries.get(1).body()).get("asientoContableId").asLong())
            .isEqualTo(json.readTree(posted).get("asientoContableId").asLong());
        assertThat(json.readTree(posted).get("intentos").asInt()).isEqualTo(3);
        long entry=json.readTree(posted).get("asientoContableId").asLong();
        assertThat(db.queryForObject("select estado from asientos_contables where id=?",String.class,entry)).isEqualTo("CONTABILIZADO");
        assertThat(db.queryForObject("select origen_modulo||':'||origen_tipo||':'||origen_id from asientos_contables where id=?",String.class,entry))
            .isEqualTo("VENTAS:FACTURA_VENTA:"+invoiceId);
        assertThat(db.queryForObject("select count(*) from partidas_asiento where asiento_id=?",Integer.class,entry)).isEqualTo(3);
        assertThat(db.queryForObject("select count(*) from cuentas_por_cobrar where factura_venta_id=?",Integer.class,invoiceId)).isEqualTo(1);
        assertThat(db.queryForObject("select monto_original from cuentas_por_cobrar where factura_venta_id=?",BigDecimal.class,invoiceId))
            .isEqualByComparingTo("58.00");
        assertThat(db.queryForObject("select cliente_id from cuentas_por_cobrar where factura_venta_id=?",Long.class,invoiceId))
            .isEqualTo(customerId);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='ACCOUNTING_RECEIVABLE_CREATED' and entidad_id=(select id from cuentas_por_cobrar where factura_venta_id=?)",Integer.class,invoiceId)).isEqualTo(1);
        assertThatThrownBy(()->db.update("update cuentas_por_cobrar set monto_original=1 where factura_venta_id=?",invoiceId))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("delete from cuentas_por_cobrar where factura_venta_id=?",invoiceId))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        http.perform(get("/api/v1/accounting/accounts-receivable").header("Authorization","Bearer "+u.token())
                .param("clienteId",Long.toString(customerId)))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
            .andExpect(jsonPath("$.content[0].facturaVentaId").value(invoiceId))
            .andExpect(jsonPath("$.content[0].clienteId").value(customerId))
            .andExpect(jsonPath("$.content[0].montoOriginal").value(58.00))
            .andExpect(jsonPath("$.content[0].saldoPendiente").value(58.00));
        long receivableId=id("select id from cuentas_por_cobrar where factura_venta_id=?",invoiceId);
        http.perform(get("/api/v1/accounting/accounts-receivable/{id}",receivableId).header("Authorization","Bearer "+u.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.fechaVencimiento").value(issueDate.toString()));
        assertThat(db.queryForObject("select debe from partidas_asiento where asiento_id=? and cuenta_contable_id=?",BigDecimal.class,entry,Long.valueOf(accountIds[0])))
            .isEqualByComparingTo("58.00");
        assertThat(db.queryForObject("select haber from partidas_asiento where asiento_id=? and cuenta_contable_id=?",BigDecimal.class,entry,Long.valueOf(accountIds[1])))
            .isEqualByComparingTo("50.00");
        assertThat(db.queryForObject("select haber from partidas_asiento where asiento_id=? and cuenta_contable_id=?",BigDecimal.class,entry,Long.valueOf(accountIds[2])))
            .isEqualByComparingTo("8.00");
        http.perform(post("/api/v1/accounting/integrations/sales-invoices/{id}/retry",invoiceId)
                .header("Authorization","Bearer "+u.token()).contentType("application/json").content("{}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.asientoContableId").value(entry)).andExpect(jsonPath("$.intentos").value(3));
        assertThat(db.queryForObject("select count(*) from asientos_contables where origen_modulo='VENTAS' and origen_tipo='FACTURA_VENTA' and origen_id=?",Integer.class,invoiceId)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from cuentas_por_cobrar where factura_venta_id=?",Integer.class,invoiceId)).isEqualTo(1);
        String pending=http.perform(get("/api/v1/accounting/integrations/sales-invoices").header("Authorization","Bearer "+u.token()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(json.readTree(pending).get("totalElements").asLong())
            .isEqualTo(db.queryForObject("select count(*) from integracion_factura_venta_contabilidad where estado='PENDIENTE'",Long.class));
        assertThat(db.queryForObject("select count(*) from integracion_factura_venta_contabilidad where factura_venta_id=? and estado='PENDIENTE'",Integer.class,invoiceId)).isZero();
        Fixture later=fixture(u,"CONFIRMADO");complete(u,later);
        db.update("update periodos_contables set estado='CERRADO',cerrado_por_usuario_id=?,cerrado_en=now() where fecha_inicio=? and fecha_fin=?",
            u.id(),java.sql.Date.valueOf(issueDate),java.sql.Date.valueOf(issueDate));
        String laterResult=http.perform(post("/api/v1/sales/invoices").header("Authorization","Bearer "+u.token())
                .contentType("application/json").content(invoiceBody(later,key())))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long laterInvoiceId=json.readTree(laterResult).get("id").asLong();
        http.perform(post("/api/v1/accounting/integrations/sales-invoices/{id}/retry",laterInvoiceId)
                .header("Authorization","Bearer "+u.token()).contentType("application/json").content("{}"))
            .andExpect(status().isConflict());
        assertThat(db.queryForObject("select estado from facturas_venta where id=?",String.class,laterInvoiceId)).isEqualTo("EMITIDA");
        assertThat(db.queryForObject("select estado from integracion_factura_venta_contabilidad where factura_venta_id=?",String.class,laterInvoiceId)).isEqualTo("PENDIENTE");
        assertThat(db.queryForObject("select count(*) from cuentas_por_cobrar where factura_venta_id=?",Integer.class,laterInvoiceId)).isZero();
        assertThat(db.queryForObject("select ultimo_codigo_error from integracion_factura_venta_contabilidad where factura_venta_id=?",String.class,laterInvoiceId))
            .isEqualTo("PERIODO_NO_DISPONIBLE");
        LocalDate retryDate=issueDate.plusDays(20);
        db.update("insert into periodos_contables(fecha_inicio,fecha_fin,estado) values (?,?,'ABIERTO')",
            java.sql.Date.valueOf(retryDate),java.sql.Date.valueOf(retryDate));
        String retry=json.writeValueAsString(Map.of("fechaContable",retryDate.toString()));
        http.perform(post("/api/v1/accounting/integrations/sales-invoices/{id}/retry",laterInvoiceId)
                .header("Authorization","Bearer "+u.token()).contentType("application/json").content(retry))
            .andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONTABILIZADA"))
            .andExpect(jsonPath("$.fechaContable").value(retryDate.toString())).andExpect(jsonPath("$.intentos").value(2));
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
        db.execute("alter table eventos_auditoria add constraint ck_test_invoice_outbox_failure check (accion <> 'ACCOUNTING_SALES_INVOICE_QUEUED') not valid");
        try {
            assertThat(invoice(u,body)).isEqualTo(409);
            assertThat(db.queryForObject("select count(*) from facturas_venta where pedido_id=?",Integer.class,f.order())).isZero();
            assertThat(db.queryForObject("select count(*) from integracion_factura_venta_contabilidad i join facturas_venta v on v.id=i.factura_venta_id where v.pedido_id=?",Integer.class,f.order())).isZero();
        } finally {db.execute("alter table eventos_auditoria drop constraint ck_test_invoice_outbox_failure");}
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
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada,costo_promedio) values (?,?,1,0.005,2)",product,f.warehouse());
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

    @Test void confirmaCobroYAplicaParcialmenteConLimitesSaldoIdempotenciaYAsientos() throws Exception {
        db.update("delete from configuracion_cuentas_factura_venta");
        db.update("delete from configuracion_cuentas_cobro");
        User u=user("VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR","VENTAS_FACTURAS_EMITIR",
            "CONTABILIDAD_INTEGRACIONES_ADMIN","CONTABILIDAD_INTEGRACIONES_REINTENTAR",
            "CONTABILIDAD_ASIENTOS_REGISTRAR","CONTABILIDAD_ASIENTOS_CONTABILIZAR",
            "CONTABILIDAD_COBROS_REGISTRAR","CONTABILIDAD_COBROS_CONFIRMAR","CONTABILIDAD_COBROS_LEER","CONTABILIDAD_COBROS_CONFIGURAR","CONTABILIDAD_CXC_LEER");
        Fixture f=fixture(u,"CONFIRMADO"); complete(u,f); configurarCuentas(u);
        LocalDate date=LocalDate.now(ZoneId.of("America/Mexico_City"));
        db.update("insert into periodos_contables(fecha_inicio,fecha_fin,estado) select ?,?,'ABIERTO' where not exists (select 1 from periodos_contables where estado='ABIERTO' and fecha_inicio<=? and fecha_fin>=?)",
            java.sql.Date.valueOf(date),java.sql.Date.valueOf(date),java.sql.Date.valueOf(date),java.sql.Date.valueOf(date));
        long invoiceId=json.readTree(http.perform(post("/api/v1/sales/invoices").header("Authorization","Bearer "+u.token())
            .contentType("application/json").content(invoiceBody(f,key()))).andExpect(status().isCreated()).andReturn()
            .getResponse().getContentAsString()).get("id").asLong();
        http.perform(post("/api/v1/accounting/integrations/sales-invoices/{id}/retry",invoiceId).header("Authorization","Bearer "+u.token())
            .contentType("application/json").content("{}" )).andExpect(status().isOk());
        long cxc=id("select id from cuentas_por_cobrar where factura_venta_id=?",invoiceId), customer=id("select cliente_id from cuentas_por_cobrar where id=?",cxc);
        long funds=cuentaContable("Fondos banco","ACTIVO","DEUDORA"), advances=cuentaContable("Anticipos clientes","PASIVO","ACREEDORA");
        http.perform(put("/api/v1/accounting/customer-receipts/accounts").header("Authorization","Bearer "+u.token()).contentType("application/json")
            .content(json.writeValueAsString(Map.of("medio","TRANSFERENCIA","cuentaFondosId",funds,"cuentaAnticiposId",advances))))
            .andExpect(status().isOk()).andExpect(jsonPath("$.medios[0].medio").value("TRANSFERENCIA"));
        String receiptBody=json.writeValueAsString(Map.of("clienteId",customer,"fecha",java.time.Instant.now().toString(),"monto",80,
            "medio","TRANSFERENCIA","referencia","REF-1","claveIdempotencia",key()));
        String receipt=http.perform(post("/api/v1/accounting/customer-receipts").header("Authorization","Bearer "+u.token())
            .contentType("application/json").content(receiptBody)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("REGISTRADO"))
            .andReturn().getResponse().getContentAsString();
        long receiptId=json.readTree(receipt).get("id").asLong();
        var confirmation=json.writeValueAsString(Map.of("fechaContable",date.toString()));
        http.perform(post("/api/v1/accounting/customer-receipts/{id}/confirm",receiptId).header("Authorization","Bearer "+u.token())
            .contentType("application/json").content(confirmation)).andExpect(status().isOk()).andExpect(jsonPath("$.estado").value("CONFIRMADO"))
            .andExpect(jsonPath("$.montoDisponible").value(80));
        assertThat(db.queryForObject("select count(*) from asientos_contables where origen_modulo='CONTABILIDAD' and origen_tipo='COBRO_CLIENTE' and origen_id=? and estado='CONTABILIZADO'",Integer.class,receiptId)).isEqualTo(1);
        String first=json.writeValueAsString(Map.of("cuentaPorCobrarId",cxc,"monto",40,"claveIdempotencia",key(),"fechaContable",date.toString()));
        String racing=json.writeValueAsString(Map.of("cuentaPorCobrarId",cxc,"monto",40,"claveIdempotencia",key(),"fechaContable",date.toString()));
        record ApplicationAttempt(int status,String body) {}
        var pool=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        List<ApplicationAttempt> attempts;
        try {
            java.util.function.Function<String,Callable<ApplicationAttempt>> apply=body->()->{gate.await();var response=http.perform(post("/api/v1/accounting/customer-receipts/{id}/applications",receiptId)
                .header("Authorization","Bearer "+u.token()).contentType("application/json").content(body)).andReturn().getResponse();
                return new ApplicationAttempt(response.getStatus(),body);};
            var a=pool.submit(apply.apply(first)); var b=pool.submit(apply.apply(racing)); gate.countDown();
            attempts=List.of(a.get(20,TimeUnit.SECONDS),b.get(20,TimeUnit.SECONDS));
        } finally {pool.shutdownNow();}
        assertThat(attempts).extracting(ApplicationAttempt::status).containsExactlyInAnyOrder(200,409);
        String winner=attempts.stream().filter(result->result.status()==200).findFirst().orElseThrow().body();
        http.perform(post("/api/v1/accounting/customer-receipts/{id}/applications",receiptId).header("Authorization","Bearer "+u.token())
            .contentType("application/json").content(winner)).andExpect(status().isOk()).andExpect(jsonPath("$.montoAplicado").value(40))
            .andExpect(jsonPath("$.montoDisponible").value(40));
        assertThat(db.queryForObject("select monto_original-coalesce(sum(a.monto_aplicado),0) from cuentas_por_cobrar c left join aplicaciones_cobro a on a.cuenta_por_cobrar_id=c.id where c.id=? group by c.id",BigDecimal.class,cxc)).isEqualByComparingTo("18.00");
        String tooMuch=json.writeValueAsString(Map.of("cuentaPorCobrarId",cxc,"monto",30,"claveIdempotencia",key(),"fechaContable",date.toString()));
        http.perform(post("/api/v1/accounting/customer-receipts/{id}/applications",receiptId).header("Authorization","Bearer "+u.token())
            .contentType("application/json").content(tooMuch)).andExpect(status().isConflict());
        String second=json.writeValueAsString(Map.of("cuentaPorCobrarId",cxc,"monto",18,"claveIdempotencia",key(),"fechaContable",date.toString()));
        http.perform(post("/api/v1/accounting/customer-receipts/{id}/applications",receiptId).header("Authorization","Bearer "+u.token())
            .contentType("application/json").content(second)).andExpect(status().isOk()).andExpect(jsonPath("$.montoAplicado").value(58))
            .andExpect(jsonPath("$.montoDisponible").value(22));
        http.perform(get("/api/v1/accounting/accounts-receivable/{id}",cxc).header("Authorization","Bearer "+u.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.saldoPendiente").value(0));
        assertThat(db.queryForObject("select count(*) from aplicaciones_cobro where cobro_id=?",Integer.class,receiptId)).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from asientos_contables where origen_modulo='CONTABILIDAD' and origen_tipo='APLICACION_COBRO' and estado='CONTABILIZADO'",Integer.class)).isEqualTo(2);
        db.update("delete from configuracion_cuentas_factura_venta");
        db.update("delete from configuracion_cuentas_cobro");
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
