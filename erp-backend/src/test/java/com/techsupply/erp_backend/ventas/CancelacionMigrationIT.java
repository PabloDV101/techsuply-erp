package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
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
class CancelacionMigrationIT extends PostgresIntegrationSupport {
    @Autowired DataSource source; @Autowired JdbcTemplate db; @Autowired PlatformTransactionManager transactions;
    long id(String sql,Object... args) {return db.queryForObject(sql,Long.class,args);}
    @Test void upgradeDesdeV11ConservaPedidosYValidaMetadatos() {
        String schema="upgrade_v12_"+UUID.randomUUID().toString().replace("-", ""),p=schema+".";
        Flyway old=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("11").load();
        assertThat(old.migrate().migrationsExecuted).isEqualTo(11);
        long[] fixture=new TransactionTemplate(transactions).execute(status->{
            db.execute("set local search_path to "+schema+", public");
            long user=id("insert into "+p+"usuarios(username,correo,password_hash) values ('u','u@example.test','hash') returning id");
            long client=id("insert into "+p+"clientes(codigo,nombre_razon_social) values ('C','Cliente') returning id");
            long warehouse=id("insert into "+p+"almacenes(codigo,nombre) values ('A','Almacen') returning id");
            long quote=id("insert into "+p+"cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia) values ('Q',?,?,current_date,'ACEPTADA','Q') returning id",client,user);
            long order=id("insert into "+p+"pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,clave_idempotencia) values ('P',?,?,?,?,?,'INTERNO','CONTADO','CANCELADO','P') returning id",client,user,quote,warehouse,user);
            return new long[]{user,order,client,warehouse};
        });
        long user=fixture[0],order=fixture[1],client=fixture[2],warehouse=fixture[3];
        Flyway upgrade=Flyway.configure().dataSource(source).schemas(schema).defaultSchema(schema).locations("classpath:db/migration").target("12").load();
        assertThat(upgrade.migrate().migrationsExecuted).isEqualTo(1);
        assertThat(db.queryForObject("select estado from "+p+"pedidos_venta where id=?",String.class,order)).isEqualTo("CANCELADO");
        assertThat(db.queryForObject("select motivo_cancelacion from "+p+"pedidos_venta where id=?",String.class,order)).isNull();
        assertThatThrownBy(()->db.update("update "+p+"pedidos_venta set estado='BORRADOR' where id=?",order)).isInstanceOf(DataIntegrityViolationException.class);
        long quote=id("insert into "+p+"cotizaciones(folio,cliente_id,vendedor_usuario_id,vigencia_hasta,estado,clave_idempotencia) values ('Q2',?,?,current_date,'ACEPTADA','Q2') returning id",client,user);
        long next=id("insert into "+p+"pedidos_venta(folio,cliente_id,vendedor_usuario_id,cotizacion_id,almacen_id,creado_por_usuario_id,canal,modalidad,estado,clave_idempotencia) values ('P2',?,?,?,?,?,'INTERNO','CONTADO','BORRADOR','P2') returning id",client,user,quote,warehouse,user);
        assertThatThrownBy(()->db.update("update "+p+"pedidos_venta set motivo_cancelacion='a' where id=?",next)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("update "+p+"pedidos_venta set estado='CANCELADO',motivo_cancelacion=' ',cancelado_por_usuario_id=?,cancelado_en=now() where id=?",user,next)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("update "+p+"pedidos_venta set estado='CANCELADO',motivo_cancelacion='Motivo',cancelado_por_usuario_id=999999,cancelado_en=now() where id=?",next)).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(db.update("update "+p+"pedidos_venta set estado='CANCELADO',motivo_cancelacion='Motivo',cancelado_por_usuario_id=?,cancelado_en=now() where id=?",user,next)).isEqualTo(1);
        assertThatThrownBy(()->db.update("update "+p+"pedidos_venta set motivo_cancelacion='Cambiado' where id=?",next)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(()->db.update("delete from "+p+"pedidos_venta where id=?",next)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
