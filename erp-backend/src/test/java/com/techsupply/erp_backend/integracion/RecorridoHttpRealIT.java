package com.techsupply.erp_backend.integracion;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;

/** Flujo completo por socket local contra el servidor embebido y PostgreSQL desechable. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RecorridoHttpRealIT extends PostgresIntegrationSupport {
    @DynamicPropertySource
    static void isolatedSchema(DynamicPropertyRegistry registry) {
        String schema = "recorrido_" + UUID.randomUUID().toString().replace("-", "");
        registry.add("spring.flyway.schemas", () -> schema);
        registry.add("spring.flyway.default-schema", () -> schema);
        registry.add("spring.datasource.hikari.connection-init-sql", () -> "SET search_path TO " + schema);
    }
    @LocalServerPort int port;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    private final HttpClient client = HttpClient.newHttpClient();
    private String token;
    private String key() { return "E" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT); }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private long account(String type, String nature) {
        return id("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?,?,?,?) returning id", key(), "Cuenta prueba", type, nature);
    }
    private JsonNode call(String method, String path, Object body, int status, String bearer) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (bearer != null) request.header("Authorization", "Bearer " + bearer);
        if (body != null) request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s: %s", method, path, response.body()).isEqualTo(status);
        return response.body().isBlank() ? null : json.readTree(response.body());
    }
    private JsonNode call(String method, String path, Object body, int status) throws Exception {
        return call(method, path, body, status, token);
    }
    private void audit(String action, long entity) {
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion=? and entidad_id=?", Integer.class, action, entity)).as(action).isEqualTo(1);
    }
    private BigDecimal indicator(JsonNode dashboard, String code) {
        for (JsonNode item : dashboard.get("indicators"))
            if (code.equals(item.get("code").asText())) return item.get("value").decimalValue();
        throw new AssertionError("Indicador ausente: " + code);
    }
    @Test void compraHastaCxCYReportesConPagoProveedor() throws Exception {
        String k=key(), name=k.toLowerCase(Locale.ROOT), password=UUID.randomUUID().toString();
        long role=id("insert into roles(codigo,nombre) values (?,?) returning id", key(), "Integracion");
        String[] permissions={"COMPRAS_ORDENES_ADMIN","COMPRAS_ORDENES_LEER","COMPRAS_ORDENES_APROBAR",
            "COMPRAS_RECEPCIONES_REGISTRAR","COMPRAS_RECEPCIONES_CONFIRMAR","COMPRAS_FACTURAS_PROVEEDOR_REGISTRAR",
            "COMPRAS_FACTURAS_PROVEEDOR_VALIDAR","COMPRAS_FACTURAS_PROVEEDOR_LEER","CONTABILIDAD_CXP_CONFIGURAR",
            "CONTABILIDAD_CXP_LEER","CONTABILIDAD_PAGOS_PROVEEDOR_REGISTRAR","CONTABILIDAD_PAGOS_PROVEEDOR_CONFIRMAR",
            "CONTABILIDAD_PAGOS_PROVEEDOR_CONFIGURAR","VENTAS_COTIZACIONES_REGISTRAR","VENTAS_COTIZACIONES_ADMIN",
            "VENTAS_PEDIDOS_REGISTRAR","VENTAS_PEDIDOS_CONFIRMAR","VENTAS_DESPACHOS_REGISTRAR","VENTAS_DESPACHOS_CONFIRMAR",
            "VENTAS_FACTURAS_EMITIR","VENTAS_FACTURAS_LEER","CONTABILIDAD_INTEGRACIONES_ADMIN",
            "CONTABILIDAD_INTEGRACIONES_REINTENTAR","CONTABILIDAD_CXC_LEER",
            "CONTABILIDAD_ASIENTOS_REGISTRAR","CONTABILIDAD_ASIENTOS_CONTABILIZAR"};
        for(String permission:permissions) assertThat(db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",role,permission)).as(permission).isEqualTo(1);
        long user=id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",name,name+"@example.test",passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",user,role);
        String approverName=key().toLowerCase(Locale.ROOT), approverPassword=UUID.randomUUID().toString();
        long approver=id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",approverName,approverName+"@example.test",passwords.encode(approverPassword));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",approver,role);
        configureInventoryAccounting(db,user);
        token=call("POST","/api/v1/auth/login",Map.of("identificador",name,"password",password),200,null).get("accessToken").asText();
        String approverToken=call("POST","/api/v1/auth/login",Map.of("identificador",approverName,"password",approverPassword),200,null).get("accessToken").asText();
        String date=LocalDate.now(ZoneId.of("America/Mexico_City")).toString();
        String report="/api/v1/reports/dashboard?from="+date+"&to="+date;
        call("GET",report,null,401,null);
        String deniedName=key().toLowerCase(Locale.ROOT), deniedPassword=UUID.randomUUID().toString();
        id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",deniedName,deniedName+"@example.test",passwords.encode(deniedPassword));
        String denied=call("POST","/api/v1/auth/login",Map.of("identificador",deniedName,"password",deniedPassword),200,null).get("accessToken").asText();
        call("GET",report,null,403,denied);
        JsonNode baseline=call("GET",report,null,200);
        BigDecimal purchasesBefore=indicator(baseline,"COMPRAS_FACTURADO_SIN_IMPUESTOS");
        BigDecimal salesBefore=indicator(baseline,"VENTAS_FACTURADO_SIN_IMPUESTOS");
        long category=id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",key(),"Categoria");
        long product=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key(),"Producto",category);
        long supplier=id("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id",key(),"Proveedor");
        long department=id("insert into departamentos(codigo,nombre) values (?,?) returning id",key(),"Departamento");
        long warehouse=id("insert into almacenes(codigo,nombre) values (?,?) returning id",key(),"Almacen");
        long customer=id("insert into clientes(codigo,nombre_razon_social) values (?,?) returning id",key(),"Cliente");
        long vat=account("ACTIVO","DEUDORA"), payable=account("PASIVO","ACREEDORA"), funds=account("ACTIVO","DEUDORA");
        long receivable=account("ACTIVO","DEUDORA"), sales=account("INGRESO","ACREEDORA"), taxes=account("PASIVO","ACREEDORA");
        call("PUT","/api/v1/accounting/supplier-invoice-accounts",Map.of("ivaAcreditableCuentaId",vat,"cuentasPorPagarCuentaId",payable),200);
        call("PUT","/api/v1/accounting/supplier-payments/accounts",Map.of("cuentaFondosId",funds),200);
        call("PUT","/api/v1/accounting/integrations/sales-invoices/accounts",Map.of("clientesPorCobrarCuentaId",receivable,"ventasCuentaId",sales,"impuestosTrasladadosCuentaId",taxes),200);
        JsonNode purchase=call("POST","/api/v1/purchase-orders",Map.of("proveedorId",supplier,"departamentoId",department,"detalles",List.of(Map.of("productoId",product,"cantidad",20,"precioUnitario",10,"descuentoPct",0,"tasaImpuesto",16))),201);
        long po=purchase.get("id").asLong(), pol=purchase.get("detalles").get(0).get("id").asLong();
        call("POST","/api/v1/purchase-orders/"+po+"/submit",null,200);
        call("POST","/api/v1/purchase-orders/"+po+"/approve",null,200,approverToken);
        call("POST","/api/v1/purchase-orders/"+po+"/send",null,200);
        JsonNode receipt=call("POST","/api/v1/purchase-receipts",Map.of("ordenCompraId",po,"almacenId",warehouse,"claveIdempotencia",key(),"detalles",List.of(Map.of("detalleOrdenCompraId",pol,"cantidadRecibida",20,"cantidadAceptada",20,"cantidadRechazada",0,"precioUnitarioRecibido",10))),201);
        long receiptId=receipt.get("id").asLong();
        call("POST","/api/v1/purchase-receipts/"+receiptId+"/confirm",null,200);
        JsonNode supplierInvoice=call("POST","/api/v1/supplier-invoices",Map.of("ordenCompraId",po,"numeroFacturaProveedor",key(),"fechaEmision",date,"fechaVencimiento",date,"detalles",List.of(Map.of("detalleOrdenCompraId",pol,"cantidadFacturada",20,"precioUnitario",10,"descuentoPct",0,"tasaImpuesto",16))),201);
        long supplierInvoiceId=supplierInvoice.get("id").asLong();
        call("POST","/api/v1/supplier-invoices/"+supplierInvoiceId+"/validate",null,200);
        long cxp=id("select id from cuentas_por_pagar where factura_proveedor_id=?",supplierInvoiceId);
        long payment=call("POST","/api/v1/accounting/supplier-payments",Map.of("proveedorId",supplier,"fechaPago",date+"T12:00:00Z","monto",100,"medio","TRANSFERENCIA","claveIdempotencia",key()),200).get("id").asLong();
        call("POST","/api/v1/accounting/supplier-payments/"+payment+"/confirm",Map.of("aplicaciones",List.of(Map.of("cuentaPorPagarId",cxp,"monto",100))),403,denied);
        call("POST","/api/v1/accounting/supplier-payments/"+payment+"/confirm",Map.of("aplicaciones",List.of(Map.of("cuentaPorPagarId",cxp,"monto",100))),200);
        assertThat(call("GET","/api/v1/accounting/payables/"+cxp,null,200).get("saldoPendiente").decimalValue()).isEqualByComparingTo("132");
        JsonNode quote=call("POST","/api/v1/sales/quotes",Map.of("clienteId",customer,"vigenciaHasta",LocalDate.parse(date).plusDays(1).toString(),"claveIdempotencia",key(),"detalles",List.of(Map.of("productoId",product,"cantidad",3,"precioUnitario",10,"descuentoPct",10,"tasaImpuesto",16))),201);
        long quoteId=quote.get("id").asLong();
        for(String action:List.of("issue","accept")) call("POST","/api/v1/sales/quotes/"+quoteId+"/"+action,null,200);
        JsonNode order=call("POST","/api/v1/sales/orders",Map.of("cotizacionId",quoteId,"almacenId",warehouse,"modalidad","CONTADO","claveIdempotencia",key()),201);
        long orderId=order.get("id").asLong(), line=order.get("detalles").get(0).get("id").asLong();
        call("POST","/api/v1/sales/orders/"+orderId+"/confirm",null,200);
        long dispatch=call("POST","/api/v1/sales/dispatches",Map.of("pedidoId",orderId,"claveIdempotencia",key(),"detalles",List.of(Map.of("detallePedidoId",line,"cantidad",3))),201).get("id").asLong();
        call("POST","/api/v1/sales/dispatches/"+dispatch+"/confirm",null,200);
        long invoice=call("POST","/api/v1/sales/invoices",Map.of("pedidoId",orderId,"claveIdempotencia",key()),201).get("id").asLong();
        call("POST","/api/v1/accounting/integrations/sales-invoices/"+invoice+"/retry",Map.of(),200);
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=? and almacen_id=?",BigDecimal.class,product,warehouse)).isEqualByComparingTo("17");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?",Integer.class,product)).isEqualTo(2);
        assertThat(db.queryForObject("select monto_original from cuentas_por_cobrar where factura_venta_id=?",BigDecimal.class,invoice)).isEqualByComparingTo("31.32");
        assertThat(db.queryForObject("select monto_original from cuentas_por_pagar where id=?",BigDecimal.class,cxp)).isEqualByComparingTo("232");
        assertThat(db.queryForObject("select sum(monto_aplicado) from aplicaciones_pago_proveedor where pago_proveedor_id=?",BigDecimal.class,payment)).isEqualByComparingTo("100");
        assertThat(db.queryForObject("select count(*) from asientos_contables where origen_modulo='COMPRAS' and origen_tipo='RECEPCION_COMPRA' and origen_id=? and estado='CONTABILIZADO'",Integer.class,receiptId)).isEqualTo(1);
        assertThat(db.queryForObject("select estado from asientos_contables where id=(select asiento_contable_id from facturas_proveedor where id=?)",String.class,supplierInvoiceId)).isEqualTo("CONTABILIZADO");
        assertThat(db.queryForObject("select estado from asientos_contables where id=(select asiento_contable_id from pagos_proveedor where id=?)",String.class,payment)).isEqualTo("CONTABILIZADO");
        assertThat(db.queryForObject("select count(*) from asientos_contables where origen_modulo='VENTAS' and origen_tipo='FACTURA_VENTA' and origen_id=? and estado='CONTABILIZADO'",Integer.class,invoice)).isEqualTo(1);
        assertThat(call("GET","/api/v1/accounting/accounts-receivable?clienteId="+customer,null,200).get("totalElements").asInt()).isEqualTo(1);
        audit("PURCHASE_RECEIPT_CONFIRMED",receiptId); audit("PURCHASE_SUPPLIER_INVOICE_VALIDATED",supplierInvoiceId);
        audit("ACCOUNTING_SUPPLIER_PAYMENT_CONFIRMED",payment); audit("SALES_DISPATCH_CONFIRMED",dispatch);
        audit("SALES_INVOICE_ISSUED",invoice); audit("ACCOUNTING_SALES_INVOICE_POSTED",invoice);
        JsonNode dashboard=call("GET",report,null,200);
        assertThat(indicator(dashboard,"COMPRAS_FACTURADO_SIN_IMPUESTOS")).isEqualByComparingTo(purchasesBefore.add(new BigDecimal("200")));
        assertThat(indicator(dashboard,"VENTAS_FACTURADO_SIN_IMPUESTOS")).isEqualByComparingTo(salesBefore.add(new BigDecimal("27")));
    }
}
