package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MasterCatalogApiIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    private String token(String... authorities) throws Exception {
        String key = UUID.randomUUID().toString();
        String password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class,
            "T_" + key.substring(0, 20).toUpperCase(Locale.ROOT), "Test");
        for (String code : authorities) {
            db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?", role, code);
        }
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",
            Long.class, key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)", user, role);
        String result = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador", key, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("accessToken").asText();
    }

    private static String code() { return "M" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT); }

    @Test void allThreeCatalogsCreateUpdateFilterAndKeepInactiveRecords() throws Exception {
        for (String[] catalog : new String[][] {
                {"departments", "DEPARTAMENTOS", "departamentos"},
                {"suppliers", "PROVEEDORES", "proveedores"},
                {"warehouses", "ALMACENES", "almacenes"}}) {
            String route = "/api/v1/" + catalog[0];
            String auth = token("MAESTROS_" + catalog[1] + "_ADMIN", "MAESTROS_" + catalog[1] + "_LEER");
            String codigo = code();
            String body = body(catalog[0], " " + codigo.toLowerCase(Locale.ROOT) + " ", "Inicial");
            String created = http.perform(post(route).header("Authorization", "Bearer " + auth)
                .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("codigo").value(codigo)).andReturn().getResponse().getContentAsString();
            long id = json.readTree(created).get("id").asLong();
            http.perform(get(route + "/{id}", id).header("Authorization", "Bearer " + auth))
                .andExpect(status().isOk()).andExpect(jsonPath("activo").value(true));
            http.perform(put(route + "/{id}", id).header("Authorization", "Bearer " + auth)
                .contentType("application/json").content(body(catalog[0], codigo, "Actualizado")))
                .andExpect(status().isOk());
            http.perform(get(route).header("Authorization", "Bearer " + auth)
                .param("codigo", codigo.toLowerCase(Locale.ROOT)).param("sort", "codigo")
                .param("direction", "desc").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1))
                .andExpect(jsonPath("content[0].id").value(id));
            http.perform(patch(route + "/{id}/status", id).header("Authorization", "Bearer " + auth)
                .contentType("application/json").content("{\"activo\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("activo").value(false));
            http.perform(patch(route + "/{id}/status", id).header("Authorization", "Bearer " + auth)
                .contentType("application/json").content("{\"activo\":false,\"campoAdicional\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("activo").value(false));
            http.perform(get(route).header("Authorization", "Bearer " + auth)
                .param("codigo", codigo).param("activo", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
            http.perform(delete(route + "/{id}", id).header("Authorization", "Bearer " + auth))
                .andExpect(status().isMethodNotAllowed());
            assertThat(db.queryForObject("select count(*) from " + catalog[2] + " where id=?", Long.class, id)).isEqualTo(1L);
            assertThat(db.queryForObject("select count(*) from eventos_auditoria where modulo='MAESTROS' and entidad=? and entidad_id=?",
                Long.class, catalog[0].equals("departments") ? "DEPARTAMENTO" : catalog[0].equals("suppliers") ? "PROVEEDOR" : "ALMACEN", id)).isEqualTo(3L);
        }
    }

    @Test void validationConflictsAndPermissionsAreEnforced() throws Exception {
        for (String[] catalog : new String[][] {
                {"departments", "DEPARTAMENTOS"}, {"suppliers", "PROVEEDORES"}, {"warehouses", "ALMACENES"}}) {
            String route = "/api/v1/" + catalog[0];
            String admin = token("MAESTROS_" + catalog[1] + "_ADMIN");
            String read = token("MAESTROS_" + catalog[1] + "_LEER");
            String codigo = code();
            String body = body(catalog[0], codigo, "Nombre");
            http.perform(post(route).header("Authorization", "Bearer " + admin).contentType("application/json").content(body))
                .andExpect(status().isCreated());
            http.perform(post(route).header("Authorization", "Bearer " + admin).contentType("application/json").content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("code").value("CONFLICT"));
            http.perform(post(route).header("Authorization", "Bearer " + admin).contentType("application/json")
                .content(body(catalog[0], " ", " "))).andExpect(status().isBadRequest());
            http.perform(get(route + "/{id}", Long.MAX_VALUE).header("Authorization", "Bearer " + read))
                .andExpect(status().isNotFound());
            http.perform(get(route)).andExpect(status().isUnauthorized());
            http.perform(get(route).header("Authorization", "Bearer " + admin)).andExpect(status().isForbidden());
            http.perform(post(route).header("Authorization", "Bearer " + read).contentType("application/json").content(body))
                .andExpect(status().isForbidden());
            for (String[] invalid : new String[][] {{"size", "101"}, {"sort", "activo"}, {"activo", "yes"}}) {
                http.perform(get(route).header("Authorization", "Bearer " + read).param(invalid[0], invalid[1]))
                    .andExpect(status().isBadRequest());
            }
            http.perform(patch(route + "/{id}/status", 1).header("Authorization", "Bearer " + admin)
                .contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        }
    }

    @Test void supplierFiscalIdentifierIsUnique() throws Exception {
        String auth = token("MAESTROS_PROVEEDORES_ADMIN");
        String fiscal = code();
        for (int i = 0; i < 2; i++) {
            http.perform(post("/api/v1/suppliers").header("Authorization", "Bearer " + auth)
                .contentType("application/json").content(json.writeValueAsString(Map.of(
                    "codigo", code(), "nombreRazonSocial", "Proveedor", "identificadorFiscal", fiscal))))
                .andExpect(i == 0 ? status().isCreated() : status().isConflict());
        }
    }

    @Test void productLifecycleAndCategoryFiltering() throws Exception {
        String auth = token("MAESTROS_PRODUCTOS_ADMIN", "MAESTROS_PRODUCTOS_LEER");
        String sku = code();
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id", Long.class, code(), "Categoría");
        Map<String, Object> request = new java.util.HashMap<>(Map.of("sku", sku, "nombre", "Producto", "unidadMedida", "PIEZA",
            "categoriaId", category, "precioLista", 12.50, "costoReferencia", 10, "tasaImpuesto", 16,
            "puntoReorden", 1, "cantidadReorden", 2));
        String body = json.writeValueAsString(request);
        String result = http.perform(post("/api/v1/products").header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(body)).andExpect(status().isCreated())
            .andExpect(header().exists("Location")).andReturn().getResponse().getContentAsString();
        long id = json.readTree(result).get("id").asLong();
        http.perform(post("/api/v1/products").header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(body)).andExpect(status().isConflict());
        http.perform(get("/api/v1/products").header("Authorization", "Bearer " + auth)
            .param("categoriaId", Long.toString(category)).param("activo", "true"))
            .andExpect(status().isOk()).andExpect(jsonPath("content[0].id").value(id));
        request.put("nombre", "Actualizado");
        http.perform(put("/api/v1/products/{id}", id).header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isOk());
        http.perform(patch("/api/v1/products/{id}/status", id).header("Authorization", "Bearer " + auth)
            .contentType("application/json").content("{\"activo\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("activo").value(false));
        http.perform(get("/api/v1/products/{id}", id).header("Authorization", "Bearer " + auth))
            .andExpect(status().isOk()).andExpect(jsonPath("activo").value(false));
        request.put("sku", code()); request.put("precioLista", -1);
        http.perform(post("/api/v1/products").header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        request.put("precioLista", 1); request.put("categoriaId", Long.MAX_VALUE);
        http.perform(post("/api/v1/products").header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isNotFound());
        request.put("categoriaId", category); request.put("proveedorPrincipalId", Long.MAX_VALUE);
        http.perform(post("/api/v1/products").header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isNotFound());
        request.remove("proveedorPrincipalId"); request.put("costoReferencia", new java.math.BigDecimal("1.001"));
        http.perform(post("/api/v1/products").header("Authorization", "Bearer " + auth)
            .contentType("application/json").content(json.writeValueAsString(request))).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/products")).andExpect(status().isUnauthorized());
        String read = token("MAESTROS_PRODUCTOS_LEER");
        http.perform(post("/api/v1/products").header("Authorization", "Bearer " + read)
            .contentType("application/json").content(body)).andExpect(status().isForbidden());
        assertThat(db.queryForObject("select count(*) from productos where id=?", Long.class, id)).isEqualTo(1L);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where entidad='PRODUCTO' and entidad_id=?", Long.class, id)).isEqualTo(3L);
    }

    private String body(String catalog, String codigo, String nombre) throws Exception {
        return catalog.equals("suppliers")
            ? json.writeValueAsString(Map.of("codigo", codigo, "nombreRazonSocial", nombre))
            : json.writeValueAsString(Map.of("codigo", codigo, "nombre", nombre));
    }
}
