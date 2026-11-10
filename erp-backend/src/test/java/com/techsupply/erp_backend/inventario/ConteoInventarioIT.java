package com.techsupply.erp_backend.inventario;

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
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import static org.assertj.core.api.Assertions.*;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ConteoInventarioIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http; @Autowired JdbcTemplate db; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    @Autowired TransactionTemplate tx;
    private record Fixture(String key, long warehouse, long first, long second) {}
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
        String key = "C" + UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        long first = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, key+"A", "Prueba", category);
        long second = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, key+"B", "Prueba", category);
        long warehouse = db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        return new Fixture(key, warehouse, first, second);
    }
    private long create(String token, long warehouse) throws Exception {
        String result = http.perform(post("/api/v1/inventory/counts").header("Authorization", "Bearer " + token).contentType("application/json")
            .content(json.writeValueAsString(Map.of("almacenId", warehouse, "fechaProgramada", "2026-11-10", "clase", "CICLICO"))))
            .andExpect(status().isCreated()).andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/api/v1/inventory/counts/")))
            .andExpect(jsonPath("estado").value("PROGRAMADO")).andExpect(jsonPath("fechaProgramada").value("2026-11-10")).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }
    private int capture(String token, long count, long product, String quantity) throws Exception {
        return http.perform(put("/api/v1/inventory/counts/{id}/lines/{productoId}", count, product).header("Authorization", "Bearer " + token)
            .contentType("application/json").content("{\"cantidadContada\":" + quantity + "}"))
            .andReturn().getResponse().getStatus();
    }
    @Test void flujoHttpValidacionesAuditoriaYStockInalterado() throws Exception {
        Fixture f = fixture(); String admin = token("INVENTARIO_CONTEOS_ADMIN", "INVENTARIO_CONTEOS_LEER");
        String reader = token("INVENTARIO_CONTEOS_LEER"), none = token("INVENTARIO_EXISTENCIAS_LEER");
        String url = "/api/v1/inventory/counts";
        http.perform(get(url)).andExpect(status().isUnauthorized());
        http.perform(get(url).header("Authorization", "Bearer " + none)).andExpect(status().isForbidden());
        http.perform(post(url).header("Authorization", "Bearer " + reader).contentType("application/json").content("{\"almacenId\":"+f.warehouse()+",\"fechaProgramada\":\"2026-11-10\",\"clase\":\"CICLICO\"}")).andExpect(status().isForbidden());
        http.perform(post(url).header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"almacenId\":0,\"fechaProgramada\":\"2026-11-10\",\"clase\":\"CICLICO\"}")).andExpect(status().isBadRequest());
        http.perform(post(url).header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"almacenId\":999999999999,\"fechaProgramada\":\"2026-11-10\",\"clase\":\"CICLICO\"}")).andExpect(status().isNotFound());
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada,costo_promedio,version) values (?,?,5,2,10,4)", f.first(), f.warehouse());
        http.perform(post(url).header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"almacenId\":"+f.warehouse()+",\"fechaProgramada\":\"2026-11-10\",\"clase\":\"CICLICO\",\"extra\":true}")).andExpect(status().isBadRequest());
        db.update("update almacenes set activo=false where id=?",f.warehouse());
        http.perform(post(url).header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"almacenId\":"+f.warehouse()+",\"fechaProgramada\":\"2026-11-10\",\"clase\":\"CICLICO\"}")).andExpect(status().isConflict());
        db.update("update almacenes set activo=true where id=?",f.warehouse());
        long count = create(admin, f.warehouse());
        http.perform(put(url+"/{id}/lines/{productoId}",count,f.first()).header("Authorization", "Bearer " + admin).contentType("application/json").content("{\"cantidadContada\":1,\"extra\":true}")).andExpect(status().isBadRequest());
        db.update("update productos set activo=false where id=?",f.second());
        assertThat(capture(admin,count,f.second(),"1")).isEqualTo(409);
        db.update("update productos set activo=true where id=?",f.second());
        http.perform(post(url+"/{id}/close", count).header("Authorization", "Bearer " + admin)).andExpect(status().isConflict());
        assertThat(capture(admin,count,f.first(),"4.0001")).isEqualTo(400);
        assertThat(capture(admin,count,f.first(),"-1")).isEqualTo(400);
        assertThat(capture(admin,count,999999999999L,"1")).isEqualTo(404);
        assertThat(capture(admin,count,f.first(),"3.000")).isEqualTo(200);
        http.perform(get(url+"/{id}",count).header("Authorization", "Bearer " + reader))
            .andExpect(status().isOk()).andExpect(jsonPath("detalles[0].cantidadSistema").value(5))
            .andExpect(jsonPath("detalles[0].cantidadContada").value(3)).andExpect(jsonPath("detalles[0].diferencia").value(-2))
            .andExpect(jsonPath("detalles[0].existenciaPrevia").value(true)).andExpect(jsonPath("detalles[0].versionExistencia").value(4));
        assertThat(capture(admin,count,f.second(),"0")).isEqualTo(200);
        http.perform(get(url+"/{id}",count).header("Authorization", "Bearer " + reader))
            .andExpect(jsonPath("detalles[1].existenciaPrevia").value(false)).andExpect(jsonPath("detalles[1].versionExistencia").value(nullValue()));
        assertThat(capture(admin,count,f.first(),"4")).isEqualTo(200);
        assertThat(db.queryForObject("select count(*) from detalles_conteo where conteo_id=?", Integer.class, count)).isEqualTo(2);
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("almacenId", Long.toString(f.warehouse())).param("estado", "EN_CAPTURA").param("size", "1"))
            .andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1)).andExpect(jsonPath("content[0].id").value(count));
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("almacenId", Long.toString(f.warehouse()))).andExpect(status().isOk()).andExpect(jsonPath("totalElements").value(1));
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("estado", "EN_CAPTURA")).andExpect(status().isOk());
        http.perform(get(url).header("Authorization", "Bearer " + reader)).andExpect(status().isOk());
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("estado", "MAL")).andExpect(status().isBadRequest());
        http.perform(get(url).header("Authorization", "Bearer " + reader).param("size", "101")).andExpect(status().isBadRequest());
        http.perform(post(url+"/{id}/close",count).header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andExpect(jsonPath("estado").value("CERRADO"));
        assertThat(capture(admin,count,f.first(),"4")).isEqualTo(409);
        http.perform(post(url+"/{id}/close",count).header("Authorization", "Bearer " + admin)).andExpect(status().isConflict());
        http.perform(post(url+"/{id}/cancel",count).header("Authorization", "Bearer " + admin)).andExpect(status().isConflict());
        long cancelled = create(admin, f.warehouse());
        http.perform(post(url+"/{id}/cancel",cancelled).header("Authorization", "Bearer " + admin)).andExpect(status().isOk()).andExpect(jsonPath("estado").value("CANCELADO"));
        assertThat(capture(admin,cancelled,f.first(),"1")).isEqualTo(409);
        assertThat(db.queryForObject("select cantidad_fisica from existencias where producto_id=? and almacen_id=?", BigDecimal.class, f.first(), f.warehouse())).isEqualByComparingTo("5");
        assertThat(db.queryForObject("select version from existencias where producto_id=? and almacen_id=?", Integer.class, f.first(), f.warehouse())).isEqualTo(4);
        assertThat(db.queryForObject("select cantidad_reservada from existencias where producto_id=? and almacen_id=?", BigDecimal.class, f.first(), f.warehouse())).isEqualByComparingTo("2");
        assertThat(db.queryForObject("select costo_promedio from existencias where producto_id=? and almacen_id=?", BigDecimal.class, f.first(), f.warehouse())).isEqualByComparingTo("10");
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?", Integer.class, f.second())).isZero();
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id in (?,?)", Integer.class, f.first(), f.second())).isZero();
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_COUNT_CREATED' and entidad_id=?", Integer.class, count)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_COUNT_STARTED' and entidad_id=?", Integer.class, count)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_COUNT_LINE_CAPTURED' and entidad_id in (select id from detalles_conteo where conteo_id=?)", Integer.class, count)).isEqualTo(3);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_COUNT_CLOSED' and entidad_id=?", Integer.class, count)).isEqualTo(1);
    }
    @Test void fallaDeAuditoriaRevierteLaCreacionDelConteo() throws Exception {
        Fixture f=fixture(); String admin=token("INVENTARIO_CONTEOS_ADMIN");
        long beforeSeq=db.queryForObject("select last_value from conteo_inventario_folio_seq",Long.class);
        boolean wasCalled=db.queryForObject("select is_called from conteo_inventario_folio_seq",Boolean.class);
        long auditBefore=db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_COUNT_CREATED'",Long.class);
        db.execute("alter table eventos_auditoria add constraint ck_test_count_audit_failure check (accion <> 'INVENTORY_COUNT_CREATED') not valid");
        try {
            int result=http.perform(post("/api/v1/inventory/counts").header("Authorization", "Bearer " + admin).contentType("application/json")
                .content(json.writeValueAsString(Map.of("almacenId",f.warehouse(),"fechaProgramada","2026-11-10","clase","CICLICO"))))
                .andReturn().getResponse().getStatus();
            assertThat(result).isEqualTo(409);
            assertThat(db.queryForObject("select is_called from conteo_inventario_folio_seq",Boolean.class)).isTrue();
            long afterSeq=db.queryForObject("select last_value from conteo_inventario_folio_seq",Long.class);
            assertThat(afterSeq).isEqualTo(wasCalled ? beforeSeq+1 : beforeSeq);
            assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_COUNT_CREATED'",Long.class)).isEqualTo(auditBefore);
            assertThat(db.queryForObject("select count(*) from conteos_inventario where almacen_id=?", Integer.class,f.warehouse())).isZero();
            assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_COUNT_CREATED' and entidad_id in (select id from conteos_inventario where almacen_id=?)", Integer.class,f.warehouse())).isZero();
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_count_audit_failure"); }
    }
    @Test void capturasConcurrentesNoPierdenLineasYSerializanElCierre() throws Exception {
        Fixture f=fixture(); String admin=token("INVENTARIO_CONTEOS_ADMIN", "INVENTARIO_CONTEOS_LEER"); long count=create(admin,f.warehouse());
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            Callable<Integer> a=()->{start.await(); return capture(admin,count,f.first(),"2");};
            Callable<Integer> b=()->{start.await(); return capture(admin,count,f.second(),"3");};
            var one=pool.submit(a); var two=pool.submit(b); start.countDown();
            assertThat(List.of(one.get(20,TimeUnit.SECONDS),two.get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally { pool.shutdownNow(); }
        assertThat(db.queryForObject("select count(*) from detalles_conteo where conteo_id=?", Integer.class, count)).isEqualTo(2);
        var race=Executors.newFixedThreadPool(2); var gate=new CountDownLatch(1);
        try {
            var capture=race.submit(()->{gate.await(); return capture(admin,count,f.first(),"7");});
            var close=race.submit(()->{gate.await(); return http.perform(post("/api/v1/inventory/counts/{id}/close",count).header("Authorization","Bearer "+admin)).andReturn().getResponse().getStatus();});
            gate.countDown(); int captureResult=capture.get(20,TimeUnit.SECONDS), closeResult=close.get(20,TimeUnit.SECONDS);
            assertThat(closeResult).isEqualTo(200); assertThat(captureResult).isIn(200,409);
            BigDecimal stored=db.queryForObject("select cantidad_contada from detalles_conteo where conteo_id=? and producto_id=?", BigDecimal.class,count,f.first());
            assertThat(stored).isEqualByComparingTo(captureResult==200 ? "7" : "2");
        } finally { race.shutdownNow(); }
        assertThat(db.queryForObject("select estado from conteos_inventario where id=?", String.class,count)).isEqualTo("CERRADO");
        assertThat(db.queryForObject("select count(*) from existencias where producto_id in (?,?)", Integer.class,f.first(),f.second())).isZero();
    }
    @Test void capturaEsperaBloqueoDeExistenciaYRegistraVersionConfirmada() throws Exception {
        Fixture f=fixture(); String admin=token("INVENTARIO_CONTEOS_ADMIN"); long count=create(admin,f.warehouse());
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,version) values (?,?,5,0)",f.first(),f.warehouse());
        var locked=new CountDownLatch(1); var release=new CountDownLatch(1); var pool=Executors.newFixedThreadPool(2);
        try {
            var writer=pool.submit(()->tx.executeWithoutResult(status->{
                db.update("update existencias set cantidad_fisica=6,version=version+1 where producto_id=? and almacen_id=?",f.first(),f.warehouse());
                locked.countDown(); try { if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("timeout"); }
                catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var capture=pool.submit(()->capture(admin,count,f.first(),"6"));
            assertThatThrownBy(()->capture.get(250,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); writer.get(10,TimeUnit.SECONDS); assertThat(capture.get(10,TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(db.queryForObject("select cantidad_sistema from detalles_conteo where conteo_id=?",BigDecimal.class,count)).isEqualByComparingTo("6");
            assertThat(db.queryForObject("select version_existencia from detalles_conteo where conteo_id=?",Integer.class,count)).isEqualTo(1);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
}
