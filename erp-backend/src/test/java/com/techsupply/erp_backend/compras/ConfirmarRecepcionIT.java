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
    @Test void lateFailureRollsBackAllAndAllRejectedCreatesNoMovement() throws Exception {
        Fixture f = fixture(); String confirm = token("COMPRAS_RECEPCIONES_CONFIRMAR");
        long receipt = receipt(f,"E",1,0);
        long category = db.queryForObject("select categoria_id from productos where id=?", Long.class, f.product());
        long secondProduct = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, f.key()+"X", "Otro", category);
        long secondLine = db.queryForObject("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,?,?,?) returning id", Long.class, f.order(), secondProduct, 10, 10, 16);
        db.update("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,?,?,?,?)", receipt, secondLine, 1, 1, 0, new BigDecimal("10000000000.00"));
        assertThat(confirm(confirm, receipt)).isEqualTo(409);
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id in (?,?)", Integer.class, f.product(), secondProduct)).isZero();
        assertThat(db.queryForObject("select count(*) from existencias where producto_id in (?,?)", Integer.class, f.product(), secondProduct)).isZero();
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
