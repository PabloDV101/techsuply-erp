package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.sql.SQLException;
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
class AccountingMigrationIT extends PostgresIntegrationSupport {
    @Autowired DataSource source;
    @Autowired JdbcTemplate db;
    @Autowired Flyway currentFlyway;
    @Autowired PlatformTransactionManager transactions;

    private long id(String sql, Object... args) { return db.queryForObject(sql, Long.class, args); }

    @Test void actualizaV24AReporteriaSinInferirPoliticas() {
        String schema="upgrade_v24_"+UUID.randomUUID().toString().replace("-", "");
        var before=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("24").load();
        assertThat(before.migrate().migrationsExecuted).isEqualTo(24);
        long supplier=id("insert into "+schema+".proveedores(codigo,nombre_razon_social) values ('PREVIO','Proveedor previo') returning id");
        var after=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("25").load();
        assertThat(after.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select nombre_razon_social from "+schema+".proveedores where id=?",String.class,supplier)).isEqualTo("Proveedor previo");
        assertThat(db.queryForObject("select count(*) from "+schema+".minimos_producto_almacen",Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from "+schema+".clasificaciones_margen_cuenta",Integer.class)).isZero();
        assertThat(after.validateWithResult().validationSuccessful).isTrue();
    }

    @Test void actualizaV23AClasificacionFlujoSinAlterarProveedor() {
        String schema="upgrade_v23_"+UUID.randomUUID().toString().replace("-", "");
        var before=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("23").load();
        assertThat(before.migrate().migrationsExecuted).isEqualTo(23);
        long supplier=id("insert into "+schema+".proveedores(codigo,nombre_razon_social) values ('PREVIO','Proveedor previo') returning id");
        var after=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("24").load();
        assertThat(after.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select nombre_razon_social from "+schema+".proveedores where id=?",String.class,supplier)).isEqualTo("Proveedor previo");
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('clasificaciones_flujo_cuenta','clasificaciones_flujo_partida')",Integer.class,schema)).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from "+schema+".permisos where codigo='CONTABILIDAD_FLUJO_CLASIFICAR'",Integer.class)).isEqualTo(1);
    }

    @Test void actualizaV21APagosConservandoDatos() {
        String schema="upgrade_v21_"+UUID.randomUUID().toString().replace("-", "");
        var before=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("21").load();
        assertThat(before.migrate().migrationsExecuted).isEqualTo(21);
        long supplier=id("insert into "+schema+".proveedores(codigo,nombre_razon_social) values ('PREVIO','Proveedor previo') returning id");
        var after=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("23").load();
        assertThat(after.migrate().migrationsExecuted).isEqualTo(2);
        assertThat(db.queryForObject("select nombre_razon_social from "+schema+".proveedores where id=?",String.class,supplier)).isEqualTo("Proveedor previo");
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('pagos_proveedor','aplicaciones_pago_proveedor','configuracion_cuenta_pago_proveedor')",Integer.class,schema)).isEqualTo(3);
        assertThat(db.queryForObject("select count(*) from "+schema+".permisos where codigo like 'CONTABILIDAD_PAGOS_PROVEEDOR_%'",Integer.class)).isEqualTo(4);
    }

    @Test void actualizaV13SinAlterarDatosYRegistraCatalogoSinActivarApi() {
        String schema = "upgrade_v14_" + UUID.randomUUID().toString().replace("-", "");
        String p = schema + ".";
        var old = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("13").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(13);
        long user = new TransactionTemplate(transactions).execute(status -> {
            db.execute("set local search_path to " + schema + ", public");
            return id("insert into " + p + "usuarios(username,correo,password_hash) values ('hist','hist@example.test','hash') returning id");
        });
        long customer = id("insert into " + p + "clientes(codigo,nombre_razon_social) values ('HIST','Cliente previo') returning id");
        long order = new TransactionTemplate(transactions).execute(status -> {
            db.execute("set local search_path to " + schema + ", public");
            long warehouse = id("insert into " + p + "almacenes(codigo,nombre) values ('A','Almacen previo') returning id");
            long quote = id("insert into " + p + "cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia) values ('Q',?,?,current_date,'ACEPTADA','Q') returning id",customer,user);
            return id("insert into " + p + "pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,clave_idempotencia) values ('P',?,?,?,?,?,'INTERNO','CONTADO','CANCELADO','P') returning id",customer,user,quote,warehouse,user);
        });
        var upgrade = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("14").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select username from " + p + "usuarios where id=?", String.class, user)).isEqualTo("hist");
        assertThat(db.queryForObject("select nombre_razon_social from " + p + "clientes where id=?", String.class, customer)).isEqualTo("Cliente previo");
        assertThat(db.queryForObject("select estado from " + p + "pedidos_venta where id=?", String.class, order)).isEqualTo("CANCELADO");
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('cuentas_contables','periodos_contables','asientos_contables','partidas_asiento')", Integer.class, schema)).isEqualTo(4);
        assertThat(db.queryForObject("select count(*) from " + p + "permisos where modulo='CONTABILIDAD'", Integer.class)).isEqualTo(9);
        assertThat(db.queryForObject("select count(*) from " + p + "cuentas_contables", Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from " + p + "asientos_contables", Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from " + p + "roles_permisos rp join " + p + "roles r on r.id=rp.rol_id join " + p + "permisos perm on perm.id=rp.permiso_id where r.codigo='ADMINISTRADOR' and perm.modulo='CONTABILIDAD'", Integer.class)).isEqualTo(9);
        var lifecycle = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("15").load();
        assertThat(lifecycle.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name='historial_asientos_contables'", Integer.class, schema)).isEqualTo(1);
        assertThat(db.queryForObject("select character_maximum_length from information_schema.columns where table_schema=? and table_name='asientos_contables' and column_name='estado'", Integer.class, schema)).isEqualTo(16);
        var reporting = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("16").load();
        assertThat(reporting.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from " + p + "permisos where modulo='CONTABILIDAD'", Integer.class)).isEqualTo(10);
        assertThat(db.queryForObject("select count(*) from " + p + "roles_permisos rp join " + p + "roles r on r.id=rp.rol_id join " + p + "permisos perm on perm.id=rp.permiso_id where r.codigo='ADMINISTRADOR' and perm.modulo='CONTABILIDAD'", Integer.class)).isEqualTo(10);
        var integration = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("17").load();
        assertThat(integration.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('configuracion_cuentas_factura_venta','integracion_factura_venta_contabilidad')", Integer.class, schema)).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from " + p + "permisos where modulo='CONTABILIDAD'", Integer.class)).isEqualTo(13);
        assertThat(db.queryForObject("select count(*) from " + p + "roles_permisos rp join " + p + "roles r on r.id=rp.rol_id join " + p + "permisos perm on perm.id=rp.permiso_id where r.codigo='ADMINISTRADOR' and perm.modulo='CONTABILIDAD'", Integer.class)).isEqualTo(13);
        var receivables = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("18").load();
        assertThat(receivables.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name='cuentas_por_cobrar'", Integer.class, schema)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from " + p + "cuentas_por_cobrar", Integer.class)).isZero();
        assertThat(db.queryForObject("select count(*) from " + p + "permisos where codigo='CONTABILIDAD_CXC_LEER'", Integer.class)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from " + p + "roles_permisos rp join " + p + "roles r on r.id=rp.rol_id join " + p + "permisos perm on perm.id=rp.permiso_id where r.codigo='ADMINISTRADOR' and perm.codigo='CONTABILIDAD_CXC_LEER'", Integer.class)).isEqualTo(1);
        var receipts = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("19").load();
        assertThat(receipts.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('cobros_clientes','aplicaciones_cobro','configuracion_cuentas_cobro')", Integer.class, schema)).isEqualTo(3);
        assertThat(db.queryForObject("select count(*) from " + p + "permisos where modulo='CONTABILIDAD'", Integer.class)).isEqualTo(18);
        var inventory = Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema)
            .locations("classpath:db/migration").target("20").load();
        assertThat(inventory.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name='configuracion_cuentas_inventario'", Integer.class, schema)).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from " + p + "permisos where modulo='CONTABILIDAD'", Integer.class)).isEqualTo(20);
        assertThat(db.queryForObject("select count(*) from " + p + "roles_permisos rp join " + p + "roles r on r.id=rp.rol_id join " + p + "permisos perm on perm.id=rp.permiso_id where r.codigo='ADMINISTRADOR' and perm.modulo='CONTABILIDAD'", Integer.class)).isEqualTo(20);
        assertThat(currentFlyway.info().applied()).hasSize(25);
    }

    @Test void restringePeriodosYPartidasYNoHabilitaContabilizacion() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        long user = id("insert into usuarios(username,correo,password_hash) values (?,?, 'hash') returning id", "account" + tag, tag + "@example.test");
        long account = id("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?, 'Cuenta', 'ACTIVO', 'DEUDORA') returning id", tag);
        assertThatThrownBy(() -> db.update("insert into cuentas_contables(codigo,nombre,tipo,naturaleza) values (?, 'Repetida', 'ACTIVO', 'DEUDORA')",tag)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into cuentas_contables(codigo,nombre,tipo,naturaleza,cuenta_padre_id) values (?, 'Sin padre', 'ACTIVO', 'DEUDORA', -1)","X"+tag)).isInstanceOf(DataIntegrityViolationException.class);
        long period = id("insert into periodos_contables(fecha_inicio,fecha_fin,estado) values ('2031-01-01','2031-01-31','ABIERTO') returning id");
        assertThatThrownBy(() -> db.update("insert into periodos_contables(fecha_inicio,fecha_fin,estado) values ('2031-01-31','2031-02-28','ABIERTO')")).isInstanceOf(DataIntegrityViolationException.class);
        long entry = id("insert into asientos_contables(folio,periodo_id,fecha_contable,descripcion,origen_modulo,origen_tipo,clave_idempotencia,creado_por_usuario_id,estado) values (?,?, '2031-01-15','Borrador','MANUAL','MANUAL',?,?,'BORRADOR') returning id", "F"+tag,period,"K"+tag,user);
        db.update("insert into partidas_asiento(asiento_id,numero_linea,cuenta_contable_id,debe,haber) values (?,1,?,100.00,0)", entry, account);
        assertThatThrownBy(() -> db.update("insert into partidas_asiento(asiento_id,numero_linea,cuenta_contable_id,debe,haber) values (?,2,?,1.001,0)", entry, account)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into partidas_asiento(asiento_id,numero_linea,cuenta_contable_id,debe,haber) values (?,2,?,1.000,0)", entry, account)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into partidas_asiento(asiento_id,numero_linea,cuenta_contable_id,debe,haber) values (?,2,?,1000000000000,0)", entry, account)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into partidas_asiento(asiento_id,numero_linea,cuenta_contable_id,debe,haber) values (?,2,?,1,1)", entry, account)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("insert into partidas_asiento(asiento_id,numero_linea,cuenta_contable_id,debe,haber) values (?,1,?,0,1)", entry, account)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> db.update("update asientos_contables set estado='CONTABILIZADO', contabilizado_por_usuario_id=?, contabilizado_en=now() where id=?",user,entry)).isInstanceOf(DataIntegrityViolationException.class);
        db.update("update periodos_contables set estado='CERRADO', cerrado_por_usuario_id=?, cerrado_en=now() where id=?",user,period);
        assertThatThrownBy(() -> db.update("update periodos_contables set estado='ABIERTO', cerrado_por_usuario_id=null, cerrado_en=null where id=?",period)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test void exclusionDePeriodosProtegeInsercionesConcurrentes() throws Exception {
        try (var first = source.getConnection(); var second = source.getConnection(); var pool = Executors.newSingleThreadExecutor()) {
            first.setAutoCommit(false);
            try (var statement = first.createStatement()) {
                statement.executeUpdate("insert into periodos_contables(fecha_inicio,fecha_fin,estado) values ('2033-04-01','2033-04-30','ABIERTO')");
            }
            CountDownLatch started = new CountDownLatch(1);
            var concurrent = pool.submit(() -> {
                try (var statement = second.createStatement()) {
                    statement.execute("set lock_timeout='5s'");
                    started.countDown();
                    statement.executeUpdate("insert into periodos_contables(fecha_inicio,fecha_fin,estado) values ('2033-04-30','2033-05-31','ABIERTO')");
                    return "insertado";
                } catch (SQLException e) {
                    return e.getSQLState();
                }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> concurrent.get(250, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            first.commit();
            assertThat(concurrent.get(5, TimeUnit.SECONDS)).isEqualTo("23P01");
        }
    }
}
