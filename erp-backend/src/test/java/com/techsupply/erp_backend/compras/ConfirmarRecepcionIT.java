package com.techsupply.erp_backend.compras;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
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
class ConfirmarRecepcionIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http; @Autowired JdbcTemplate db; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    record Fixture(String key, long order, long line, long product, long warehouse, long user) {}
    private String token(String... permissions) throws Exception {
        String key = UUID.randomUUID().toString(), password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class, "T_" + key.substring(0,20).toUpperCase(Locale.ROOT), "Test");
        for (String permission : permissions) db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, permission);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        configureInventoryAccounting(db, user);
        String result = http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador", key, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("accessToken").asText();
    }
    private Fixture fixture() {
        String key = "Z" + UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id", Long.class, key, "Test");
        long product = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, key, "Test", category);
        long supplier = db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id", Long.class, key, "Test");
        long dept = db.queryForObject("insert into departamentos(codigo,nombre) values (?,?) returning id", Long.class, key, "Test");
        long warehouse = db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id", Long.class, key, "Test");
        String name = key.toLowerCase(Locale.ROOT);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, name, name + "@example.test", "test-hash");
        long order = db.queryForObject("insert into ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values (?,?,?,?,?) returning id", Long.class, key, supplier, dept, user, "ENVIADA");
        long line = db.queryForObject("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,?,?,?) returning id", Long.class, order, product, 20, 10, 16);
        return new Fixture(key, order, line, product, warehouse, user);
    }
    private long receipt(Fixture f, String suffix, int accepted, int rejected) {
        long receipt = db.queryForObject("insert into recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,?) returning id", Long.class, f.key()+suffix, f.order(), f.warehouse(), f.user(), f.key()+suffix, "PENDIENTE");
        db.update("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,?,?,?,?)", receipt, f.line(), accepted+rejected, accepted, rejected, 10);
        return receipt;
    }
    private int confirm(String token, long id) throws Exception {
        return http.perform(post("/api/v1/purchase-receipts/{id}/confirm", id).header("Authorization", "Bearer " + token)).andReturn().getResponse().getStatus();
    }
    @Test void facturaProveedorConciliaRecepcionYContabilizaCxPUnaSolaVez() throws Exception {
        Fixture f=fixture();
        String actor=token("COMPRAS_RECEPCIONES_CONFIRMAR","COMPRAS_FACTURAS_PROVEEDOR_REGISTRAR",
            "COMPRAS_FACTURAS_PROVEEDOR_VALIDAR","COMPRAS_FACTURAS_PROVEEDOR_LEER",
            "CONTABILIDAD_CXP_CONFIGURAR","CONTABILIDAD_CXP_LEER", "CONTABILIDAD_PAGOS_PROVEEDOR_REGISTRAR",
            "CONTABILIDAD_PAGOS_PROVEEDOR_CONFIRMAR", "CONTABILIDAD_PAGOS_PROVEEDOR_CONFIGURAR",
            "CONTABILIDAD_ASIENTOS_REGISTRAR", "CONTABILIDAD_ASIENTOS_CONTABILIZAR", "CONTABILIDAD_ASIENTOS_REVERTIR");
        db.update("update ordenes_compra set subtotal=200,impuestos=32,total=232 where id=?",f.order());
        long receipt=receipt(f,"FP",20,0);
        assertThat(confirm(actor,receipt)).isEqualTo(200);
        long vat=db.queryForObject("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?,?,?,?) returning id",
            Long.class,f.key()+"IVA","IVA acreditable","ACTIVO","DEUDORA");
        long payable=db.queryForObject("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?,?,?,?) returning id",
            Long.class,f.key()+"CXP","Cuentas por pagar","PASIVO","ACREEDORA");
        http.perform(put("/api/v1/accounting/supplier-invoice-accounts").header("Authorization","Bearer "+actor)
            .contentType("application/json").content(json.writeValueAsString(Map.of(
                "ivaAcreditableCuentaId",vat,"cuentasPorPagarCuentaId",payable))))
            .andExpect(status().isOk());
        var input=Map.of("ordenCompraId",f.order(),"numeroFacturaProveedor",f.key()+"-N",
            "fechaEmision","2026-11-01","fechaVencimiento","2026-12-01",
            "detalles",List.of(Map.of("detalleOrdenCompraId",f.line(),"cantidadFacturada",20,
                "precioUnitario",10,"descuentoPct",0,"tasaImpuesto",16)));
        String result=http.perform(post("/api/v1/supplier-invoices").header("Authorization","Bearer "+actor)
            .contentType("application/json").content(json.writeValueAsString(input)))
            .andExpect(status().isCreated()).andExpect(jsonPath("estado").value("REGISTRADA"))
            .andReturn().getResponse().getContentAsString();
        long invoice=json.readTree(result).get("id").asLong();
        http.perform(post("/api/v1/supplier-invoices").header("Authorization","Bearer "+actor)
            .contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isConflict());
        http.perform(post("/api/v1/supplier-invoices/{id}/validate",invoice).header("Authorization","Bearer "+actor))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("VALIDADA"));
        http.perform(post("/api/v1/supplier-invoices/{id}/validate",invoice).header("Authorization","Bearer "+actor))
            .andExpect(status().isOk());
        assertThat(db.queryForObject("select count(*) from cuentas_por_pagar where factura_proveedor_id=?",Integer.class,invoice)).isEqualTo(1);
        long seat=db.queryForObject("select asiento_contable_id from facturas_proveedor where id=?",Long.class,invoice);
        assertThat(db.queryForObject("select sum(debe) from partidas_asiento where asiento_id=?",BigDecimal.class,seat)).isEqualByComparingTo("232.00");
        assertThat(db.queryForObject("select sum(haber) from partidas_asiento where asiento_id=?",BigDecimal.class,seat)).isEqualByComparingTo("232.00");
        assertThat(db.queryForObject("select count(*) from asientos_contables where origen_modulo='COMPRAS' and origen_tipo='FACTURA_PROVEEDOR' and origen_id=?",Integer.class,invoice)).isEqualTo(1);
        long cxp=db.queryForObject("select id from cuentas_por_pagar where factura_proveedor_id=?",Long.class,invoice);
        long supplier=db.queryForObject("select proveedor_id from cuentas_por_pagar where id=?",Long.class,cxp);
        long funds=db.queryForObject("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?,?,?,?) returning id",Long.class,f.key()+"BAN","Bancos","ACTIVO","DEUDORA");
        http.perform(put("/api/v1/accounting/supplier-payments/accounts").header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(Map.of("cuentaFondosId",funds)))).andExpect(status().isOk());
        http.perform(get("/api/v1/accounting/supplier-payments/accounts").header("Authorization","Bearer "+actor)).andExpect(status().isOk()).andExpect(jsonPath("cuentaFondosId").value(funds));
        var payment=Map.of("proveedorId",supplier,"fechaPago","2026-11-19T12:00:00Z","monto",100,"medio","TRANSFERENCIA","claveIdempotencia",f.key());
        String created=http.perform(post("/api/v1/accounting/supplier-payments").header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(payment))).andExpect(status().isOk()).andExpect(jsonPath("proveedorId").value(supplier)).andExpect(jsonPath("clave_idempotencia").doesNotExist()).andReturn().getResponse().getContentAsString();
        long paymentId=json.readTree(created).get("id").asLong();
        http.perform(post("/api/v1/accounting/supplier-payments").header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(payment))).andExpect(status().isOk()).andExpect(jsonPath("id").value(paymentId));
        var changedPayment=Map.of("proveedorId",supplier,"fechaPago","2026-11-19T12:00:00Z","monto",101,"medio","TRANSFERENCIA","claveIdempotencia",f.key());
        http.perform(post("/api/v1/accounting/supplier-payments").header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(changedPayment))).andExpect(status().isConflict());
        var applications=Map.of("aplicaciones",List.of(Map.of("cuentaPorPagarId",cxp,"monto",100)));
        long otherSupplier=db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id",Long.class,f.key()+"OT","Otro proveedor");
        var otherPayment=Map.of("proveedorId",otherSupplier,"fechaPago","2026-11-19T12:00:00Z","monto",100,"medio","TRANSFERENCIA","claveIdempotencia",f.key()+"OT");
        String otherResponse=http.perform(post("/api/v1/accounting/supplier-payments").header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(otherPayment))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long otherPaymentId=json.readTree(otherResponse).get("id").asLong();
        http.perform(post("/api/v1/accounting/supplier-payments/{id}/confirm",otherPaymentId).header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(applications))).andExpect(status().isConflict());
        assertThat(db.queryForObject("select count(*) from aplicaciones_pago_proveedor where pago_proveedor_id=?",Integer.class,otherPaymentId)).isZero();
        http.perform(post("/api/v1/accounting/supplier-payments/{id}/confirm",paymentId).contentType("application/json").content(json.writeValueAsString(applications))).andExpect(status().isUnauthorized());
        String unauthorizedUser=token("CONTABILIDAD_PAGOS_PROVEEDOR_LEER");
        http.perform(post("/api/v1/accounting/supplier-payments/{id}/confirm",paymentId).header("Authorization","Bearer "+unauthorizedUser).contentType("application/json").content(json.writeValueAsString(applications))).andExpect(status().isForbidden());
        var incomplete=Map.of("aplicaciones",List.of(Map.of("cuentaPorPagarId",cxp,"monto",99)));
        http.perform(post("/api/v1/accounting/supplier-payments/{id}/confirm",paymentId).header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(incomplete))).andExpect(status().isConflict());
        assertThat(db.queryForObject("select count(*) from aplicaciones_pago_proveedor where pago_proveedor_id=?",Integer.class,paymentId)).isZero();
        db.update("delete from configuracion_cuenta_pago_proveedor");
        http.perform(post("/api/v1/accounting/supplier-payments/{id}/confirm",paymentId).header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(applications))).andExpect(status().isConflict());
        assertThat(db.queryForObject("select count(*) from aplicaciones_pago_proveedor where pago_proveedor_id=?",Integer.class,paymentId)).isZero();
        assertThat(db.queryForObject("select estado from pagos_proveedor where id=?",String.class,paymentId)).isEqualTo("REGISTRADO");
        http.perform(put("/api/v1/accounting/supplier-payments/accounts").header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(Map.of("cuentaFondosId",funds)))).andExpect(status().isOk());
        http.perform(post("/api/v1/accounting/supplier-payments/{id}/confirm",paymentId).header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(applications))).andExpect(status().isOk()).andExpect(jsonPath("aplicaciones[0].cuentaPorPagarId").value(cxp));
        long paymentSeat=db.queryForObject("select asiento_contable_id from pagos_proveedor where id=?",Long.class,paymentId);
        assertThat(db.queryForObject("select debe from partidas_asiento where asiento_id=? and cuenta_contable_id=?",BigDecimal.class,paymentSeat,payable)).isEqualByComparingTo("100");
        assertThat(db.queryForObject("select haber from partidas_asiento where asiento_id=? and cuenta_contable_id=?",BigDecimal.class,paymentSeat,funds)).isEqualByComparingTo("100");
        http.perform(post("/api/v1/accounting/entries/{id}/reverse",paymentSeat).header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(Map.of("fechaContable",java.time.LocalDate.now(java.time.ZoneId.of("America/Mexico_City")).toString(),"motivo","Prueba","claveIdempotencia",f.key()+"REV")))).andExpect(status().isConflict());
        http.perform(post("/api/v1/accounting/supplier-payments/{id}/confirm",paymentId).header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(applications))).andExpect(status().isOk());
        http.perform(get("/api/v1/accounting/payables/{id}",cxp).header("Authorization","Bearer "+actor)).andExpect(status().isOk()).andExpect(jsonPath("saldoPendiente").value(132));
        List<Long> competing=new ArrayList<>();
        for (int i=0;i<2;i++) {
            var next=Map.of("proveedorId",supplier,"fechaPago","2026-11-19T12:00:00Z","monto",100,"medio","TRANSFERENCIA","claveIdempotencia",f.key()+"-"+i);
            String response=http.perform(post("/api/v1/accounting/supplier-payments").header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(next))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            competing.add(json.readTree(response).get("id").asLong());
        }
        var pool=Executors.newFixedThreadPool(2);
        try {
            var gate=new CountDownLatch(1);
            List<Future<Integer>> results=new ArrayList<>();
            for(long next:competing) results.add(pool.submit(()->{ gate.await(); return http.perform(post("/api/v1/accounting/supplier-payments/{id}/confirm",next).header("Authorization","Bearer "+actor).contentType("application/json").content(json.writeValueAsString(applications))).andReturn().getResponse().getStatus(); }));
            gate.countDown();
            assertThat(List.of(results.get(0).get(30,TimeUnit.SECONDS),results.get(1).get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
        http.perform(get("/api/v1/accounting/payables/{id}",cxp).header("Authorization","Bearer "+actor)).andExpect(status().isOk()).andExpect(jsonPath("saldoPendiente").value(32));
    }
    @Test void facturaProveedorConDiferenciaPermaneceRegistradaYSePuedeCorregir() throws Exception {
        Fixture f=fixture();
        String actor=token("COMPRAS_RECEPCIONES_CONFIRMAR","COMPRAS_FACTURAS_PROVEEDOR_REGISTRAR",
            "COMPRAS_FACTURAS_PROVEEDOR_VALIDAR","COMPRAS_FACTURAS_PROVEEDOR_LEER");
        db.update("update ordenes_compra set subtotal=200,impuestos=32,total=232 where id=?",f.order());
        assertThat(confirm(actor,receipt(f,"DIFF",20,0))).isEqualTo(200);
        var line=Map.of("detalleOrdenCompraId",f.line(),"cantidadFacturada",19,
            "precioUnitario",10,"descuentoPct",0,"tasaImpuesto",16);
        var input=Map.of("ordenCompraId",f.order(),"numeroFacturaProveedor",f.key()+"-D",
            "fechaEmision","2026-11-01","fechaVencimiento","2026-12-01","detalles",List.of(line));
        String result=http.perform(post("/api/v1/supplier-invoices").header("Authorization","Bearer "+actor)
            .contentType("application/json").content(json.writeValueAsString(input)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long invoice=json.readTree(result).get("id").asLong();
        http.perform(post("/api/v1/supplier-invoices/{id}/validate",invoice).header("Authorization","Bearer "+actor))
            .andExpect(status().isConflict());
        assertThat(db.queryForObject("select estado from facturas_proveedor where id=?",String.class,invoice)).isEqualTo("REGISTRADA");
        assertThat(db.queryForObject("select count(*) from cuentas_por_pagar where factura_proveedor_id=?",Integer.class,invoice)).isZero();
        var corrected=Map.of("ordenCompraId",f.order(),"numeroFacturaProveedor",f.key()+"-D",
            "fechaEmision","2026-11-01","fechaVencimiento","2026-12-01",
            "detalles",List.of(Map.of("detalleOrdenCompraId",f.line(),"cantidadFacturada",20,
                "precioUnitario",10,"descuentoPct",0,"tasaImpuesto",16)));
        http.perform(put("/api/v1/supplier-invoices/{id}",invoice).header("Authorization","Bearer "+actor)
            .contentType("application/json").content(json.writeValueAsString(corrected)))
            .andExpect(status().isOk()).andExpect(jsonPath("total").value(232.0));
        db.update("delete from configuracion_cuentas_factura_proveedor");
        http.perform(post("/api/v1/supplier-invoices/{id}/validate",invoice).header("Authorization","Bearer "+actor))
            .andExpect(status().isConflict());
        assertThat(db.queryForObject("select estado from facturas_proveedor where id=?",String.class,invoice)).isEqualTo("REGISTRADA");
        assertThat(db.queryForObject("select count(*) from asientos_contables where origen_modulo='COMPRAS' and origen_tipo='FACTURA_PROVEEDOR' and origen_id=?",Integer.class,invoice)).isZero();
    }
    @Test void configuracionIncompletaRevierteRecepcionYDejaReintentoPendiente() throws Exception {
        Fixture f=fixture(); String confirmer=token("COMPRAS_RECEPCIONES_CONFIRMAR"); long receipt=receipt(f,"ROLLBACK",2,0);
        db.update("delete from configuracion_cuentas_inventario");
        assertThat(confirm(confirmer,receipt)).isEqualTo(409);
        assertThat(db.queryForObject("select estado from recepciones_compra where id=?",String.class,receipt)).isEqualTo("PENDIENTE");
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,f.product())).isZero();
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where detalle_recepcion_id in (select id from detalles_recepcion where recepcion_id=?)",Integer.class,receipt)).isZero();
        assertThat(db.queryForObject("select count(*) from asientos_contables where origen_modulo='COMPRAS' and origen_tipo='RECEPCION_COMPRA' and origen_id=?",Integer.class,receipt)).isZero();
    }
    @Test void endToEndOrderAndPartialReceiptThroughHttp() throws Exception {
        Fixture f = fixture();
        String creator = token("COMPRAS_ORDENES_ADMIN", "COMPRAS_ORDENES_LEER", "COMPRAS_RECEPCIONES_REGISTRAR", "COMPRAS_RECEPCIONES_CONFIRMAR");
        String approver = token("COMPRAS_ORDENES_APROBAR");
        long supplier = db.queryForObject("select proveedor_id from ordenes_compra where id=?", Long.class, f.order());
        long department = db.queryForObject("select departamento_id from ordenes_compra where id=?", Long.class, f.order());
        var input = Map.of("proveedorId", supplier, "departamentoId", department, "detalles", List.of(Map.of(
            "productoId", f.product(), "cantidad", 20, "precioUnitario", 10, "descuentoPct", 0, "tasaImpuesto", 16)));
        String created = http.perform(post("/api/v1/purchase-orders").header("Authorization", "Bearer " + creator)
            .contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long order = json.readTree(created).get("id").asLong();
        long detail = json.readTree(created).get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/purchase-orders/{id}/submit", order).header("Authorization", "Bearer " + creator)).andExpect(status().isOk());
        http.perform(post("/api/v1/purchase-orders/{id}/approve", order).header("Authorization", "Bearer " + approver)).andExpect(status().isOk());
        http.perform(post("/api/v1/purchase-orders/{id}/send", order).header("Authorization", "Bearer " + creator)).andExpect(status().isOk());
        var receipt = Map.of("ordenCompraId", order, "almacenId", f.warehouse(), "claveIdempotencia", f.key()+"HTTP",
            "detalles", List.of(Map.of("detalleOrdenCompraId", detail, "cantidadRecibida", 20,
                "cantidadAceptada", 18, "cantidadRechazada", 2, "precioUnitarioRecibido", 10)));
        String registered = http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + creator)
            .contentType("application/json").content(json.writeValueAsString(receipt))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long receiptId = json.readTree(registered).get("id").asLong();
        assertThat(confirm(creator, receiptId)).isEqualTo(200);
        http.perform(get("/api/v1/purchase-orders/{id}", order).header("Authorization", "Bearer " + creator))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("PARCIALMENTE_RECIBIDA"))
            .andExpect(jsonPath("detalles[0].cantidadPendiente").value(2));
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?", BigDecimal.class, f.product())).isEqualByComparingTo("18");
    }
    @Test void partialReceiptThenCompleteAndIdempotentRetry() throws Exception {
        Fixture f = fixture(); String confirm = token("COMPRAS_RECEPCIONES_CONFIRMAR"); String reader = token("COMPRAS_RECEPCIONES_LEER", "COMPRAS_ORDENES_LEER");
        long first = receipt(f,"A",18,2);
        http.perform(post("/api/v1/purchase-receipts/{id}/confirm", first)).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/purchase-receipts/{id}/confirm", first).header("Authorization", "Bearer " + reader)).andExpect(status().isForbidden());
        http.perform(post("/api/v1/purchase-receipts/{id}/confirm", first).header("Authorization", "Bearer " + confirm))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("CONFIRMADA")).andExpect(jsonPath("confirmadaEn").isNotEmpty());
        assertThat(db.queryForObject("select estado from ordenes_compra where id=?", String.class, f.order())).isEqualTo("PARCIALMENTE_RECIBIDA");
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?", BigDecimal.class, f.product())).isEqualByComparingTo("18");
        http.perform(get("/api/v1/purchase-orders/{id}", f.order()).header("Authorization", "Bearer " + reader))
            .andExpect(status().isOk()).andExpect(jsonPath("detalles[0].cantidadAceptada").value(18))
            .andExpect(jsonPath("detalles[0].cantidadPendiente").value(2));
        assertThat(confirm(confirm, first)).isEqualTo(200);
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?", Integer.class, f.product())).isEqualTo(1);
        long firstEntry = db.queryForObject("select id from asientos_contables where origen_modulo='COMPRAS' and origen_tipo='RECEPCION_COMPRA' and origen_id=?", Long.class, first);
        assertThat(db.queryForObject("select estado from asientos_contables where id=?", String.class, firstEntry)).isEqualTo("CONTABILIZADO");
        assertThat(db.queryForObject("select sum(debe) from partidas_asiento where asiento_id=?", BigDecimal.class, firstEntry)).isEqualByComparingTo("180.00");
        assertThat(db.queryForObject("select sum(haber) from partidas_asiento where asiento_id=?", BigDecimal.class, firstEntry)).isEqualByComparingTo("180.00");
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad_id=? and accion='PURCHASE_RECEIPT_CONFIRMED'", Integer.class, first)).isEqualTo(1);
        assertThat(db.queryForObject("select detalle::text from eventos_auditoria where entidad_id=? and accion='PURCHASE_RECEIPT_CONFIRMED'", String.class, first))
            .contains("PENDIENTE", "CONFIRMADA");
        assertThat(db.queryForObject("select detalle::text from eventos_auditoria where entidad_id=? and accion='PURCHASE_ORDER_RECEIPT_STATUS_CHANGED'", String.class, f.order()))
            .contains("ENVIADA", "PARCIALMENTE_RECIBIDA");
        long second = receipt(f,"B",2,0);
        assertThat(confirm(confirm, second)).isEqualTo(200);
        assertThat(db.queryForObject("select estado from ordenes_compra where id=?", String.class, f.order())).isEqualTo("RECIBIDA");
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?", BigDecimal.class, f.product())).isEqualByComparingTo("20");
        http.perform(get("/api/v1/purchase-orders/{id}", f.order()).header("Authorization", "Bearer " + reader))
            .andExpect(status().isOk()).andExpect(jsonPath("detalles[0].cantidadPendiente").value(0));
        assertThat(confirm(confirm, Long.MAX_VALUE)).isEqualTo(404);
    }
    @Test void simultaneousReceiptsCannotOverrunAndRepeatedConfirmationIsSafe() throws Exception {
        Fixture f = fixture(); String confirm = token("COMPRAS_RECEPCIONES_CONFIRMAR");
        long a = receipt(f,"C",15,0), b = receipt(f,"D",15,0);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Integer> first = () -> { start.await(); return confirm(confirm,a); };
            Callable<Integer> second = () -> { start.await(); return confirm(confirm,b); };
            var x=pool.submit(first); var y=pool.submit(second); start.countDown();
            assertThat(List.of(x.get(20,TimeUnit.SECONDS), y.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?", BigDecimal.class, f.product())).isEqualByComparingTo("15");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?", Integer.class, f.product())).isEqualTo(1);
        long winner = db.queryForObject("select id from recepciones_compra where orden_compra_id=? and estado='CONFIRMADA'", Long.class, f.order());
        var retryPool = Executors.newFixedThreadPool(2); var startAgain = new CountDownLatch(1);
        try {
            Callable<Integer> repeat = () -> { startAgain.await(); return confirm(confirm,winner); };
            var x=retryPool.submit(repeat); var y=retryPool.submit(repeat); startAgain.countDown();
            assertThat(List.of(x.get(20,TimeUnit.SECONDS), y.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally { retryPool.shutdownNow(); }
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?", Integer.class, f.product())).isEqualTo(1);
    }
    @Test void pendingReceiptAcceptsMaximumDocumentPriceInInventory() throws Exception {
        Fixture f = fixture(); String confirm = token("COMPRAS_RECEPCIONES_CONFIRMAR");
        long receipt = receipt(f, "MAX", 1, 0);
        db.update("update detalles_recepcion set precio_unitario_recibido=? where recepcion_id=?", new BigDecimal("999999999999.99"), receipt);
        assertThat(confirm(confirm, receipt)).isEqualTo(200);
        assertThat(db.queryForObject("select costo_promedio from existencias where producto_id=?", BigDecimal.class, f.product())).isEqualByComparingTo("999999999999.9900");
        assertThat(db.queryForObject("select costo_unitario from movimientos_inventario where producto_id=?", BigDecimal.class, f.product())).isEqualByComparingTo("999999999999.9900");
    }
    @Test void lateFailureRollsBackAllAndAllRejectedCreatesNoMovement() throws Exception {
        Fixture f = fixture(); String confirm = token("COMPRAS_RECEPCIONES_CONFIRMAR");
        long receipt = receipt(f,"E",1,0);
        long category = db.queryForObject("select categoria_id from productos where id=?", Long.class, f.product());
        long secondProduct = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, f.key()+"X", "Otro", category);
        long secondLine = db.queryForObject("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,?,?,?) returning id", Long.class, f.order(), secondProduct, 10, 10, 16);
        db.update("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,?,?,?,?)", receipt, secondLine, 1, 1, 0, new BigDecimal("10000000000.00"));
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica) values (?,?,?)", secondProduct, f.warehouse(), new BigDecimal("99999999999.999"));
        assertThat(confirm(confirm, receipt)).isEqualTo(409);
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id in (?,?)", Integer.class, f.product(), secondProduct)).isZero();
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?", Integer.class, f.product())).isZero();
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?", BigDecimal.class, secondProduct)).isEqualByComparingTo("99999999999.999");
        assertThat(db.queryForObject("select estado from recepciones_compra where id=?", String.class, receipt)).isEqualTo("PENDIENTE");
        assertThat(db.queryForObject("select estado from ordenes_compra where id=?", String.class, f.order())).isEqualTo("ENVIADA");
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='PURCHASE_RECEIPT_CONFIRMED' and entidad_id=?", Integer.class, receipt)).isZero();
        Fixture rejected = fixture(); long zero = receipt(rejected,"F",0,2);
        assertThat(confirm(confirm, zero)).isEqualTo(200);
        assertThat(db.queryForObject("select estado from ordenes_compra where id=?", String.class, rejected.order())).isEqualTo("ENVIADA");
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?", Integer.class, rejected.product())).isZero();
        Fixture invalid = fixture(); long pending = receipt(invalid,"G",2,0);
        db.update("update detalles_recepcion set cantidad_recibida=3 where recepcion_id=?", pending);
        assertThat(confirm(confirm, pending)).isEqualTo(409);
        db.update("update detalles_recepcion set cantidad_recibida=2 where recepcion_id=?", pending);
        db.update("update almacenes set activo=false where id=?", invalid.warehouse());
        assertThat(confirm(confirm, pending)).isEqualTo(409);
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?", Integer.class, invalid.product())).isZero();
    }
}
