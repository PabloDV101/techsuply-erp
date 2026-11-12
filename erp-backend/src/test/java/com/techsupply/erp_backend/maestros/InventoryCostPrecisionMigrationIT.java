package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.math.BigDecimal;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class InventoryCostPrecisionMigrationIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @Autowired PlatformTransactionManager transactionManager;

    @Test void upgradeFromV7PreservesCostsAndSupportsReceiptPriceRange() {
        String schema = "upgrade_v8_" + UUID.randomUUID().toString().replace("-", "");
        Flyway old = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("7").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(7);
        String p = schema + ".";
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
        db.execute("set local search_path to " + schema + ", public");
        long category = db.queryForObject("insert into " + p + "categorias_producto(codigo,nombre) values ('TEST','Test') returning id", Long.class);
        long product = db.queryForObject("insert into " + p + "productos(sku,nombre,categoria_id) values ('TEST','Test',?) returning id", Long.class, category);
        long warehouse = db.queryForObject("insert into " + p + "almacenes(codigo,nombre) values ('TEST','Test') returning id", Long.class);
        long supplier = db.queryForObject("insert into " + p + "proveedores(codigo,nombre_razon_social) values ('TEST','Test') returning id", Long.class);
        long department = db.queryForObject("insert into " + p + "departamentos(codigo,nombre) values ('TEST','Test') returning id", Long.class);
        long user = db.queryForObject("insert into " + p + "usuarios(username,correo,password_hash) values ('test','test@example.test','test-hash') returning id", Long.class);
        long order = db.queryForObject("insert into " + p + "ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values ('TEST',?,?,?,'ENVIADA') returning id", Long.class, supplier, department, user);
        long line = db.queryForObject("insert into " + p + "detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,1,1,0) returning id", Long.class, order, product);
        long receipt = db.queryForObject("insert into " + p + "recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values ('TEST',?,?,?,'TEST','PENDIENTE') returning id", Long.class, order, warehouse, user);
        long detail = db.queryForObject("insert into " + p + "detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,1,1,0,1) returning id", Long.class, receipt, line);
        BigDecimal oldCost = new BigDecimal("9999999999.9999");
        db.update("insert into " + p + "existencias(producto_id,almacen_id,cantidad_fisica,costo_promedio) values (?,?,1,?)", product, warehouse, oldCost);
        db.update("insert into " + p + "movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_recepcion_id,clave_operacion) values (?,?,'ENTRADA','RECEPCION_COMPRA',1,?,?,?,'TEST')", product, warehouse, oldCost, user, detail);
        });
        Flyway upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("8").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select costo_promedio from " + p + "existencias", BigDecimal.class)).isEqualByComparingTo("9999999999.9999");
        assertThat(db.queryForObject("select costo_unitario from " + p + "movimientos_inventario", BigDecimal.class)).isEqualByComparingTo("9999999999.9999");
        assertThat(db.queryForObject("select count(*) from information_schema.columns where table_schema=? and ((table_name='existencias' and column_name='costo_promedio') or (table_name='movimientos_inventario' and column_name='costo_unitario')) and numeric_precision=16 and numeric_scale=4", Integer.class, schema)).isEqualTo(2);
        BigDecimal newCost = new BigDecimal("999999999999.9900");
        db.update("update " + p + "existencias set costo_promedio=?", newCost);
        db.update("update " + p + "movimientos_inventario set costo_unitario=?", newCost);
        assertThat(db.queryForObject("select costo_promedio from " + p + "existencias", BigDecimal.class)).isEqualByComparingTo(newCost);
        assertThat(db.queryForObject("select costo_unitario from " + p + "movimientos_inventario", BigDecimal.class)).isEqualByComparingTo(newCost);
    }
}
