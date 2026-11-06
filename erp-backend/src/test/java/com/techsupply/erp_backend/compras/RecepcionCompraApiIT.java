package com.techsupply.erp_backend.compras;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
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
class RecepcionCompraApiIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http; @Autowired JdbcTemplate db; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
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
    @Test void registrationReplayValidationAndConcurrency() throws Exception {
        String key = "R" + UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id", Long.class, key, "Test");
        long product = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, key, "Test", category);
        long supplier = db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id", Long.class, key, "Test");
        long department = db.queryForObject("insert into departamentos(codigo,nombre) values (?,?) returning id", Long.class, key, "Test");
        long warehouse = db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id", Long.class, key, "Test");
        String username = key.toLowerCase(Locale.ROOT);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, username, username + "@example.test", "test-hash");
        long order = db.queryForObject("insert into ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values (?,?,?,?,?) returning id", Long.class, key, supplier, department, user, "ENVIADA");
        long line = db.queryForObject("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,?,?,?) returning id", Long.class, order, product, 20, 10, 16);
        String register = token("COMPRAS_RECEPCIONES_REGISTRAR", "COMPRAS_RECEPCIONES_LEER");
        String reader = token("COMPRAS_RECEPCIONES_LEER");
        Map<String,Object> detail = Map.of("detalleOrdenCompraId", line, "cantidadRecibida", 20, "cantidadAceptada", 18, "cantidadRechazada", 2, "precioUnitarioRecibido", 10);
        Map<String,Object> input = new HashMap<>(Map.of("ordenCompraId", order, "almacenId", warehouse, "claveIdempotencia", key, "detalles", List.of(detail)));
        String body = json.writeValueAsString(input);
        http.perform(get("/api/v1/purchase-receipts")).andExpect(status().isUnauthorized());
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + reader).contentType("application/json").content(body)).andExpect(status().isForbidden());
        String created = http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content(body))
            .andExpect(status().isCreated()).andExpect(header().exists("Location")).andExpect(jsonPath("estado").value("PENDIENTE"))
            .andReturn().getResponse().getContentAsString();
        long id = json.readTree(created).get("id").asLong();
        assertThat(json.readTree(created).get("folio").asText()).matches("RC-[0-9]{12}");
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("id").value(id));
        var normalizedDetail = new HashMap<>(detail);
        normalizedDetail.put("cantidadRecibida", new java.math.BigDecimal("20.000"));
        var normalized = new HashMap<>(input);
        normalized.put("claveIdempotencia", " " + key + " ");
        normalized.put("referenciaExterna", "  ");
        normalized.put("detalles", List.of(normalizedDetail));
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json")
            .content(json.writeValueAsString(normalized))).andExpect(status().isOk()).andExpect(jsonPath("id").value(id));
        http.perform(get("/api/v1/purchase-receipts/{id}", id).header("Authorization", "Bearer " + reader)).andExpect(status().isOk()).andExpect(jsonPath("detalles[0].cantidadAceptada").value(18));
        http.perform(get("/api/v1/purchase-receipts").header("Authorization", "Bearer " + reader).param("ordenCompraId", Long.toString(order))).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        input.put("referenciaExterna", "diferente");
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isConflict());
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='PURCHASE_RECEIPT_CREATED' and entidad_id=?", Integer.class, id)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?", Integer.class, product)).isZero();
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?", Integer.class, product)).isZero();
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content("{}")).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/purchase-receipts/{id}", Long.MAX_VALUE).header("Authorization", "Bearer " + reader)).andExpect(status().isNotFound());
        input.remove("referenciaExterna"); input.put("claveIdempotencia", key + "C");
        String concurrent = json.writeValueAsString(input);
        var pool = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            Callable<Integer> call = () -> { start.await(); return http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content(concurrent)).andReturn().getResponse().getStatus(); };
            var a = pool.submit(call); var b = pool.submit(call); start.countDown();
            assertThat(List.of(a.get(20,TimeUnit.SECONDS), b.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,201);
        } finally { pool.shutdownNow(); }
        assertThat(db.queryForObject("select count(*) from recepciones_compra where clave_idempotencia=?", Integer.class, key + "C")).isEqualTo(1);
        input.put("claveIdempotencia", key + "X");
        db.update("update almacenes set activo=false where id=?", warehouse);
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isConflict());
        db.update("update almacenes set activo=true where id=?", warehouse);
        input.put("detalles", List.of(Map.of("detalleOrdenCompraId", Long.MAX_VALUE, "cantidadRecibida", 1, "cantidadAceptada", 1, "cantidadRechazada", 0, "precioUnitarioRecibido", 1)));
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isNotFound());
        input.put("detalles", List.of(detail, detail));
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isConflict());
        input.put("detalles", List.of(Map.of("detalleOrdenCompraId", line, "cantidadRecibida", 2, "cantidadAceptada", 2, "cantidadRechazada", 1, "precioUnitarioRecibido", 1)));
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isBadRequest());
        input.put("detalles", List.of(detail));
        db.update("update ordenes_compra set estado='BORRADOR' where id=?", order);
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content(json.writeValueAsString(input))).andExpect(status().isConflict());
        http.perform(post("/api/v1/purchase-receipts").header("Authorization", "Bearer " + register).contentType("application/json").content(body))
            .andExpect(status().isOk()).andExpect(jsonPath("id").value(id));
    }
}
