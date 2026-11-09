package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PurchaseCoreMigrationIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;

    @Test void upgradeFromV6PreservesDataAndAddsSchema() {
        String schema = "upgrade_v7_" + UUID.randomUUID().toString().replace("-", "");
        Flyway old = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("6").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(6);
        db.update("insert into " + schema + ".departamentos(codigo,nombre) values ('PREVIO','Previo')");
        Flyway upgrade = Flyway.configure().dataSource(dataSource).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("7").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from " + schema + ".departamentos where codigo='PREVIO'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('ordenes_compra','detalles_orden_compra','recepciones_compra','detalles_recepcion','existencias','movimientos_inventario')", Integer.class, schema)).isEqualTo(6);
        assertThat(flyway.info().applied()).hasSize(8);
        assertThat(db.queryForObject("select count(*) from " + schema + ".permisos where codigo in ('COMPRAS_ORDENES_LEER','COMPRAS_ORDENES_ADMIN','COMPRAS_ORDENES_APROBAR','COMPRAS_RECEPCIONES_LEER','COMPRAS_RECEPCIONES_REGISTRAR','COMPRAS_RECEPCIONES_CONFIRMAR','INVENTARIO_EXISTENCIAS_LEER')", Integer.class)).isEqualTo(7);
        assertThat(db.queryForObject("select count(*) from " + schema + ".roles_permisos rp join " + schema + ".roles r on r.id=rp.rol_id join " + schema + ".permisos p on p.id=rp.permiso_id where r.codigo='ADMINISTRADOR' and p.modulo in ('COMPRAS','INVENTARIO')", Integer.class)).isEqualTo(7);
        assertThat(db.queryForObject("select count(*) from information_schema.sequences where sequence_schema=? and sequence_name in ('orden_compra_folio_seq','recepcion_compra_folio_seq')", Integer.class, schema)).isEqualTo(2);
    }

    @Test void constraintsProtectCoreTransactions() {
        String key = UUID.randomUUID().toString().substring(0, 12).toUpperCase();
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        long product = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, key, "Prueba", category);
        long supplier = db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id", Long.class, key, "Prueba");
        long department = db.queryForObject("insert into departamentos(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        long warehouse = db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        String username = key.toLowerCase(java.util.Locale.ROOT);
        long user = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, username, username + "@example.test", "test-hash");
        long order = db.queryForObject("insert into ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values (?,?,?,?,?) returning id", Long.class, key, supplier, department, user, "BORRADOR");
        assertThatThrownBy(() -> db.update("insert into ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values (?,?,?,?,?)", key, supplier, department, user, "BORRADOR")).isInstanceOf(DataIntegrityViolationException.class);
        long line = db.queryForObject("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,?,?,?) returning id", Long.class, order, product, 20, 1, 16);
        assertThatThrownBy(() -> db.update("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,?,?,?)", order, product, 0, 1, 16)).isInstanceOf(DataIntegrityViolationException.class);
        long receipt = db.queryForObject("insert into recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,?) returning id", Long.class, key, order, warehouse, user, key, "PENDIENTE");
        assertThatThrownBy(() -> db.update("insert into recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,?)", key + "X", order, warehouse, user, key, "PENDIENTE")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,?,?,?,?)", receipt, line, 20, 19, 2, 1)).isInstanceOf(DataIntegrityViolationException.class);
        long detail = db.queryForObject("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,?,?,?,?) returning id", Long.class, receipt, line, 20, 18, 2, 1);
        db.update("insert into existencias(producto_id,almacen_id,cantidad_fisica) values (?,?,?)", product, warehouse, 18);
        assertThatThrownBy(() -> db.update("insert into existencias(producto_id,almacen_id) values (?,?)", product, warehouse)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("update existencias set cantidad_reservada=19 where producto_id=? and almacen_id=?", product, warehouse)).isInstanceOf(DataIntegrityViolationException.class);
        db.update("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,usuario_id,detalle_recepcion_id,clave_operacion) values (?,?,?,?,?,?,?,?)", product, warehouse, "ENTRADA", "RECEPCION_COMPRA", 18, user, detail, key);
        assertThatThrownBy(() -> db.update("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,usuario_id,detalle_recepcion_id,clave_operacion) values (?,?,?,?,?,?,?,?)", product, warehouse, "ENTRADA", "RECEPCION_COMPRA", 18, user, detail, key + "X")).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("delete from productos where id=?", product)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
