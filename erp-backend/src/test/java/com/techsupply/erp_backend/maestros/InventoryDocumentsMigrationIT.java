package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class InventoryDocumentsMigrationIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired Flyway currentFlyway;

    @Test void upgradeV8PreservesReceiptsAndEnforcesInventoryConstraints() {
        String schema = "upgrade_v9_" + UUID.randomUUID().toString().replace("-", ""), p = schema + ".";
        Flyway old = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("8").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(8);
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            db.execute("set local search_path to " + schema + ", public");
            long category = id("insert into " + p + "categorias_producto(codigo,nombre) values ('TEST','Test') returning id");
            long product = id("insert into " + p + "productos(sku,nombre,categoria_id) values ('TEST','Test',?) returning id", category);
            long warehouse = id("insert into " + p + "almacenes(codigo,nombre) values ('TEST','Test') returning id");
            long supplier = id("insert into " + p + "proveedores(codigo,nombre_razon_social) values ('TEST','Test') returning id");
            long dept = id("insert into " + p + "departamentos(codigo,nombre) values ('TEST','Test') returning id");
            long user = id("insert into " + p + "usuarios(username,correo,password_hash) values ('test','test@example.test','test-hash') returning id");
            id("insert into " + p + "usuarios(username,correo,password_hash) values ('approver','approver@example.test','test-hash') returning id");
            long order = id("insert into " + p + "ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values ('TEST',?,?,?,'ENVIADA') returning id", supplier, dept, user);
            long line = id("insert into " + p + "detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,1,1,0) returning id", order, product);
            long receipt = id("insert into " + p + "recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values ('TEST',?,?,?,'TEST','CONFIRMADA') returning id", order, warehouse, user);
            long detail = id("insert into " + p + "detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,1,1,0,1) returning id", receipt, line);
            db.update("insert into " + p + "existencias(producto_id,almacen_id,cantidad_fisica,costo_promedio) values (?,?,1,?)", product, warehouse, new BigDecimal("999999999999.9900"));
            db.update("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_recepcion_id,clave_operacion) values (?,?,'ENTRADA','RECEPCION_COMPRA',1,?,?,?,'RECEPCION:TEST')", product, warehouse, new BigDecimal("999999999999.9900"), user, detail);
        });
        Flyway upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("9").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(currentFlyway.info().applied()).hasSize(11);
        assertThat(db.queryForObject("select costo_promedio from " + p + "existencias", BigDecimal.class)).isEqualByComparingTo("999999999999.9900");
        assertThat(db.queryForObject("select costo_unitario from " + p + "movimientos_inventario where motivo='RECEPCION_COMPRA'", BigDecimal.class)).isEqualByComparingTo("999999999999.9900");
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('conteos_inventario','detalles_conteo','ajustes_inventario','detalles_ajuste_inventario')", Integer.class, schema)).isEqualTo(4);
        assertThat(db.queryForObject("select count(*) from " + p + "roles_permisos rp join " + p + "roles r on r.id=rp.rol_id join " + p + "permisos pe on pe.id=rp.permiso_id where r.codigo='ADMINISTRADOR' and pe.codigo in ('INVENTARIO_MOVIMIENTOS_LEER','INVENTARIO_CONTEOS_LEER','INVENTARIO_CONTEOS_ADMIN','INVENTARIO_AJUSTES_LEER','INVENTARIO_AJUSTES_REGISTRAR','INVENTARIO_AJUSTES_APROBAR','INVENTARIO_AJUSTES_APLICAR','INVENTARIO_ALERTAS_LEER')", Integer.class)).isEqualTo(8);
        long product = id("select id from " + p + "productos where sku='TEST'"), warehouse = id("select id from " + p + "almacenes where codigo='TEST'");
        long user = id("select id from " + p + "usuarios where username='test'"), receiptDetail = id("select id from " + p + "detalles_recepcion");
        long count = id("insert into " + p + "conteos_inventario(folio,almacen_id,creado_por_usuario_id,fecha_programada,estado,clase) values ('CNT',?,?,current_date,'EN_CAPTURA','CICLICO') returning id", warehouse, user);
        long countDetail = id("insert into " + p + "detalles_conteo(conteo_id,producto_id,cantidad_sistema,cantidad_contada,diferencia,existencia_previa,version_existencia) values (?,?,1,2,1,true,0) returning id", count, product);
        fails("update " + p + "detalles_conteo set diferencia=0 where id=?", countDetail);
        fails("update " + p + "detalles_conteo set existencia_previa=false,version_existencia=null where id=?", countDetail);
        fails("insert into " + p + "detalles_conteo(conteo_id,producto_id,cantidad_sistema,cantidad_contada,diferencia,existencia_previa,version_existencia) values (?,?,1,2,1,true,0)", count, product);
        long adjustment = id("insert into " + p + "ajustes_inventario(folio,almacen_id,motivo,solicitado_por_usuario_id,clave_idempotencia,estado) values ('ADJ',?,'Test',?,'ADJ','PENDIENTE') returning id", warehouse, user);
        fails("update " + p + "ajustes_inventario set estado='APROBADO',autorizado_por_usuario_id=?,autorizado_en=now() where id=?", user, adjustment);
        fails("update " + p + "ajustes_inventario set estado='APLICADO' where id=?", adjustment);
        fails("insert into " + p + "ajustes_inventario(folio,almacen_id,motivo,solicitado_por_usuario_id,clave_idempotencia,estado) values ('ADJ2',?,'Test',?,'ADJ','PENDIENTE')", warehouse, user);
        fails("insert into " + p + "detalles_ajuste_inventario(ajuste_inventario_id,producto_id,tipo,cantidad) values (?,?,'POSITIVO',1)", adjustment, product);
        long approver = id("select id from " + p + "usuarios where username='approver'");
        db.update("update " + p + "ajustes_inventario set estado='APROBADO',autorizado_por_usuario_id=?,autorizado_en=now() where id=?", approver, adjustment);
        long adjustDetail = id("insert into " + p + "detalles_ajuste_inventario(ajuste_inventario_id,producto_id,tipo,cantidad,costo_unitario,detalle_conteo_id) values (?,?,'POSITIVO',1,?,?) returning id", adjustment, product, new BigDecimal("999999999999.9900"), countDetail);
        fails("insert into " + p + "detalles_ajuste_inventario(ajuste_inventario_id,producto_id,tipo,cantidad,costo_unitario) values (?,?,'POSITIVO',1,1)", adjustment, product);
        fails("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,usuario_id,clave_operacion) values (?,?,'ENTRADA','AJUSTE',1,?,'NO_SOURCE')", product, warehouse, user);
        long secondReceipt = id("insert into " + p + "recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) select 'TEST2',orden_compra_id,almacen_id,registrada_por_usuario_id,'TEST2','PENDIENTE' from " + p + "recepciones_compra where folio='TEST' returning id");
        long unusedDetail = id("insert into " + p + "detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) select ?,detalle_orden_compra_id,1,1,0,1 from " + p + "detalles_recepcion where id=? returning id", secondReceipt, receiptDetail);
        fails("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,usuario_id,detalle_recepcion_id,detalle_ajuste_id,clave_operacion) values (?,?,'ENTRADA','AJUSTE',1,?,?,?,'TWO_SOURCES')", product, warehouse, user, unusedDetail, adjustDetail);
        fails("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,usuario_id,detalle_ajuste_id,clave_operacion) values (?,?,'SALIDA','RECEPCION_COMPRA',1,?,?,'WRONG_TYPE')", product, warehouse, user, adjustDetail);
        db.update("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,usuario_id,detalle_ajuste_id,clave_operacion) values (?,?,'ENTRADA','AJUSTE',1,?,?,'AJUSTE:TEST')", product, warehouse, user, adjustDetail);
        fails("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,usuario_id,detalle_ajuste_id,clave_operacion) values (?,?,'ENTRADA','AJUSTE',1,?,?,'AJUSTE:OTHER')", product, warehouse, user, adjustDetail);
        db.update("update " + p + "conteos_inventario set estado='CERRADO' where id=?", count);
        db.update("update " + p + "ajustes_inventario set estado='APLICADO', aplicado_en=now() where id=?", adjustment);
        Flyway history = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("10").load();
        assertThat(history.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from " + p + "movimientos_inventario", Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("select costo_promedio from " + p + "existencias", BigDecimal.class)).isEqualByComparingTo("999999999999.9900");
        fails("update " + p + "movimientos_inventario set cantidad=2 where detalle_ajuste_id=?", adjustDetail);
        fails("delete from " + p + "detalles_conteo where id=?", countDetail);
        fails("update " + p + "ajustes_inventario set motivo='cambio' where id=?", adjustment);
        db.update("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_recepcion_id,clave_operacion) values (?,?,'ENTRADA','RECEPCION_COMPRA',1,1,?,?,'UPGRADE:NEW')", product, warehouse, user, unusedDetail);
        assertThat(db.queryForObject("select count(*) from " + p + "movimientos_inventario", Integer.class)).isEqualTo(3);
    }
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private void fails(String sql, Object... args) { assertThatThrownBy(() -> db.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class); }
}
