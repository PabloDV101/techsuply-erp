package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class FacturaMigrationIT extends PostgresIntegrationSupport {
    @Autowired DataSource source; @Autowired JdbcTemplate db; @Autowired PlatformTransactionManager transactions;
    long id(String sql,Object... args){return db.queryForObject(sql,Long.class,args);}
    @Test void upgradeDesdeV12ConservaPedidoYAgregaEsquemaYPermisos() {
        String schema="upgrade_v13_"+UUID.randomUUID().toString().replace("-", ""),p=schema+".";
        var old=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("12").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(12);
        long order=new TransactionTemplate(transactions).execute(status->{
            db.execute("set local search_path to "+schema+", public");
            long user=id("insert into "+p+"usuarios(username,correo,password_hash) values ('u','u@example.test','hash') returning id");
            long customer=id("insert into "+p+"clientes(codigo,nombre_razon_social) values ('C','Cliente') returning id");
            long warehouse=id("insert into "+p+"almacenes(codigo,nombre) values ('A','Almacen') returning id");
            long quote=id("insert into "+p+"cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia) values ('Q',?,?,current_date,'ACEPTADA','Q') returning id",customer,user);
            return id("insert into "+p+"pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,clave_idempotencia) values ('P',?,?,?,?,?,'INTERNO','CONTADO','CANCELADO','P') returning id",customer,user,quote,warehouse,user);
        });
        var upgrade=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("13").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select estado from "+p+"pedidos_venta where id=?",String.class,order)).isEqualTo("CANCELADO");
        assertThat(db.queryForObject("select count(*) from information_schema.tables where table_schema=? and table_name in ('facturas_venta','detalles_factura_venta')",Integer.class,schema)).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from pg_indexes where schemaname=? and indexname in ('idx_facturas_venta_cliente_fecha','idx_detalles_factura_venta_pedido')",Integer.class,schema)).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from "+p+"permisos where codigo in ('VENTAS_FACTURAS_LEER','VENTAS_FACTURAS_EMITIR')",Integer.class)).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from "+p+"roles_permisos rp join "+p+"roles r on r.id=rp.rol_id join "+p+"permisos perm on perm.id=rp.permiso_id where r.codigo='ADMINISTRADOR' and perm.codigo in ('VENTAS_FACTURAS_LEER','VENTAS_FACTURAS_EMITIR')",Integer.class)).isEqualTo(2);
    }
}
