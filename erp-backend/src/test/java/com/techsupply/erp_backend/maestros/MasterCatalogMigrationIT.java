package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.Locale;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class MasterCatalogMigrationIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired DataSource dataSource;
    @Autowired Flyway flyway;

    private static String code() {
        return "T" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    }

    @Test
    void flywayCreatesV6CatalogsAndPermissions() {
        assertThat(flyway.info().applied()).hasSize(13);
        assertThat(db.queryForObject("""
            select count(*) from information_schema.tables
            where table_schema = current_schema()
              and table_name in ('departamentos', 'proveedores', 'almacenes', 'productos')
            """, Integer.class)).isEqualTo(4);
        assertThat(db.queryForObject("select count(*) from permisos where modulo='MAESTROS' and codigo <> 'MAESTROS_CATEGORIAS_LEER' and codigo <> 'MAESTROS_CATEGORIAS_ADMIN' and codigo not like 'MAESTROS_CLIENTES_%'", Integer.class))
            .isEqualTo(8);
        assertThat(db.queryForObject("""
            select count(*) from roles_permisos rp
            join roles r on r.id = rp.rol_id
            join permisos p on p.id = rp.permiso_id
            where r.codigo = 'ADMINISTRADOR' and p.modulo = 'MAESTROS' and p.codigo not like 'MAESTROS_CLIENTES_%'
            """, Integer.class)).isEqualTo(10);
    }

    @Test
    void databaseRejectsInvalidMasterDataAndPreservesReferences() {
        String departmentCode = code();
        db.update("insert into departamentos(codigo,nombre) values (?,?)", departmentCode, "Compras");
        assertThatThrownBy(() -> db.update("insert into departamentos(codigo,nombre) values (?,?)", departmentCode, "Otro"))
            .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into departamentos(codigo,nombre) values (?,?)", "minusculas", "Otro"))
            .isInstanceOf(DataIntegrityViolationException.class);

        String supplierCode = code();
        long supplier = db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id",
            Long.class, supplierCode, "Proveedor de prueba");
        assertThatThrownBy(() -> db.update("insert into proveedores(codigo,nombre_razon_social,plazo_credito_dias) values (?,?,?)",
            code(), "Otro", -1)).isInstanceOf(DataIntegrityViolationException.class);

        db.update("insert into almacenes(codigo,nombre) values (?,?)", code(), "Principal");
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id",
            Long.class, code(), "Componentes");
        String sku = code();
        long product = db.queryForObject("""
            insert into productos(sku,nombre,categoria_id,proveedor_principal_id)
            values (?,?,?,?) returning id
            """, Long.class, sku, "Producto de prueba", category, supplier);
        assertThat(db.queryForObject("select activo from productos where id=?", Boolean.class, product)).isTrue();
        assertThat(db.queryForObject("select precio_lista from productos where id=?", java.math.BigDecimal.class, product))
            .isEqualByComparingTo("0.00");
        assertThatThrownBy(() -> db.update("insert into productos(sku,nombre,categoria_id) values (?,?,?)",
            sku, "Duplicado", category)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into productos(sku,nombre,categoria_id) values (?,?,?)",
            code(), "Sin categoría", Long.MAX_VALUE)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into productos(sku,nombre,categoria_id,precio_lista) values (?,?,?,?)",
            code(), "Precio inválido", category, -1)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into productos(sku,nombre,categoria_id,tasa_impuesto) values (?,?,?,?)",
            code(), "Tasa inválida", category, 101)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("delete from proveedores where id=?", supplier))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void existingV5DatabaseUpgradesToV6WithoutLosingData() {
        String schema = "upgrade_v6_" + UUID.randomUUID().toString().replace("-", "");
        Flyway throughV5 = Flyway.configure().dataSource(dataSource)
            .schemas(schema).defaultSchema(schema).locations("classpath:db/migration")
            .target("5").load();
        assertThat(throughV5.migrate().migrationsExecuted).isEqualTo(5);
        JdbcTemplate isolated = new JdbcTemplate(dataSource);
        isolated.update("insert into " + schema + ".categorias_producto(codigo,nombre) values (?,?)", code(), "Preexistente");

        Flyway throughV6 = Flyway.configure().dataSource(dataSource)
            .schemas(schema).defaultSchema(schema).locations("classpath:db/migration")
            .target("6").load();
        assertThat(throughV6.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(isolated.queryForObject("select count(*) from " + schema + ".categorias_producto", Integer.class))
            .isEqualTo(1);
        assertThat(isolated.queryForObject("""
            select count(*) from information_schema.tables
            where table_schema = ? and table_name in ('departamentos', 'proveedores', 'almacenes', 'productos')
            """, Integer.class, schema)).isEqualTo(4);
        assertThat(isolated.queryForObject("select count(*) from " + schema + ".productos", Integer.class)).isZero();
        assertThat(isolated.queryForObject("select count(*) from " + schema + ".proveedores", Integer.class)).isZero();
    }
}
