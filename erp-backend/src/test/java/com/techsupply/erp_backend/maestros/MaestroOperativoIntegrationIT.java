package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MaestroOperativoIntegrationIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired PasswordEncoder passwords;

    @Test
    void supplierAndProductResourcesCreatePersistSearchAndRequireAuthentication() throws Exception {
        String token = token("MAESTROS_PROVEEDORES_LEER", "PROVEEDOR_WRITE",
            "MAESTROS_PRODUCTOS_LEER", "PRODUCTO_WRITE");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        String supplierCode = "P" + suffix;
        String supplierTaxId = "R" + suffix;

        String supplier = http.perform(post("/api/v1/proveedores")
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "codigo", supplierCode,
                    "ruc", supplierTaxId,
                    "razonSocial", "Proveedor de prueba " + suffix
                ))))
            .andExpect(status().isCreated())
            .andExpect(header().exists("Location"))
            .andExpect(jsonPath("codigo").value(supplierCode))
            .andReturn().getResponse().getContentAsString();
        long supplierId = json.readTree(supplier).path("id").asLong();
        assertThat(db.queryForObject("select razon_social from proveedores where id=?", String.class, supplierId))
            .isEqualTo("Proveedor de prueba " + suffix);
        http.perform(get("/api/v1/proveedores")
                .param("search", supplierCode)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("totalElements").value(1))
            .andExpect(jsonPath("content[0].id").value(supplierId));

        long categoryId = db.queryForObject(
            "insert into categorias_producto(codigo,nombre) values (?,?) returning id",
            Long.class, "C" + suffix, "Categoría de prueba " + suffix);
        String productCode = "PR" + suffix;
        String product = http.perform(post("/api/v1/productos")
                .header("Authorization", bearer(token))
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of(
                    "codigo", productCode,
                    "categoriaId", categoryId,
                    "descripcion", "Producto de prueba " + suffix,
                    "unidadMedida", "pieza",
                    "precioReferencia", new BigDecimal("125.50")
                ))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("codigo").value(productCode))
            .andReturn().getResponse().getContentAsString();
        long productId = json.readTree(product).path("id").asLong();
        assertThat(db.queryForObject("select precio_referencia from productos where id=?", BigDecimal.class, productId))
            .isEqualByComparingTo("125.50");
        http.perform(get("/api/v1/productos")
                .param("search", productCode)
                .header("Authorization", bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("totalElements").value(1))
            .andExpect(jsonPath("content[0].id").value(productId));

        http.perform(get("/api/v1/proveedores"))
            .andExpect(status().isUnauthorized());
    }

    private String token(String... authorities) throws Exception {
        String key = UUID.randomUUID().toString().replace("-", "");
        String password = UUID.randomUUID().toString();
        long role = db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id", Long.class,
            "T_" + key.substring(0, 20).toUpperCase(Locale.ROOT), "Test");
        for (String code : authorities) {
            db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",
                role, code);
        }
        db.update("insert into usuarios(username,correo,password_hash) values (?,?,?)",
            key, key + "@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) "
                + "select u.id,? from usuarios u where u.username=?",
            role, key);
        String response = http.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content(json.writeValueAsString(Map.of("identificador", key, "password", password))))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        return json.readTree(response).path("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
