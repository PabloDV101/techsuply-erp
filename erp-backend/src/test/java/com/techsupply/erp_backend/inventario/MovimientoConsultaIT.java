package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.application.MovimientoConsultaService;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class MovimientoConsultaIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http; @Autowired JdbcTemplate db; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    @Autowired MovimientoConsultaService service;
    record Fixture(String key, long product, long warehouse, long user, long receipt) {}
    private String token(String... permissions) throws Exception {
        String key = UUID.randomUUID().toString(), password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class, "T_" + key.substring(0,20).toUpperCase(Locale.ROOT), "Test");
        for (String permission : permissions) db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, permission);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        String body = http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador", key, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(body).get("accessToken").asText();
    }
    private Fixture fixture() {
        String key = "K" + UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        long product = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, key, "Prueba", category);
        long supplier = db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id", Long.class, key, "Prueba");
        long dept = db.queryForObject("insert into departamentos(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        long warehouse = db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        String username = key.toLowerCase(Locale.ROOT);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, username, username + "@example.test", "test-hash");
        long order = db.queryForObject("insert into ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values (?,?,?,?,?) returning id", Long.class, key, supplier, dept, user, "ENVIADA");
        long line = db.queryForObject("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,?,?,?) returning id", Long.class, order, product, 2, 10, 16);
        long receipt = db.queryForObject("insert into recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,?) returning id", Long.class, key, order, warehouse, user, key, "PENDIENTE");
        db.update("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,?,?,?,?)", receipt, line, 1, 1, 0, 10);
        return new Fixture(key, product, warehouse, user, receipt);
    }
    private long adjustmentMovement(Fixture f, String timestamp) {
        long adjustment = db.queryForObject("insert into ajustes_inventario(folio,almacen_id,motivo,solicitado_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,'PENDIENTE') returning id", Long.class, f.key(), f.warehouse(), "Fixture", f.user(), f.key());
        long detail = db.queryForObject("insert into detalles_ajuste_inventario(ajuste_inventario_id,producto_id,tipo,cantidad,costo_unitario) values (?,?,'POSITIVO',1,10) returning id", Long.class, adjustment, f.product());
        // Synthetic movement exercises the second SQL source without applying an adjustment.
        return db.queryForObject("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_ajuste_id,clave_operacion,fecha) values (?,?,'ENTRADA','AJUSTE',1,10,?,?,?,?::timestamptz) returning id", Long.class, f.product(), f.warehouse(), f.user(), detail, "AJUSTE:" + detail, timestamp);
    }
    @Test void lecturaPaginadaFiltradaYAutorizadaConFuentesYOrdenEstable() throws Exception {
        String reader = token("INVENTARIO_MOVIMIENTOS_LEER", "COMPRAS_RECEPCIONES_CONFIRMAR");
        String forbidden = token("INVENTARIO_EXISTENCIAS_LEER");
        Fixture f = fixture(), other = fixture();
        long source = db.queryForObject("select id from detalles_recepcion where recepcion_id=?", Long.class, f.receipt());
        assertThatThrownBy(() -> db.update("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_recepcion_id,clave_operacion) values (?,?,'ENTRADA','RECEPCION_COMPRA',1,10,?,?,?)", f.product(), other.warehouse(), f.user(), source, "WRONG:" + source))
            .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        http.perform(post("/api/v1/purchase-receipts/{id}/confirm", f.receipt()).header("Authorization", "Bearer " + reader)).andExpect(status().isOk());
        http.perform(post("/api/v1/purchase-receipts/{id}/confirm", other.receipt()).header("Authorization", "Bearer " + reader)).andExpect(status().isOk());
        long received = db.queryForObject("select m.id from movimientos_inventario m join detalles_recepcion d on d.id=m.detalle_recepcion_id where d.recepcion_id=?", Long.class, f.receipt());
        long receivedDetail = db.queryForObject("select detalle_recepcion_id from movimientos_inventario where id=?", Long.class, received);
        String receivedAt = db.queryForObject("select fecha::text from movimientos_inventario where id=?", String.class, received);
        long adjustment = adjustmentMovement(f, receivedAt);
        long adjustmentDetail = db.queryForObject("select detalle_ajuste_id from movimientos_inventario where id=?", Long.class, adjustment);
        String url = "/api/v1/inventory/movements";
        http.perform(get(url).header("Authorization", "Bearer " + reader)).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(greaterThanOrEqualTo(3)));
        http.perform(get(url)).andExpect(status().isUnauthorized());
        http.perform(get(url).header("Authorization", "Bearer " + forbidden)).andExpect(status().isForbidden());
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("productoId", "0")).andExpect(status().isBadRequest());
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("almacenId", "-1")).andExpect(status().isBadRequest());
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("size", "101")).andExpect(status().isBadRequest());
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("page", "-1")).andExpect(status().isBadRequest());
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("productoId", Long.toString(f.product())).param("almacenId", Long.toString(f.warehouse())).param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2)).andExpect(jsonPath("totalPages").value(2))
            .andExpect(jsonPath("content[0].id").value(adjustment)).andExpect(jsonPath("content[0].productoId").value(f.product()))
            .andExpect(jsonPath("content[0].almacenId").value(f.warehouse())).andExpect(jsonPath("content[0].motivo").value("AJUSTE"))
            .andExpect(jsonPath("content[0].tipo").value("ENTRADA")).andExpect(jsonPath("content[0].cantidad").value(1))
            .andExpect(jsonPath("content[0].costoUnitario").value(10)).andExpect(jsonPath("content[0].usuarioId").value(f.user()))
            .andExpect(jsonPath("content[0].fecha").isString()).andExpect(jsonPath("content[0].detalleAjusteId").value(adjustmentDetail))
            .andExpect(jsonPath("content[0].detalleRecepcionId").value(nullValue()));
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("productoId", Long.toString(f.product())).param("almacenId", Long.toString(f.warehouse())).param("size", "1").param("page", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(received)).andExpect(jsonPath("content[0].motivo").value("RECEPCION_COMPRA"))
            .andExpect(jsonPath("content[0].detalleRecepcionId").value(receivedDetail)).andExpect(jsonPath("content[0].detalleAjusteId").value(nullValue()));
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("productoId", Long.toString(f.product())))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(2));
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("almacenId", Long.toString(other.warehouse())))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("productoId", Long.toString(f.product())).param("almacenId", Long.toString(other.warehouse())))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(0));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("test", null, List.of()));
        try { assertThatThrownBy(() -> service.listar(0, 20, null, null)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
