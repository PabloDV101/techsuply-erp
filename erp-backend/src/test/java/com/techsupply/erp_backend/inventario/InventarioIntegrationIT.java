package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.application.InventarioService;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import com.techsupply.erp_backend.security.infrastructure.security.SecurityActor;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class InventarioIntegrationIT extends PostgresIntegrationSupport {
    @Autowired InventarioService inventory; @Autowired JdbcTemplate db; @Autowired TransactionTemplate tx;
    @Autowired MockMvc http; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    private record Fixture(long product, long warehouse, long user, String username, String password, long first, long second) {}
    private Fixture fixture() {
        String key = "t" + UUID.randomUUID().toString().replace("-", "").substring(0,12);
        String code = key.toUpperCase(Locale.ROOT); String password = UUID.randomUUID().toString();
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id", Long.class, code, "Test");
        long product = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, code, "Test", category);
        long supplier = db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id", Long.class, code, "Test");
        long department = db.queryForObject("insert into departamentos(codigo,nombre) values (?,?) returning id", Long.class, code, "Test");
        long warehouse = db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id", Long.class, code, "Test");
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, key, key + "@example.test", passwords.encode(password));
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class, code, "Test");
        db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo in ('INVENTARIO_EXISTENCIAS_LEER','COMPRAS_RECEPCIONES_CONFIRMAR')", role);
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        long[] details = new long[2];
        for (int i=0; i<2; i++) {
            long order = db.queryForObject("insert into ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values (?,?,?,?,?) returning id", Long.class, code+i, supplier, department, user, "ENVIADA");
            long line = db.queryForObject("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,?,?,?) returning id", Long.class, order, product, 10, 10, 16);
            long receipt = db.queryForObject("insert into recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,?) returning id", Long.class, code+i, order, warehouse, user, code+i, "PENDIENTE");
            details[i] = db.queryForObject("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,?,?,?,?) returning id", Long.class, receipt, line, i+2, i+2, 0, (i+1)*10);
        }
        return new Fixture(product, warehouse, user, key, password, details[0], details[1]);
    }
    private void identity(Fixture f) {
        var auth = new UsernamePasswordAuthenticationToken(new SecurityActor(f.user(), f.username()), null,
            List.of(new SimpleGrantedAuthority("COMPRAS_RECEPCIONES_CONFIRMAR")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
    private void receive(Fixture f, long detail) {
        identity(f);
        try { tx.executeWithoutResult(status -> {
            inventory.registrarEntradaRecepcion(detail);
            db.update("update recepciones_compra set estado='CONFIRMADA',confirmada_en=now() where id=(select recepcion_id from detalles_recepcion where id=?)", detail);
        }); } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void concurrentFirstStockAndHttpQuery() throws Exception {
        Fixture f = fixture(); var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            var a = pool.submit(() -> { start.await(); receive(f, f.first()); return null; });
            var b = pool.submit(() -> { start.await(); receive(f, f.second()); return null; });
            start.countDown(); a.get(20, TimeUnit.SECONDS); b.get(20, TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=? and almacen_id=?", Integer.class, f.product(), f.warehouse())).isEqualTo(1);
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=? and almacen_id=?", BigDecimal.class, f.product(), f.warehouse())).isEqualByComparingTo("5.000");
        assertThat(db.queryForObject("select costo_promedio from existencias where producto_id=? and almacen_id=?", BigDecimal.class, f.product(), f.warehouse())).isEqualByComparingTo("16.0000");
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=?", Integer.class, f.product())).isEqualTo(2);
        http.perform(get("/api/v1/inventory/stock")).andExpect(status().isUnauthorized());
        String result = http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador", f.username(), "password", f.password()))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(result).get("accessToken").asText();
        http.perform(get("/api/v1/inventory/stock").header("Authorization", "Bearer " + token).param("productoId", Long.toString(f.product())).param("almacenId", Long.toString(f.warehouse())))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].cantidadDisponible").value(5));
        http.perform(get("/api/v1/inventory/stock").header("Authorization", "Bearer " + token).param("size", "101")).andExpect(status().isBadRequest());
        String other = UUID.randomUUID().toString();
        db.update("insert into usuarios(username,correo,password_hash) values (?,?,?)", other, other + "@example.test", passwords.encode(f.password()));
        String deniedLogin = http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador", other, "password", f.password()))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        http.perform(get("/api/v1/inventory/stock").header("Authorization", "Bearer " + json.readTree(deniedLogin).get("accessToken").asText()))
            .andExpect(status().isForbidden());
    }
    @Test void rollbackAndDuplicateDoNotLeavePartialChanges() {
        Fixture f = fixture(); identity(f);
        try {
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> { inventory.registrarEntradaRecepcion(f.first()); throw new IllegalStateException("rollback"); })).isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> inventory.registrarEntradaRecepcion(f.first())))
                .isInstanceOf(IllegalStateException.class);
            assertThat(db.queryForObject("select count(*) from existencias where producto_id=?", Integer.class, f.product())).isZero();
            assertThat(db.queryForObject("select count(*) from movimientos_inventario where detalle_recepcion_id=?", Integer.class, f.first())).isZero();
        } finally { SecurityContextHolder.clearContext(); }
        receive(f, f.first());
        assertThatThrownBy(() -> receive(f, f.first())).isInstanceOf(RuntimeException.class);
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where detalle_recepcion_id=?", Integer.class, f.first())).isEqualTo(1);
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=?", BigDecimal.class, f.product())).isEqualByComparingTo("2");
    }

    @Test void internalEntryRequiresPermissionTransactionAndActiveWarehouse() {
        Fixture f = fixture();
        identity(f);
        try {
            assertThatThrownBy(() -> inventory.registrarEntradaRecepcion(f.first())).isInstanceOf(org.springframework.transaction.IllegalTransactionStateException.class);
            db.update("update almacenes set activo=false where id=?", f.warehouse());
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> inventory.registrarEntradaRecepcion(f.first())))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
            assertThat(db.queryForObject("select count(*) from movimientos_inventario where detalle_recepcion_id=?", Integer.class, f.first())).isZero();
            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                new SecurityActor(f.user(), f.username()), null, List.of(new SimpleGrantedAuthority("INVENTARIO_EXISTENCIAS_LEER"))));
            assertThatThrownBy(() -> tx.executeWithoutResult(status -> inventory.registrarEntradaRecepcion(f.first())))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        } finally { SecurityContextHolder.clearContext(); }
    }
}
