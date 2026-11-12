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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AplicarAjusteIT extends PostgresIntegrationSupport {
    @Autowired MockMvc http; @Autowired JdbcTemplate db; @Autowired ObjectMapper json; @Autowired PasswordEncoder passwords;
    @Autowired InventarioService inventory; @Autowired TransactionTemplate tx;
    record Account(long id,String username,String token) {}
    record Fixture(String key,long warehouse,long first,long second) {}
    private Account account(String... permissions) throws Exception {
        String key=UUID.randomUUID().toString(),password=UUID.randomUUID().toString();
        long role=db.queryForObject("insert into roles(codigo,nombre) values (?,?) returning id",Long.class,"T_"+key.substring(0,20).toUpperCase(Locale.ROOT),"Test");
        for(String permission:permissions) db.update("insert into roles_permisos(rol_id,permiso_id) select ?,id from permisos where codigo=?",role,permission);
        long user=db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",Long.class,key,key+"@example.test",passwords.encode(password));
        db.update("insert into usuarios_roles(usuario_id,rol_id) values (?,?)",user,role);
        String body=http.perform(post("/api/v1/auth/login").contentType("application/json").content(json.writeValueAsString(Map.of("identificador",key,"password",password))))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(user,key,json.readTree(body).get("accessToken").asText());
    }
    private Fixture fixture() {
        String key="J"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category=db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id",Long.class,key,"Prueba");
        long first=db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",Long.class,key+"A","Prueba",category);
        long second=db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",Long.class,key+"B","Prueba",category);
        long warehouse=db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id",Long.class,key,"Prueba");
        return new Fixture(key,warehouse,first,second);
    }
    private void stock(Fixture f,long product,String physical,String reserved,String average,int version) {
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica,cantidad_reservada,costo_promedio,version) values (?,?,?,?,?,?)",product,f.warehouse(),new BigDecimal(physical),new BigDecimal(reserved),new BigDecimal(average),version);
    }
    private static Map<String,Object> plus(long product,int quantity,int cost) { return Map.of("productoId",product,"tipo","POSITIVO","cantidad",quantity,"costoUnitario",cost); }
    private static Map<String,Object> minus(long product,int quantity) { return Map.of("productoId",product,"tipo","NEGATIVO","cantidad",quantity); }
    private static Map<String,Object> counted(Map<String,Object> line,long detail) { var map=new HashMap<>(line);map.put("detalleConteoId",detail);return map; }
    private long create(Fixture f,Account creator,String suffix,List<Map<String,Object>> lines) throws Exception {
        var body=Map.of("almacenId",f.warehouse(),"motivo","Solicitud","claveIdempotencia",f.key()+suffix,"detalles",lines);
        String result=http.perform(post("/api/v1/inventory/adjustments").header("Authorization","Bearer "+creator.token()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return json.readTree(result).get("id").asLong();
    }
    private void approve(Account actor,long id) throws Exception { http.perform(post("/api/v1/inventory/adjustments/{id}/approve",id).header("Authorization","Bearer "+actor.token())).andExpect(status().isOk()); }
    private int apply(Account actor,long id) throws Exception { return http.perform(post("/api/v1/inventory/adjustments/{id}/apply",id).header("Authorization","Bearer "+actor.token())).andReturn().getResponse().getStatus(); }
    private long count(Fixture f,Account actor,long product,int counted) throws Exception {
        var body=Map.of("almacenId",f.warehouse(),"fechaProgramada","2026-11-10","clase","CICLICO");
        String created=http.perform(post("/api/v1/inventory/counts").header("Authorization","Bearer "+actor.token()).contentType("application/json").content(json.writeValueAsString(body)))
            .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        long id=json.readTree(created).get("id").asLong();
        String line=http.perform(put("/api/v1/inventory/counts/{id}/lines/{productoId}",id,product).header("Authorization","Bearer "+actor.token()).contentType("application/json").content("{\"cantidadContada\":"+counted+"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        long detail=json.readTree(line).get("detalles").get(0).get("id").asLong();
        http.perform(post("/api/v1/inventory/counts/{id}/close",id).header("Authorization","Bearer "+actor.token())).andExpect(status().isOk());
        return detail;
    }
    private long receipt(Fixture f,Account creator,List<Long> products) {
        String suffix=UUID.randomUUID().toString().substring(0,5).toUpperCase(Locale.ROOT), key=f.key()+suffix;
        long supplier=db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id",Long.class,key,"Prueba");
        long dept=db.queryForObject("insert into departamentos(codigo,nombre) values (?,?) returning id",Long.class,key,"Prueba");
        long order=db.queryForObject("insert into ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values (?,?,?,?,?) returning id",Long.class,key,supplier,dept,creator.id(),"ENVIADA");
        long receipt=db.queryForObject("insert into recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,?) returning id",Long.class,key,order,f.warehouse(),creator.id(),key,"PENDIENTE");
        for(long product:products) {
            long line=db.queryForObject("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,?,?,?) returning id",Long.class,order,product,1,10,0);
            db.update("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,?,?,?,?)",receipt,line,1,1,0,10);
        }
        return receipt;
    }
    private BigDecimal number(String column,long product) { return db.queryForObject("select "+column+" from existencias where producto_id=?",BigDecimal.class,product); }
    private int movementCount(Fixture f) { return db.queryForObject("select count(*) from movimientos_inventario where producto_id in (?,?)",Integer.class,f.first(),f.second()); }

    @Test void aplicaDosSentidosConCostoAuditoriaPermisosYReintento() throws Exception {
        Fixture f=fixture(); Account creator=account("INVENTARIO_AJUSTES_REGISTRAR"),approver=account("INVENTARIO_AJUSTES_APROBAR"),applier=account("INVENTARIO_AJUSTES_APLICAR"),denied=account("INVENTARIO_AJUSTES_LEER");
        stock(f,f.first(),"10","3","4",0); stock(f,f.second(),"5","1","8",0);
        long id=create(f,creator,"M",List.of(plus(f.first(),2,10),minus(f.second(),3)));
        http.perform(post("/api/v1/inventory/adjustments/{id}/apply",id)).andExpect(status().isUnauthorized());
        long auditBefore=db.queryForObject("select count(*) from eventos_auditoria where accion='AUTHORIZATION_DENIED' and usuario_id=?",Long.class,denied.id());
        assertThat(apply(denied,id)).isEqualTo(403);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='AUTHORIZATION_DENIED' and usuario_id=?",Long.class,denied.id())).isEqualTo(auditBefore+1);
        assertThat(apply(applier,id)).isEqualTo(409);
        approve(approver,id);
        http.perform(post("/api/v1/inventory/adjustments/{id}/apply",id).header("Authorization","Bearer "+applier.token()))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("APLICADO")).andExpect(jsonPath("aplicadoEn").isString())
            .andExpect(jsonPath("detalles[1].costoUnitario").value(8));
        assertThat(number("cantidad_fisica",f.first())).isEqualByComparingTo("12"); assertThat(number("costo_promedio",f.first())).isEqualByComparingTo("5");
        assertThat(number("cantidad_reservada",f.first())).isEqualByComparingTo("3"); assertThat(number("cantidad_fisica",f.second())).isEqualByComparingTo("2");
        assertThat(number("costo_promedio",f.second())).isEqualByComparingTo("8"); assertThat(number("cantidad_reservada",f.second())).isEqualByComparingTo("1");
        assertThat(db.queryForObject("select count(*) from existencias where producto_id in (?,?) and version=1",Integer.class,f.first(),f.second())).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=? and tipo='ENTRADA' and costo_unitario=10 and usuario_id=?",Integer.class,f.first(),applier.id())).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from movimientos_inventario where producto_id=? and tipo='SALIDA' and costo_unitario=8 and usuario_id=?",Integer.class,f.second(),applier.id())).isEqualTo(1);
        assertThat(db.queryForObject("select costo_unitario from detalles_ajuste_inventario where ajuste_inventario_id=? and producto_id=?",BigDecimal.class,id,f.second())).isEqualByComparingTo("8");
        assertThat(apply(applier,id)).isEqualTo(200); assertThat(movementCount(f)).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_APPLIED' and entidad_id=? and usuario_id=?",Integer.class,id,applier.id())).isEqualTo(1);
        var retryBody=Map.of("almacenId",f.warehouse(),"motivo","Solicitud","claveIdempotencia",f.key()+"M","detalles",List.of(minus(f.second(),3),plus(f.first(),2,10)));
        http.perform(post("/api/v1/inventory/adjustments").header("Authorization","Bearer "+creator.token()).contentType("application/json").content(json.writeValueAsString(retryBody)))
            .andExpect(status().isOk()).andExpect(jsonPath("estado").value("APLICADO"));
    }
    @Test void fallaTardiaReviertePrimeraLineaYConservaReserva() throws Exception {
        Fixture f=fixture();Account creator=account("INVENTARIO_AJUSTES_REGISTRAR"),approver=account("INVENTARIO_AJUSTES_APROBAR"),applier=account("INVENTARIO_AJUSTES_APLICAR");
        stock(f,f.second(),"5","4","7",0);
        long id=create(f,creator,"R",List.of(plus(f.first(),2,10),minus(f.second(),2))); approve(approver,id);
        assertThat(apply(applier,id)).isEqualTo(409);
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,f.first())).isZero();
        assertThat(number("cantidad_fisica",f.second())).isEqualByComparingTo("5"); assertThat(number("cantidad_reservada",f.second())).isEqualByComparingTo("4");
        assertThat(movementCount(f)).isZero();
        assertThat(db.queryForObject("select estado from ajustes_inventario where id=?",String.class,id)).isEqualTo("APROBADO");
        assertThat(db.queryForObject("select count(*) from detalles_ajuste_inventario where ajuste_inventario_id=? and tipo='NEGATIVO' and costo_unitario is null",Integer.class,id)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_APPLIED' and entidad_id=?",Integer.class,id)).isZero();
    }
    @Test void versionObsoletaNoSeCompensaAunqueCantidadSeaIgual() throws Exception {
        Fixture f=fixture(); Account creator=account("INVENTARIO_AJUSTES_REGISTRAR","INVENTARIO_CONTEOS_ADMIN"),approver=account("INVENTARIO_AJUSTES_APROBAR"),applier=account("INVENTARIO_AJUSTES_APLICAR");
        stock(f,f.first(),"5","0","3",0); long detail=count(f,creator,f.first(),7);
        long id=create(f,creator,"V",List.of(counted(plus(f.first(),2,10),detail))); approve(approver,id);
        db.update("update existencias set version=version+1 where producto_id=?",f.first());
        assertThat(apply(applier,id)).isEqualTo(409); assertThat(number("cantidad_fisica",f.first())).isEqualByComparingTo("5");
        assertThat(movementCount(f)).isZero(); assertThat(db.queryForObject("select estado from ajustes_inventario where id=?",String.class,id)).isEqualTo("APROBADO");
    }
    @Test void dosAjustesDelMismoConteoSoloPuedenAplicarseUnaVez() throws Exception {
        Fixture f=fixture();Account creator=account("INVENTARIO_AJUSTES_REGISTRAR","INVENTARIO_CONTEOS_ADMIN"),approver=account("INVENTARIO_AJUSTES_APROBAR"),applier=account("INVENTARIO_AJUSTES_APLICAR");
        long detail=count(f,creator,f.first(),2);
        long first=create(f,creator,"A",List.of(counted(plus(f.first(),2,10),detail))), second=create(f,creator,"B",List.of(counted(plus(f.first(),2,10),detail)));
        approve(approver,first); approve(approver,second);
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await();return apply(applier,first);});var b=pool.submit(()->{start.await();return apply(applier,second);});
            start.countDown();assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,409);
        } finally { pool.shutdownNow(); }
        assertThat(number("cantidad_fisica",f.first())).isEqualByComparingTo("2"); assertThat(movementCount(f)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from ajustes_inventario where id in (?,?) and estado='APLICADO'",Integer.class,first,second)).isEqualTo(1);
    }
    @Test void recepcionYPrimeraExistenciaSeSerializanSinPerderMovimientos() throws Exception {
        Fixture f=fixture();Account creator=account("INVENTARIO_AJUSTES_REGISTRAR","INVENTARIO_CONTEOS_ADMIN"),approver=account("INVENTARIO_AJUSTES_APROBAR"),applier=account("INVENTARIO_AJUSTES_APLICAR"),receiver=account("COMPRAS_RECEPCIONES_CONFIRMAR");
        long detail=count(f,creator,f.first(),2); long id=create(f,creator,"C",List.of(counted(plus(f.first(),2,10),detail)));approve(approver,id);
        long receipt=receipt(f,creator,List.of(f.first()));
        var pool=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await();return apply(applier,id);});
            var b=pool.submit(()->{start.await();return http.perform(post("/api/v1/purchase-receipts/{id}/confirm",receipt).header("Authorization","Bearer "+receiver.token())).andReturn().getResponse().getStatus();});
            start.countDown();int adjustment=a.get(30,TimeUnit.SECONDS), reception=b.get(30,TimeUnit.SECONDS);
            assertThat(reception).isEqualTo(200);assertThat(adjustment).isIn(200,409);
            assertThat(number("cantidad_fisica",f.first())).isEqualByComparingTo(adjustment==200?"3":"1");
            assertThat(movementCount(f)).isEqualTo(adjustment==200?2:1);
            assertThat(db.queryForObject("select estado from ajustes_inventario where id=?",String.class,id)).isEqualTo(adjustment==200?"APLICADO":"APROBADO");
        } finally { pool.shutdownNow(); }
    }
    @Test void recepcionYAjusteDeDosProductosConcurrenSinPerderExistencias() throws Exception {
        Fixture f=fixture(); Account creator=account("INVENTARIO_AJUSTES_REGISTRAR"), approver=account("INVENTARIO_AJUSTES_APROBAR"), applier=account("INVENTARIO_AJUSTES_APLICAR"), receiver=account("COMPRAS_RECEPCIONES_CONFIRMAR");
        long id=create(f,creator,"T",List.of(plus(f.second(),1,10),plus(f.first(),1,10))); approve(approver,id);
        long receipt=receipt(f,creator,List.of(f.second(),f.first()));
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await();return apply(applier,id);});
            var b=pool.submit(()->{start.await();return http.perform(post("/api/v1/purchase-receipts/{id}/confirm",receipt).header("Authorization","Bearer "+receiver.token())).andReturn().getResponse().getStatus();});
            start.countDown(); assertThat(List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally { pool.shutdownNow(); }
        assertThat(number("cantidad_fisica",f.first())).isEqualByComparingTo("2"); assertThat(number("cantidad_fisica",f.second())).isEqualByComparingTo("2");
        assertThat(movementCount(f)).isEqualTo(4);
    }
    @Test void recepcionAnteriorInvalidaConteoSinExistencia() throws Exception {
        Fixture f=fixture(); Account creator=account("INVENTARIO_AJUSTES_REGISTRAR","INVENTARIO_CONTEOS_ADMIN"), approver=account("INVENTARIO_AJUSTES_APROBAR"), applier=account("INVENTARIO_AJUSTES_APLICAR"), receiver=account("COMPRAS_RECEPCIONES_CONFIRMAR");
        long detail=count(f,creator,f.first(),2), id=create(f,creator,"S",List.of(counted(plus(f.first(),2,10),detail)));
        approve(approver,id);
        long receipt=receipt(f,creator,List.of(f.first()));
        http.perform(post("/api/v1/purchase-receipts/{id}/confirm",receipt).header("Authorization","Bearer "+receiver.token())).andExpect(status().isOk());
        assertThat(apply(applier,id)).isEqualTo(409);
        assertThat(number("cantidad_fisica",f.first())).isEqualByComparingTo("1");
        assertThat(movementCount(f)).isEqualTo(1);
    }
    @Test void aplicacionesConcurrentesDelMismoAjusteSonIdempotentes() throws Exception {
        Fixture f=fixture(); Account creator=account("INVENTARIO_AJUSTES_REGISTRAR"), approver=account("INVENTARIO_AJUSTES_APROBAR"), applier=account("INVENTARIO_AJUSTES_APLICAR");
        long id=create(f,creator,"D",List.of(plus(f.first(),1,10))); approve(approver,id);
        var pool=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=pool.submit(()->{start.await();return apply(applier,id);}); var b=pool.submit(()->{start.await();return apply(applier,id);});
            start.countDown(); assertThat(List.of(a.get(25,TimeUnit.SECONDS),b.get(25,TimeUnit.SECONDS))).containsExactlyInAnyOrder(200,200);
        } finally { pool.shutdownNow(); }
        assertThat(number("cantidad_fisica",f.first())).isEqualByComparingTo("1"); assertThat(movementCount(f)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from eventos_auditoria where accion='INVENTORY_ADJUSTMENT_APPLIED' and entidad_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void falloDeAuditoriaRevierteInventarioYEstado() throws Exception {
        Fixture f=fixture();Account creator=account("INVENTARIO_AJUSTES_REGISTRAR"),approver=account("INVENTARIO_AJUSTES_APROBAR"),applier=account("INVENTARIO_AJUSTES_APLICAR");
        stock(f,f.first(),"5","1","8",0); long id=create(f,creator,"F",List.of(minus(f.first(),2)));approve(approver,id);
        db.execute("alter table eventos_auditoria add constraint ck_test_apply_audit_failure check (accion <> 'INVENTORY_ADJUSTMENT_APPLIED') not valid");
        try {
            assertThat(apply(applier,id)).isEqualTo(409);
            assertThat(number("cantidad_fisica",f.first())).isEqualByComparingTo("5");assertThat(movementCount(f)).isZero();
            assertThat(db.queryForObject("select estado from ajustes_inventario where id=?",String.class,id)).isEqualTo("APROBADO");
            assertThat(db.queryForObject("select count(*) from detalles_ajuste_inventario where ajuste_inventario_id=? and costo_unitario is null",Integer.class,id)).isEqualTo(1);
        } finally { db.execute("alter table eventos_auditoria drop constraint ck_test_apply_audit_failure"); }
        assertThat(apply(applier,id)).isEqualTo(200); assertThat(number("cantidad_fisica",f.first())).isEqualByComparingTo("3");
    }
    @Test void llamadaInternaNoPuedeConfirmarMovimientoSinAjusteAplicado() throws Exception {
        Fixture f=fixture();Account creator=account("INVENTARIO_AJUSTES_REGISTRAR"),approver=account("INVENTARIO_AJUSTES_APROBAR"),applier=account("INVENTARIO_AJUSTES_APLICAR");
        long id=create(f,creator,"I",List.of(plus(f.first(),1,10)));approve(approver,id);
        long detail=db.queryForObject("select id from detalles_ajuste_inventario where ajuste_inventario_id=?",Long.class,id);
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(new SecurityActor(applier.id(),applier.username()),null,List.of(new SimpleGrantedAuthority("INVENTARIO_AJUSTES_APLICAR"))));
        try { assertThatThrownBy(()->tx.executeWithoutResult(status->inventory.registrarAjuste(detail))).isInstanceOf(IllegalStateException.class); }
        finally { SecurityContextHolder.clearContext(); }
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?",Integer.class,f.first())).isZero(); assertThat(movementCount(f)).isZero();
    }
}
