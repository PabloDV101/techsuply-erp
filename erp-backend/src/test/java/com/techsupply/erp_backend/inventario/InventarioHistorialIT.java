package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class InventarioHistorialIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired PlatformTransactionManager manager;
    record Data(long product, long warehouse, long creator, long approver, long count, long countLine,
                long openCount, long openCountLine, long adjustment, long adjustmentLine, long openAdjustment, long movement) {}
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private Data data() {
        String key="H"+UUID.randomUUID().toString().substring(0,12).toUpperCase(Locale.ROOT);
        long category=id("insert into categorias_producto(codigo,nombre) values (?,?) returning id",key,"Prueba");
        long product=id("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id",key,"Prueba",category);
        long warehouse=id("insert into almacenes(codigo,nombre) values (?,?) returning id",key,"Prueba");
        long creator=id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",key.toLowerCase(Locale.ROOT),key.toLowerCase(Locale.ROOT)+"@example.test","test-hash");
        long approver=id("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id",key.toLowerCase(Locale.ROOT)+"a",key.toLowerCase(Locale.ROOT)+"a@example.test","test-hash");
        long count=id("insert into conteos_inventario(folio,almacen_id,creado_por_usuario_id,fecha_programada,estado,clase) values (?,?,?,current_date,'EN_CAPTURA','CICLICO') returning id",key,warehouse,creator);
        long line=id("insert into detalles_conteo(conteo_id,producto_id,cantidad_sistema,cantidad_contada,diferencia,existencia_previa) values (?,?,0,1,1,false) returning id",count,product);
        db.update("update conteos_inventario set estado='CERRADO' where id=?",count);
        long openCount=id("insert into conteos_inventario(folio,almacen_id,creado_por_usuario_id,fecha_programada,estado,clase) values (?,?,?,current_date,'EN_CAPTURA','CICLICO') returning id",key+"O",warehouse,creator);
        long openLine=id("insert into detalles_conteo(conteo_id,producto_id,cantidad_sistema,cantidad_contada,diferencia,existencia_previa) values (?,?,0,2,2,false) returning id",openCount,product);
        long adjustment=id("insert into ajustes_inventario(folio,almacen_id,motivo,solicitado_por_usuario_id,clave_idempotencia,estado) values (?,?,'Prueba',?,?,'PENDIENTE') returning id",key,warehouse,creator,key);
        long adjLine=id("insert into detalles_ajuste_inventario(ajuste_inventario_id,producto_id,tipo,cantidad,costo_unitario,detalle_conteo_id) values (?,?,'POSITIVO',1,10,?) returning id",adjustment,product,line);
        db.update("update ajustes_inventario set estado='APLICADO',autorizado_por_usuario_id=?,autorizado_en=now(),aplicado_en=now() where id=?",approver,adjustment);
        long movement=id("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_ajuste_id,clave_operacion) values (?,?,'ENTRADA','AJUSTE',1,10,?,?,?) returning id",product,warehouse,creator,adjLine,"AJUSTE:"+adjLine);
        long openAdjustment=id("insert into ajustes_inventario(folio,almacen_id,motivo,solicitado_por_usuario_id,clave_idempotencia,estado) values (?,?,'Original',?,?,'PENDIENTE') returning id",key+"O",warehouse,creator,key+"O");
        return new Data(product,warehouse,creator,approver,count,line,openCount,openLine,adjustment,adjLine,openAdjustment,movement);
    }
    private String role() {
        String role="inventory_test_"+UUID.randomUUID().toString().replace("-","");
        db.execute("create role "+role+" nologin");
        db.execute("grant usage on schema public to "+role);
        db.execute("grant select,insert,update,delete,truncate on conteos_inventario,detalles_conteo,ajustes_inventario,detalles_ajuste_inventario,movimientos_inventario to "+role);
        db.execute("grant usage,select on all sequences in schema public to "+role);
        assertThat(db.queryForObject("select rolsuper from pg_roles where rolname=?",Boolean.class,role)).isFalse();
        assertThat(db.queryForObject("select has_table_privilege(?, 'movimientos_inventario', 'UPDATE,DELETE,TRUNCATE')",Boolean.class,role)).isTrue();
        return role;
    }
    private void asRole(String role, String sql, Object... args) {
        new TransactionTemplate(manager).executeWithoutResult(status->{db.execute("set local role "+role);db.update(sql,args);});
    }
    private void blocked(String role,String sql,Object... args) {
        assertThatThrownBy(()->asRole(role,sql,args)).isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void bloqueaDmlYTruncateInclusoConPrivilegiosRealesYRevierteTodaLaTransaccion() {
        Data d=data(); String role=role();
        blocked(role,"update movimientos_inventario set cantidad=2 where id=?",d.movement());
        blocked(role,"delete from movimientos_inventario where id=?",d.movement());
        blocked(role,"truncate table movimientos_inventario");
        blocked(role,"update ajustes_inventario set motivo='Cambio' where id=?",d.adjustment());
        blocked(role,"delete from ajustes_inventario where id=?",d.adjustment());
        blocked(role,"update detalles_ajuste_inventario set cantidad=2 where id=?",d.adjustmentLine());
        blocked(role,"delete from detalles_ajuste_inventario where id=?",d.adjustmentLine());
        blocked(role,"insert into detalles_ajuste_inventario(ajuste_inventario_id,producto_id,tipo,cantidad,costo_unitario) values (?,?,'POSITIVO',1,1)",d.adjustment(),d.product());
        long openAdjustmentLine=id("insert into detalles_ajuste_inventario(ajuste_inventario_id,producto_id,tipo,cantidad,costo_unitario) values (?,?,'POSITIVO',1,1) returning id",d.openAdjustment(),d.product());
        blocked(role,"update detalles_ajuste_inventario set ajuste_inventario_id=? where id=?",d.adjustment(),openAdjustmentLine);
        blocked(role,"insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_ajuste_id,clave_operacion) values (?,?,'ENTRADA','AJUSTE',2,1,?,?,?)",d.product(),d.warehouse(),d.creator(),openAdjustmentLine,"mismatch:"+d.movement());
        blocked(role,"update conteos_inventario set fecha_programada=current_date where id=?",d.count());
        blocked(role,"delete from conteos_inventario where id=?",d.count());
        blocked(role,"update detalles_conteo set cantidad_contada=2,diferencia=2 where id=?",d.countLine());
        blocked(role,"delete from detalles_conteo where id=?",d.countLine());
        blocked(role,"insert into detalles_conteo(conteo_id,producto_id,cantidad_sistema,cantidad_contada,diferencia,existencia_previa) values (?,?,0,1,1,false)",d.count(),d.product());
        blocked(role,"update detalles_conteo set conteo_id=? where id=?",d.count(),d.openCountLine());
        blocked(role,"truncate table conteos_inventario cascade");
        asRole(role,"update detalles_conteo set cantidad_contada=3,diferencia=3 where id=?",d.openCountLine());
        assertThat(db.queryForObject("select cantidad_contada from detalles_conteo where id=?",java.math.BigDecimal.class,d.openCountLine())).isEqualByComparingTo("3");
        asRole(role,"update conteos_inventario set estado='CANCELADO' where id=?",d.openCount());
        blocked(role,"delete from detalles_conteo where id=?",d.openCountLine());
        assertThatThrownBy(()->new TransactionTemplate(manager).executeWithoutResult(status->{
            db.execute("set local role "+role); db.update("update ajustes_inventario set motivo='Temporal' where id=?",d.openAdjustment());
            db.update("delete from movimientos_inventario where id=?",d.movement());
        })).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(db.queryForObject("select motivo from ajustes_inventario where id=?",String.class,d.openAdjustment())).isEqualTo("Original");
        blocked(role,"insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_ajuste_id,clave_operacion) values (?,?,'ENTRADA','AJUSTE',1,10,?,?,?)",d.product(),d.warehouse(),d.creator(),d.adjustmentLine(),"other:"+d.movement());
    }
    @Test void cierreConcurrenteSerializaCambiosDeDetalle() throws Exception {
        Data d=data(); var pool=Executors.newFixedThreadPool(2); var locked=new CountDownLatch(1); var release=new CountDownLatch(1);
        try {
            var close=pool.submit(()->new TransactionTemplate(manager).executeWithoutResult(status->{
                db.update("update conteos_inventario set estado='CERRADO' where id=?",d.openCount()); locked.countDown();
                try { if(!release.await(10,TimeUnit.SECONDS)) throw new IllegalStateException("Timeout"); } catch(InterruptedException e) {Thread.currentThread().interrupt();throw new IllegalStateException(e);}
            }));
            assertThat(locked.await(5,TimeUnit.SECONDS)).isTrue();
            var edit=pool.submit(()->db.update("update detalles_conteo set cantidad_contada=3,diferencia=3 where id=?",d.openCountLine()));
            assertThatThrownBy(()->edit.get(300,TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown(); close.get(10,TimeUnit.SECONDS);
            assertThatThrownBy(()->edit.get(10,TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class).hasCauseInstanceOf(DataIntegrityViolationException.class);
            assertThat(db.queryForObject("select cantidad_contada from detalles_conteo where id=?",java.math.BigDecimal.class,d.openCountLine())).isEqualByComparingTo("2");
        } finally { release.countDown();pool.shutdownNow(); }
    }
}
