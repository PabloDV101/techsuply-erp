package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Flujo backend con MockMvc y PostgreSQL real; los catálogos y usuarios son datos de prueba. */
@SpringBootTest @AutoConfigureMockMvc
class FlujoCompraInventarioIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    record Account(long id, String token) {}
    record Catalog(String key, long product, long supplier, long department, long warehouse) {}

    private long id(String query, Object... args) { return db.queryForObject(query, Long.class, args); }
    private Account account(String... permissions) throws Exception {
        String key = UUID.randomUUID().toString(), password = UUID.randomUUID().toString();
        long role = id("insert into roles(codigo,nombre) values (?,?) returning id", "T_" + key.substring(0,20).toUpperCase(Locale.ROOT), "Test");
        for (String permission : permissions) db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, permission);
        long user = id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        String response = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador",key,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(user,json.readTree(response).get("accessToken").asText());
    }
    private Catalog catalog() {
        String key = "V"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category=id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",key,"Prueba vertical");
        long product=id("insert into productos(sku,nombre,categoria_id,punto_reorden,cantidad_reorden) values (?,?,?,17,8) returning id",key,"Prueba vertical",category);
        long supplier=id("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id",key,"Prueba vertical");
        long department=id("insert into departamentos(codigo,nombre) values (?,?) returning id",key,"Prueba vertical");
        long warehouse=id("insert into almacenes(codigo,nombre) values (?,?) returning id",key,"Prueba vertical");
        return new Catalog(key,product,supplier,department,warehouse);
    }
    private String auth(Account account) { return "Bearer " + account.token(); }
    private ResultActions postJson(String url, Account account, Object payload) throws Exception {
        return http.perform(post(url).header("Authorization",auth(account)).contentType("application/json").content(json.writeValueAsString(payload)));
    }
    private JsonNode body(ResultActions actions) throws Exception { return json.readTree(actions.andReturn().getResponse().getContentAsString()); }
    private void stock(Account actor, Catalog f, int expected) throws Exception {
        http.perform(get("/api/v1/inventory/stock").header("Authorization",auth(actor)).param("productoId",Long.toString(f.product())).param("almacenId",Long.toString(f.warehouse())))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].cantidadFisica").value(expected))
            .andExpect(jsonPath("content[0].cantidadReservada").value(0)).andExpect(jsonPath("content[0].cantidadDisponible").value(expected));
    }
    private void alerts(Account actor, Catalog f, int count, int available) throws Exception {
        ResultActions result=http.perform(get("/api/v1/inventory/low-stock-alerts").header("Authorization",auth(actor)).param("productoId",Long.toString(f.product())))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(count));
        if(count==1) result.andExpect(jsonPath("content[0].disponibleGlobal").value(available)).andExpect(jsonPath("content[0].puntoReorden").value(17)).andExpect(jsonPath("content[0].cantidadReorden").value(8));
    }
    private int apply(Account actor,long adjustment) throws Exception {
        return http.perform(post("/api/v1/inventory/adjustments/{id}/apply",adjustment).header("Authorization",auth(actor))).andReturn().getResponse().getStatus();
    }

    @Test void ordenRecepcionConteoAjusteKardexYAlertaConRollbackYReintentos() throws Exception {
        Catalog f=catalog();
        Account operator=account("COMPRAS_ORDENES_ADMIN","COMPRAS_ORDENES_LEER","COMPRAS_RECEPCIONES_REGISTRAR","COMPRAS_RECEPCIONES_CONFIRMAR",
            "INVENTARIO_EXISTENCIAS_LEER","INVENTARIO_CONTEOS_ADMIN","INVENTARIO_AJUSTES_REGISTRAR","INVENTARIO_AJUSTES_APLICAR",
            "INVENTARIO_MOVIMIENTOS_LEER","INVENTARIO_ALERTAS_LEER");
        Account approver=account("COMPRAS_ORDENES_APROBAR","INVENTARIO_AJUSTES_APROBAR");
        Account reader=account("INVENTARIO_AJUSTES_LEER");
        alerts(operator,f,1,0);

        var orderRequest=Map.of("proveedorId",f.supplier(),"departamentoId",f.department(),"detalles",List.of(Map.of(
            "productoId",f.product(),"cantidad",20,"precioUnitario",10,"descuentoPct",0,"tasaImpuesto",16)));
        JsonNode order=body(postJson("/api/v1/purchase-orders",operator,orderRequest).andExpect(status().isCreated()));
        long orderId=order.get("id").asLong(), orderLine=order.get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/purchase-orders/{id}/submit",orderId).header("Authorization",auth(operator))).andExpect(status().isOk());
        http.perform(post("/api/v1/purchase-orders/{id}/approve",orderId).header("Authorization",auth(approver))).andExpect(status().isOk());
        http.perform(post("/api/v1/purchase-orders/{id}/send",orderId).header("Authorization",auth(operator))).andExpect(status().isOk());

        var receiptRequest=Map.of("ordenCompraId",orderId,"almacenId",f.warehouse(),"claveIdempotencia",f.key()+"REC", "detalles",List.of(Map.of(
            "detalleOrdenCompraId",orderLine,"cantidadRecibida",20,"cantidadAceptada",18,"cantidadRechazada",2,"precioUnitarioRecibido",10)));
        JsonNode receipt=body(postJson("/api/v1/purchase-receipts",operator,receiptRequest).andExpect(status().isCreated()));
        long receiptId=receipt.get("id").asLong(), receiptLine=receipt.get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/purchase-receipts/{id}/confirm",receiptId).header("Authorization",auth(operator)))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("CONFIRMADA")).andExpect(jsonPath("detalles[0].cantidadRechazada").value(2));
        http.perform(post("/api/v1/purchase-receipts/{id}/confirm",receiptId).header("Authorization",auth(operator))).andExpect(status().isOk());
        http.perform(get("/api/v1/purchase-orders/{id}",orderId).header("Authorization",auth(operator)))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("PARCIALMENTE_RECIBIDA")).andExpect(jsonPath("detalles[0].cantidadPendiente").value(2));
        stock(operator,f,18); alerts(operator,f,0,0);

        JsonNode count=body(postJson("/api/v1/inventory/counts",operator,Map.of("almacenId",f.warehouse(),"fechaProgramada","2026-11-10","clase","CICLICO")).andExpect(status().isCreated()));
        long countId=count.get("id").asLong();
        JsonNode capture=body(http.perform(put("/api/v1/inventory/counts/{id}/lines/{productoId}",countId,f.product()).header("Authorization",auth(operator))
            .contentType("application/json").content("{\"cantidadContada\":16}")).andExpect(status().isOk())
            .andExpect(jsonPath("detalles[0].cantidadSistema").value(18)).andExpect(jsonPath("detalles[0].diferencia").value(-2)));
        long countLine=capture.get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/inventory/counts/{id}/close",countId).header("Authorization",auth(operator))).andExpect(status().isOk());
        stock(operator,f,18);
        var adjustmentRequest=Map.of("almacenId",f.warehouse(),"motivo","Diferencia de conteo", "claveIdempotencia",f.key()+"ADJ", "detalles",List.of(Map.of(
            "productoId",f.product(),"tipo","NEGATIVO","cantidad",2,"detalleConteoId",countLine)));
        JsonNode adjustment=body(postJson("/api/v1/inventory/adjustments",operator,adjustmentRequest).andExpect(status().isCreated()));
        long adjustmentId=adjustment.get("id").asLong(),adjustmentLine=adjustment.get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/inventory/adjustments/{id}/approve",adjustmentId).header("Authorization",auth(approver))).andExpect(status().isOk());
        long deniedBefore=id("select count(*) from eventos_auditoria where accion='AUTHORIZATION_DENIED' and usuario_id=?",reader.id());
        assertThat(apply(reader,adjustmentId)).isEqualTo(403);
        assertThat(id("select count(*) from eventos_auditoria where accion='AUTHORIZATION_DENIED' and usuario_id=?",reader.id())).isEqualTo(deniedBefore+1);
        stock(operator,f,18);

        db.execute("alter table eventos_auditoria add constraint ck_test_vertical_apply_failure check (accion <> 'INVENTORY_ADJUSTMENT_APPLIED' or entidad_id <> " + adjustmentId + ") not valid");
        try {
            assertThat(apply(operator,adjustmentId)).isEqualTo(409);
            stock(operator,f,18);alerts(operator,f,0,0);
            assertThat(id("select count(*) from movimientos_inventario where producto_id=?",f.product())).isEqualTo(1);
            assertThat(db.queryForObject("select estado from ajustes_inventario where id=?",String.class,adjustmentId)).isEqualTo("APROBADO");
            assertThat(id("select version from existencias where producto_id=? and almacen_id=?",f.product(),f.warehouse())).isEqualTo(1);
            assertThat(id("select count(*) from detalles_ajuste_inventario where id=? and costo_unitario is null",adjustmentLine)).isEqualTo(1);
            assertThat(id("select count(*) from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_APPLIED' and entidad_id=?",adjustmentId)).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_vertical_apply_failure"); }

        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            Callable<Integer> repeat=()->{start.await();return apply(operator,adjustmentId);};
            var a=pool.submit(repeat);var b=pool.submit(repeat);start.countDown();
            assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally { pool.shutdownNow(); }
        stock(operator,f,16); alerts(operator,f,1,16);
        assertThat(id("select version from existencias where producto_id=? and almacen_id=?",f.product(),f.warehouse())).isEqualTo(2);
        assertThat(db.queryForObject("select costo_promedio from existencias where producto_id=? and almacen_id=?",BigDecimal.class,f.product(),f.warehouse())).isEqualByComparingTo("10");
        assertThat(id("select count(*) from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_APPLIED' and entidad_id=?",adjustmentId)).isEqualTo(1);
        assertThat(id("select count(*) from eventos_auditoria where accion='PURCHASE_RECEIPT_CONFIRMED' and entidad_id=?",receiptId)).isEqualTo(1);
        http.perform(get("/api/v1/inventory/movements").header("Authorization",auth(operator)).param("productoId",Long.toString(f.product())).param("almacenId",Long.toString(f.warehouse())))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2))
            .andExpect(jsonPath("content[0].motivo").value("AJUSTE")).andExpect(jsonPath("content[0].tipo").value("SALIDA"))
            .andExpect(jsonPath("content[0].cantidad").value(2)).andExpect(jsonPath("content[0].costoUnitario").value(10)).andExpect(jsonPath("content[0].detalleAjusteId").value(adjustmentLine))
            .andExpect(jsonPath("content[1].motivo").value("RECEPCION_COMPRA")).andExpect(jsonPath("content[1].tipo").value("ENTRADA"))
            .andExpect(jsonPath("content[1].cantidad").value(18)).andExpect(jsonPath("content[1].costoUnitario").value(10)).andExpect(jsonPath("content[1].detalleRecepcionId").value(receiptLine));
    }
}
