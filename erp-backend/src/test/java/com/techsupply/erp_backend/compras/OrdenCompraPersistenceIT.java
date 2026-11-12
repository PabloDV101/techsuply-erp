package com.techsupply.erp_backend.compras;

import com.techsupply.erp_backend.compras.domain.model.*;
import com.techsupply.erp_backend.compras.infrastructure.persistence.OrdenCompraRepository;
import com.techsupply.erp_backend.maestros.domain.model.*;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class OrdenCompraPersistenceIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db;
    @Autowired EntityManager em;
    @Autowired OrdenCompraRepository orders;

    @Test @Transactional void savesReloadsAndPreservesAgreedValuesAndTransitions() {
        String key = "T" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        long productId = db.queryForObject("insert into productos(sku,nombre,categoria_id,precio_lista) values (?,?,?,?) returning id", Long.class, key, "Prueba", category, 10);
        long supplierId = db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id", Long.class, key, "Prueba");
        long deptId = db.queryForObject("insert into departamentos(codigo,nombre) values (?,?) returning id", Long.class, key, "Compras");
        String username = key.toLowerCase(Locale.ROOT);
        long creatorId = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, username, username + "@example.test", "test-hash");
        long approverId = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, username + "a", username + "a@example.test", "test-hash");
        Usuario creator = em.find(Usuario.class, creatorId);
        Usuario approver = em.find(Usuario.class, approverId);
        Producto product = em.find(Producto.class, productId);
        OrdenCompra order = new OrdenCompra("OC-" + key, em.find(Proveedor.class, supplierId), em.find(Departamento.class, deptId), creator, Instant.now(), null);
        assertThatThrownBy(order::registrarEnvio).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(order::enviarAprobacion).isInstanceOf(IllegalStateException.class);
        order.agregarDetalle(new DetalleOrdenCompra(product, new BigDecimal("3.000"), new BigDecimal("10.00"), new BigDecimal("10.00"), new BigDecimal("16.00")));
        assertThatThrownBy(() -> order.agregarDetalle(new DetalleOrdenCompra(product, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO))).isInstanceOf(IllegalArgumentException.class);
        order.enviarAprobacion();
        assertThatThrownBy(() -> order.aprobar(creator, Instant.now())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> order.agregarDetalle(new DetalleOrdenCompra(product, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.ZERO))).isInstanceOf(IllegalStateException.class);
        order.aprobar(approver, Instant.now());
        order.registrarEnvio();
        long orderId = orders.saveAndFlush(order).getId();
        product.setPrecioLista(new BigDecimal("55.00"));
        em.flush(); em.clear();
        OrdenCompra loaded = orders.findById(orderId).orElseThrow();
        assertThat(loaded.getEstado()).isEqualTo(EstadoOrdenCompra.ENVIADA);
        assertThat(loaded.getAprobadaPor().getId()).isEqualTo(approverId);
        assertThat(loaded.getTotal()).isEqualByComparingTo("31.32");
        assertThat(loaded.getDetalles()).hasSize(1);
        assertThat(loaded.getDetalles().getFirst().getPrecioUnitario()).isEqualByComparingTo("10.00");
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?", Integer.class, productId)).isZero();

        OrdenCompra altered = new OrdenCompra("OC-X" + key, em.find(Proveedor.class, supplierId), em.find(Departamento.class, deptId),
            em.find(Usuario.class, creatorId), Instant.now(), null);
        altered.agregarDetalle(new DetalleOrdenCompra(em.find(Producto.class, productId), BigDecimal.ONE, BigDecimal.TEN, BigDecimal.ZERO, BigDecimal.ZERO));
        altered.enviarAprobacion();
        long alteredId = orders.saveAndFlush(altered).getId();
        db.update("update ordenes_compra set total=99 where id=?", alteredId);
        em.clear();
        OrdenCompra invalid = orders.findById(alteredId).orElseThrow();
        assertThatThrownBy(() -> invalid.aprobar(em.find(Usuario.class, approverId), Instant.now())).isInstanceOf(IllegalStateException.class);
        assertThat(invalid.getEstado()).isEqualTo(EstadoOrdenCompra.PENDIENTE_APROBACION);
    }
}
