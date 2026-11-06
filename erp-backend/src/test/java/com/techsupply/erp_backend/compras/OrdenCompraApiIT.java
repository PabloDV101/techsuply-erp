package com.techsupply.erp_backend.compras;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.*;
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
class OrdenCompraApiIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http; @Autowired JdbcTemplate db; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    private String token(String... permissions) throws Exception {
        String key = UUID.randomUUID().toString(); String password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class, "T_" + key.substring(0,20).toUpperCase(Locale.ROOT), "Test");
        for (String permission : permissions) db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, permission);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        String body = http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador", key, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
    @Test void lifecycleValidationsSecurityAndAudit() throws Exception {
        String key = "T" + UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id", Long.class, key, "Test");
        long product = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, key, "Test", category);
        long supplier = db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id", Long.class, key, "Test");
        long department = db.queryForObject("insert into departamentos(codigo,nombre) values (?,?) returning id", Long.class, key, "Test");
        String creator = token("COMPRAS_ORDENES_ADMIN", "COMPRAS_ORDENES_LEER", "COMPRAS_ORDENES_APROBAR");
        String approver = token("COMPRAS_ORDENES_APROBAR");
        String reader = token("COMPRAS_ORDENES_LEER");
        Map<String,Object> line = Map.of("productoId", product, "cantidad", 3, "precioUnitario", 10, "descuentoPct", 10, "tasaImpuesto", 16);
        Map<String,Object> request = Map.of("proveedorId", supplier, "departamentoId", department, "detalles", List.of(line));
        String payload = json.writeValueAsString(request);
        http.perform(get("/api/v1/purchase-orders")).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/purchase-orders").header("Authorization", "Bearer " + reader).contentType("application/json").content(payload)).andExpect(status().isForbidden());
        String response = http.perform(post("/api/v1/purchase-orders").header("Authorization", "Bearer " + creator).contentType("application/json").content(payload))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("total").value(31.32)).andReturn().getResponse().getContentAsString();
        long id = json.readTree(response).get("id").asLong();
        assertThat(json.readTree(response).get("folio").asText()).matches("OC-[0-9]{12}");
        http.perform(get("/api/v1/purchase-orders").header("Authorization", "Bearer " + reader).param("proveedorId", Long.toString(supplier)).param("estado", "BORRADOR"))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].detalles[0].cantidadPendiente").value(3));
        http.perform(post("/api/v1/purchase-orders/{id}/send", id).header("Authorization", "Bearer " + creator)).andExpect(status().isConflict());
        http.perform(post("/api/v1/purchase-orders/{id}/submit", id).header("Authorization", "Bearer " + creator)).andExpect(status().isOk());
        http.perform(post("/api/v1/purchase-orders/{id}/approve", id).header("Authorization", "Bearer " + creator)).andExpect(status().isConflict());
        http.perform(post("/api/v1/purchase-orders/{id}/approve", id).header("Authorization", "Bearer " + reader)).andExpect(status().isForbidden());
        http.perform(post("/api/v1/purchase-orders/{id}/approve", id).header("Authorization", "Bearer " + approver)).andExpect(status().isOk()).andExpect(jsonPath("estado").value("APROBADA"));
        http.perform(post("/api/v1/purchase-orders/{id}/send", id).header("Authorization", "Bearer " + creator)).andExpect(status().isOk()).andExpect(jsonPath("estado").value("ENVIADA"));
        http.perform(get("/api/v1/purchase-orders/{id}", id).header("Authorization", "Bearer " + reader)).andExpect(status().isOk()).andExpect(jsonPath("detalles[0].precioUnitario").value(10));
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad='ORDEN_COMPRA' and entidad_id=?", Integer.class, id)).isEqualTo(4);
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?", Integer.class, product)).isZero();
        db.update("update proveedores set activo=false where id=?", supplier);
        http.perform(post("/api/v1/purchase-orders").header("Authorization", "Bearer " + creator).contentType("application/json").content(payload)).andExpect(status().isConflict());
        http.perform(post("/api/v1/purchase-orders").header("Authorization", "Bearer " + creator).contentType("application/json").content("{}" )).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/purchase-orders/{id}", Long.MAX_VALUE).header("Authorization", "Bearer " + reader)).andExpect(status().isNotFound());
        db.update("update proveedores set activo=true where id=?", supplier);
        http.perform(post("/api/v1/purchase-orders").header("Authorization", "Bearer " + creator).contentType("application/json")
            .content(json.writeValueAsString(Map.of("proveedorId", supplier, "departamentoId", department, "detalles", List.of(line, line)))))
            .andExpect(status().isConflict());
        http.perform(post("/api/v1/purchase-orders").header("Authorization", "Bearer " + creator).contentType("application/json")
            .content(json.writeValueAsString(Map.of("proveedorId", Long.MAX_VALUE, "departamentoId", department, "detalles", List.of(line)))))
            .andExpect(status().isNotFound());
        db.update("update productos set activo=false where id=?", product);
        http.perform(post("/api/v1/purchase-orders").header("Authorization", "Bearer " + creator).contentType("application/json").content(payload)).andExpect(status().isConflict());
        db.update("update productos set activo=true where id=?", product);
        long warehouse = db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id", Long.class, key, "Test");
        long userId = db.queryForObject("select creada_por_usuario_id from ordenes_compra where id=?", Long.class, id);
        long lineId = db.queryForObject("select id from detalles_orden_compra where orden_compra_id=?", Long.class, id);
        long receipt = db.queryForObject("insert into recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,?) returning id", Long.class, key, id, warehouse, userId, key, "CONFIRMADA");
        db.update("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,?,?,?,?)", receipt, lineId, 2, 2, 0, 10);
        http.perform(get("/api/v1/purchase-orders/{id}", id).header("Authorization", "Bearer " + reader)).andExpect(status().isOk())
            .andExpect(jsonPath("detalles[0].cantidadAceptada").value(2)).andExpect(jsonPath("detalles[0].cantidadPendiente").value(1));
        http.perform(get("/api/v1/purchase-orders").header("Authorization", "Bearer " + reader).param("proveedorId", Long.toString(supplier)))
            .andExpect(status().isOk()).andExpect(jsonPath("content[0].detalles[0].cantidadPendiente").value(1));
        long secondProduct = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, key + "X", "Otro", category);
        Map<String,Object> secondLine = Map.of("productoId", secondProduct, "cantidad", 1, "precioUnitario", 5, "descuentoPct", 0, "tasaImpuesto", 0);
        String multiple = json.writeValueAsString(Map.of("proveedorId", supplier, "departamentoId", department, "detalles", List.of(line, secondLine)));
        String created = http.perform(post("/api/v1/purchase-orders").header("Authorization", "Bearer " + creator).contentType("application/json").content(multiple))
            .andExpect(status().isCreated()).andExpect(jsonPath("detalles.length()").value(2)).andReturn().getResponse().getContentAsString();
        long concurrentId = json.readTree(created).get("id").asLong();
        var pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> submit = () -> { start.await(); return http.perform(post("/api/v1/purchase-orders/{id}/submit", concurrentId)
                .header("Authorization", "Bearer " + creator)).andReturn().getResponse().getStatus(); };
            var first = pool.submit(submit); var second = pool.submit(submit); start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder(200, 409);
            assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad_id=? and accion='PURCHASE_ORDER_SUBMITTED'", Integer.class, concurrentId)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
}
