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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CategoriaProductoIntegrationIT extends PostgresIntegrationSupport {
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

    private static String code() {
        return "C" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private String body(String code, String name, Long parent) {
        try {
            return json.writeValueAsString(new CategoryInput(code, name, null, parent));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record CategoryInput(String codigo, String nombre, String descripcion, Long categoriaPadreId) {}

    private long create(String token, String code, Long parent) throws Exception {
        String result = http.perform(post("/api/v1/categories").header("Authorization", "Bearer " + token)
            .contentType("application/json").content(body(code, "Categoría", parent)))
            .andExpect(status().isCreated()).andExpect(header().exists("Location"))
            .andExpect(jsonPath("codigo").value(code))
            .andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }

    private ResultActions update(String token, long id, String code, Long parent) throws Exception {
        return http.perform(put("/api/v1/categories/{id}", id).header("Authorization", "Bearer " + token)
            .contentType("application/json").content(body(code, "Actualizada", parent)));
    }

    @Test void createReadUpdateAndDeactivateWithoutPhysicalDeletion() throws Exception {
        String token = token("MAESTROS_CATEGORIAS_ADMIN", "MAESTROS_CATEGORIAS_LEER");
        String code = code();
        long id = create(token, code, null);
        assertThat(db.queryForObject("select codigo from categorias_producto where id=?", String.class, id)).isEqualTo(code);
        http.perform(get("/api/v1/categories/{id}", id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("nombre").value("Categoría"));
        update(token, id, code, null).andExpect(status().isOk())
            .andExpect(jsonPath("nombre").value("Actualizada"));
        http.perform(patch("/api/v1/categories/{id}/status", id).header("Authorization", "Bearer " + token)
            .contentType("application/json").content("{\"activa\":false}"))
            .andExpect(status().isOk()).andExpect(jsonPath("activa").value(false));
        http.perform(get("/api/v1/categories/{id}", id).header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("activa").value(false));
        http.perform(delete("/api/v1/categories/{id}", id).header("Authorization", "Bearer " + token))
            .andExpect(status().isMethodNotAllowed());
        assertThat(db.queryForObject("select count(*) from categorias_producto where id=?", Long.class, id)).isEqualTo(1L);
    }

    @Test void validationConflictAndMissingResource() throws Exception {
        String token = token("MAESTROS_CATEGORIAS_ADMIN", "MAESTROS_CATEGORIAS_LEER");
        String code = code();
        create(token, code, null);
        http.perform(post("/api/v1/categories").header("Authorization", "Bearer " + token)
            .contentType("application/json").content(body(code, "Duplicado", null)))
            .andExpect(status().isConflict()).andExpect(jsonPath("code").value("CONFLICT"));
        http.perform(post("/api/v1/categories").header("Authorization", "Bearer " + token)
            .contentType("application/json").content("{\"codigo\":\"\",\"nombre\":\" \"}"))
            .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("BAD_REQUEST"));
        http.perform(post("/api/v1/categories").header("Authorization", "Bearer " + token)
            .contentType("application/json").content(body(code(), "Hija", Long.MAX_VALUE)))
            .andExpect(status().isNotFound()).andExpect(jsonPath("code").value("NOT_FOUND"));
        http.perform(get("/api/v1/categories/{id}", Long.MAX_VALUE).header("Authorization", "Bearer " + token))
            .andExpect(status().isNotFound());
    }

    @Test void paginatedListingBoundsAndSortAllowlist() throws Exception {
        String token = token("MAESTROS_CATEGORIAS_ADMIN", "MAESTROS_CATEGORIAS_LEER");
        create(token, code(), null);
        http.perform(get("/api/v1/categories?page=0&size=1&sort=codigo&direction=desc")
            .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("size").value(1))
            .andExpect(jsonPath("content.length()").value(1))
            .andExpect(jsonPath("totalElements").isNumber());
        for (String query : new String[] {"size=101", "page=-1", "sort=activo", "direction=sideways"}) {
            http.perform(get("/api/v1/categories?" + query).header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("code").value("BAD_REQUEST"));
        }
    }

    @Test void permissionsAreEnforcedForReadsAndWrites() throws Exception {
        String read = token("MAESTROS_CATEGORIAS_LEER");
        String write = token("MAESTROS_CATEGORIAS_ADMIN");
        long id = create(write, code(), null);
        http.perform(get("/api/v1/categories/{id}", id).header("Authorization", "Bearer " + read))
            .andExpect(status().isOk());
        http.perform(post("/api/v1/categories").header("Authorization", "Bearer " + read)
            .contentType("application/json").content(body(code(), "Sin permiso", null)))
            .andExpect(status().isForbidden()).andExpect(jsonPath("code").value("FORBIDDEN"));
        http.perform(get("/api/v1/categories/{id}", id).header("Authorization", "Bearer " + write))
            .andExpect(status().isForbidden());
        http.perform(get("/api/v1/categories/{id}", id)).andExpect(status().isUnauthorized());
    }

    @Test void parentReferenceAndCycleAreProtected() throws Exception {
        String token = token("MAESTROS_CATEGORIAS_ADMIN", "MAESTROS_CATEGORIAS_LEER");
        String parentCode = code();
        long parent = create(token, parentCode, null);
        long child = create(token, code(), parent);
        update(token, parent, parentCode, child).andExpect(status().isConflict())
            .andExpect(jsonPath("code").value("CONFLICT"));
        update(token, parent, parentCode, parent).andExpect(status().isConflict());
        assertThat(db.queryForObject("select categoria_padre_id is null from categorias_producto where id=?",
            Boolean.class, parent)).isTrue();
        assertThat(db.queryForObject("select categoria_padre_id from categorias_producto where id=?",
            Long.class, child)).isEqualTo(parent);
        assertThatThrownBy(() -> db.update("insert into categorias_producto(codigo,nombre,categoria_padre_id) values (?,?,?)",
            code(), "Huérfana", Long.MAX_VALUE)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
