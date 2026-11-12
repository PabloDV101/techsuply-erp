package com.techsupply.erp_backend.ventas;

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
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class CotizacionConsultaIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http;
    @Autowired JdbcTemplate db;
    @Autowired PasswordEncoder passwords;
    @Autowired ObjectMapper json;
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    @Test void lecturaAutorizadaFiltraYPaginaSinModificarStock() throws Exception {
        String key = "Q" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        String username = key.toLowerCase(Locale.ROOT), password = UUID.randomUUID().toString();
        long role = id("insert into roles(codigo,nombre) values (?,?) returning id", "R"+key, "Consulta");
        db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo='VENTAS_COTIZACIONES_LEER'", role);
        long user = id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", username, username+"@example.test", passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",user,role);
        String login = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador",username,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = json.readTree(login).get("accessToken").asText();
        long customer = id("insert into clientes(codigo,nombre_razon_social) values (?,?) returning id",key,"Cliente");
        long category = id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",key,"Categoria");
        long product = id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key,"Producto",category);
        long quote = id("insert into cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia) values (?,?,?,date '2026-12-31','BORRADOR',?) returning id",key,customer,user,key);
        db.update("insert into detalles_cotizacion(cotizacion_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,2,10,16)",quote,product);
        http.perform(get("/api/v1/sales/quotes/{id}", quote).header("Authorization","Bearer "+token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.clienteId").value(customer))
            .andExpect(jsonPath("$.detalles[0].productoId").value(product));
        http.perform(get("/api/v1/sales/quotes").header("Authorization","Bearer "+token).param("clienteId",Long.toString(customer)).param("size","1"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.content[0].id").value(quote));
        http.perform(get("/api/v1/sales/quotes")).andExpect(status().isUnauthorized());
        String other = username + "x";
        id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", other, other + "@example.test", passwords.encode(password));
        String deniedLogin = http.perform(post("/api/v1/auth/login").contentType("application/json")
            .content(json.writeValueAsString(Map.of("identificador", other, "password", password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String deniedToken = json.readTree(deniedLogin).get("accessToken").asText();
        http.perform(get("/api/v1/sales/quotes").header("Authorization", "Bearer " + deniedToken)).andExpect(status().isForbidden());
        http.perform(get("/api/v1/sales/quotes/{id}", Long.MAX_VALUE).header("Authorization", "Bearer " + token)).andExpect(status().isNotFound());
        http.perform(get("/api/v1/sales/quotes").header("Authorization", "Bearer " + token).param("size", "101")).andExpect(status().isBadRequest());
        http.perform(get("/api/v1/sales/quotes").header("Authorization","Bearer "+token).param("clienteId","0")).andExpect(status().isBadRequest());
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,product)).isZero();
    }
}
