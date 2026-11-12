package com.techsupply.erp_backend.ventas;

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
class SalesMigrationIT extends PostgresIntegrationSupport {
    @Autowired DataSource dataSource;
    @Autowired JdbcTemplate db;
    @Autowired Flyway flyway;
    @Autowired PlatformTransactionManager transactions;
    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }
    private void fails(String sql, Object... args) { assertThatThrownBy(() -> db.update(sql, args)).isInstanceOf(DataIntegrityViolationException.class); }

    @Test void cleanSchemaHasSalesTablesAndPermissions() {
        assertThat(flyway.info().applied()).hasSize(13);
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=current_schema() and table_name in ('clientes','cotizaciones','detalles_cotizacion','pedidos_venta','detalles_pedido','despachos','detalles_despacho')", Integer.class)).isEqualTo(7);
        assertThat(db.queryForObject("select count(*) from permisos where codigo like 'VENTAS_%' or codigo like 'MAESTROS_CLIENTES_%'", Integer.class)).isEqualTo(15);
        assertThat(db.queryForObject("select count(*) from roles_permisos rp join roles r on r.id=rp.rol_id join permisos p on p.id=rp.permiso_id where r.codigo='ADMINISTRADOR' and (p.codigo like 'VENTAS_%' or p.codigo like 'MAESTROS_CLIENTES_%')", Integer.class)).isEqualTo(15);
        assertThat(db.queryForObject("select count(*) from pg_indexes where schemaname=current_schema() and indexname in ('idx_clientes_activo_codigo','idx_cotizaciones_cliente_fecha','idx_cotizaciones_estado_fecha','idx_detalles_cotizacion_producto','idx_pedidos_cliente_fecha','idx_pedidos_estado_fecha','idx_detalles_pedido_producto','idx_despachos_pedido','idx_despachos_estado_fecha','idx_detalles_despacho_pedido')", Integer.class)).isEqualTo(10);
        assertThat(db.queryForObject("select count(*) from information_schema.referential_constraints where constraint_schema=current_schema() and constraint_name in (select constraint_name from information_schema.key_column_usage where table_schema=current_schema() and table_name='usuarios' and column_name='cliente_id')", Integer.class)).isZero();
    }

    @Test void upgradePreservesHistoryAndProtectsDispatchSource() {
        String schema = "upgrade_v11_" + UUID.randomUUID().toString().replace("-", ""), p = schema + ".";
        Flyway old = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("10").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(10);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            db.execute("set local search_path to " + schema + ", public");
            long user = id("insert into " + p + "usuarios(username,correo,password_hash,cliente_id) values ('old','old@example.test','hash',9999) returning id");
            long approver = id("insert into " + p + "usuarios(username,correo,password_hash) values ('approver','approver@example.test','hash') returning id");
            long category = id("insert into " + p + "categorias_producto(codigo,nombre) values ('CAT','Categoria') returning id");
            long product = id("insert into " + p + "productos(sku,nombre,categoria_id) values ('PROD','Producto',?) returning id",category);
            long warehouse = id("insert into " + p + "almacenes(codigo,nombre) values ('ALM','Almacen') returning id");
            long supplier = id("insert into " + p + "proveedores(codigo,nombre_razon_social) values ('PROV','Proveedor') returning id");
            long dept = id("insert into " + p + "departamentos(codigo,nombre) values ('DEP','Departamento') returning id");
            long order = id("insert into " + p + "ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values ('OC',?,?,?,'ENVIADA') returning id",supplier,dept,user);
            long line = id("insert into " + p + "detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,10,2,0) returning id",order,product);
            long receipt = id("insert into " + p + "recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values ('REC',?,?,?,'REC','CONFIRMADA') returning id",order,warehouse,user);
            long detail = id("insert into " + p + "detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,precio_unitario_recibido) values (?,?,10,10,2) returning id",receipt,line);
            db.update("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_recepcion_id,clave_operacion) values (?,?,'ENTRADA','RECEPCION_COMPRA',10,2,?,?,'MR')",product,warehouse,user,detail);
            long adjustment=id("insert into " + p + "ajustes_inventario(folio,almacen_id,motivo,solicitado_por_usuario_id,autorizado_por_usuario_id,autorizado_en,clave_idempotencia,estado) values ('AJ',?,'razon',?,?,now(),'AJ','APROBADO') returning id",warehouse,user,approver);
            long adjDetail=id("insert into " + p + "detalles_ajuste_inventario(ajuste_inventario_id,producto_id,tipo,cantidad,costo_unitario) values (?,?,'POSITIVO',1,3) returning id",adjustment,product);
            db.update("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_ajuste_id,clave_operacion) values (?,?,'ENTRADA','AJUSTE',1,3,?,?,'MA')",product,warehouse,user,adjDetail);
        });
        Flyway upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("11").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from " + p + "movimientos_inventario", Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("select cliente_id from " + p + "usuarios where username='old'", Long.class)).isEqualTo(9999L);
        long user=id("select id from " + p + "usuarios where username='old'"), product=id("select id from " + p + "productos where sku='PROD'"), warehouse=id("select id from " + p + "almacenes where codigo='ALM'");
        long client=id("insert into " + p + "clientes(codigo,nombre_razon_social) values ('CLI','Cliente') returning id");
        fails("insert into " + p + "clientes(codigo,nombre_razon_social) values ('cli','Cliente')");
        long quote=id("insert into " + p + "cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia) values ('COT',?,?,current_date,'ACEPTADA','COT') returning id",client,user);
        long order=id("insert into " + p + "pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,clave_idempotencia) values ('PED',?,?,?,?,?,'INTERNO','CONTADO','PARCIALMENTE_DESPACHADO','PED') returning id",client,user,quote,warehouse,user);
        long secondQuote=id("insert into " + p + "cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia) values ('COT2',?,?,current_date,'ACEPTADA','COT2') returning id",client,user);
        fails("insert into " + p + "pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,clave_idempotencia) values ('PED2',?,?,?,?,?,'INTERNO','CREDITO','BORRADOR','PED2')",client,user,secondQuote,warehouse,user);
        fails("insert into " + p + "pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,clave_idempotencia) values ('PED2',?,?,?,?,?,'INTERNO','CONTADO','BORRADOR','PED2')",client,user,quote,warehouse,user);
        long line=id("insert into " + p + "detalles_pedido(pedido_id,producto_id,cantidad,cantidad_reservada,cantidad_despachada,precio_unitario,tasa_impuesto) values (?,?,10,6,4,10,16) returning id",order,product);
        fails("update " + p + "detalles_pedido set cantidad_reservada=7 where id=?",line);
        long dispatch=id("insert into " + p + "despachos(folio,pedido_id,almacen_id,registrado_por_usuario_id,estado,clave_idempotencia) values ('DES',?,?,?,'BORRADOR','DES') returning id",order,warehouse,user);
        long detail=id("insert into " + p + "detalles_despacho(despacho_id,detalle_pedido_id,cantidad,costo_unitario) values (?,?,4,999999999999.9999) returning id",dispatch,line);
        fails("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_despacho_id,clave_operacion) values (?,?,'SALIDA','DESPACHO_VENTA',3,999999999999.9999,?,?,'BAD')",product,warehouse,user,detail);
        db.update("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_despacho_id,clave_operacion) values (?,?,'SALIDA','DESPACHO_VENTA',4,999999999999.9999,?,?,'MD')",product,warehouse,user,detail);
        assertThat(db.queryForObject("select costo_unitario from " + p + "movimientos_inventario where clave_operacion='MD'",BigDecimal.class)).isEqualByComparingTo("999999999999.9999");
        fails("update " + p + "detalles_despacho set cantidad=5 where id=?",detail);
        db.update("update " + p + "despachos set estado='CONFIRMADO' where id=?",dispatch);
        fails("update " + p + "despachos set estado='CANCELADO' where id=?",dispatch);
        fails("delete from " + p + "movimientos_inventario where clave_operacion='MR'");
        fails("update " + p + "detalles_pedido set producto_id=999999 where id=?",line);
        fails("update " + p + "detalles_despacho set costo_unitario=1 where id=?",detail);
        fails("insert into " + p + "detalles_despacho(despacho_id,detalle_pedido_id,cantidad) values (?,?,1)",dispatch,line);
        fails("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_despacho_id,clave_operacion) values (?,?,'SALIDA','DESPACHO_VENTA',4,999999999999.9999,?,?,'MD2')",product,warehouse,user,detail);
    }
}
