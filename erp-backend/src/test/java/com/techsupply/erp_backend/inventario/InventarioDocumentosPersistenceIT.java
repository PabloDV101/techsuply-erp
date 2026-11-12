package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.domain.model.*;
import com.techsupply.erp_backend.inventario.infrastructure.persistence.*;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class InventarioDocumentosPersistenceIT extends PostgresIntegrationSupport {
    @Autowired JdbcTemplate db; @Autowired EntityManager em;
    @Autowired ConteoInventarioRepository counts; @Autowired DetalleConteoRepository countDetails;
    @Autowired AjusteInventarioRepository adjustments; @Autowired DetalleAjusteInventarioRepository adjustmentDetails;
    @Autowired MovimientoInventarioRepository movements;
    @Test @Transactional void guardaRecargaDocumentosYLeeFuenteDeAjusteSinCambiarStock() {
        String key = "T" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
        long category = db.queryForObject("insert into categorias_producto(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        long product = db.queryForObject("insert into productos(sku,nombre,categoria_id) values (?,?,?) returning id", Long.class, key, "Prueba", category);
        long warehouse = db.queryForObject("insert into almacenes(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        String username = key.toLowerCase(Locale.ROOT);
        long creator = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, username, username + "@example.test", "test-hash");
        long approver = db.queryForObject("insert into usuarios(username,correo,password_hash) values (?,?,?) returning id", Long.class, username + "a", username + "a@example.test", "test-hash");
        ConteoInventario count = new ConteoInventario(key, warehouse, creator, LocalDate.of(2026, 11, 10), ClaseConteo.CICLICO);
        count.capturar(product, BigDecimal.ZERO, new BigDecimal("2.000"), false, null);
        counts.save(count); em.flush(); count.cerrar(); em.flush();
        long countId = count.getId(), countDetailId = count.getDetalles().getFirst().getId();
        AjusteInventario adjustment = new AjusteInventario(key, warehouse, "Diferencia", creator, key, Instant.parse("2026-11-10T12:00:00Z"));
        adjustment.agregarDetalle(product, TipoAjuste.POSITIVO, new BigDecimal("2.000"), new BigDecimal("999999999999.9900"), countDetailId, null);
        adjustment.aprobar(approver, Instant.parse("2026-11-10T13:00:00Z"));
        adjustments.save(adjustment); em.flush();
        long adjustmentId = adjustment.getId(), detailId = adjustment.getDetalles().getFirst().getId();
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?", Integer.class, product)).isZero();
        // Synthetic rows verify both JPA sources; they do not represent adjustment application.
        long movementId = db.queryForObject("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_ajuste_id,clave_operacion) values (?,?,'ENTRADA','AJUSTE',2,?,?,?,?) returning id", Long.class, product, warehouse, new BigDecimal("999999999999.9900"), approver, detailId, "AJUSTE:" + detailId);
        long supplier = db.queryForObject("insert into proveedores(codigo,nombre_razon_social) values (?,?) returning id", Long.class, key, "Prueba");
        long dept = db.queryForObject("insert into departamentos(codigo,nombre) values (?,?) returning id", Long.class, key, "Prueba");
        long order = db.queryForObject("insert into ordenes_compra(folio,proveedor_id,departamento_id,creada_por_usuario_id,estado) values (?,?,?,?,?) returning id", Long.class, key, supplier, dept, creator, "ENVIADA");
        long line = db.queryForObject("insert into detalles_orden_compra(orden_compra_id,producto_id,cantidad,precio_unitario,tasa_impuesto) values (?,?,?,?,?) returning id", Long.class, order, product, 1, 1, 0);
        long receipt = db.queryForObject("insert into recepciones_compra(folio,orden_compra_id,almacen_id,registrada_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,?) returning id", Long.class, key, order, warehouse, creator, key, "PENDIENTE");
        long receiptDetail = db.queryForObject("insert into detalles_recepcion(recepcion_id,detalle_orden_compra_id,cantidad_recibida,cantidad_aceptada,cantidad_rechazada,precio_unitario_recibido) values (?,?,?,?,?,?) returning id", Long.class, receipt, line, 1, 1, 0, 1);
        long receiptMovement = db.queryForObject("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_recepcion_id,clave_operacion) values (?,?,'ENTRADA','RECEPCION_COMPRA',1,1,?,?,?) returning id", Long.class, product, warehouse, creator, receiptDetail, "RECEPCION:" + receiptDetail);
        em.clear();
        ConteoInventario loaded = counts.findById(countId).orElseThrow();
        assertThat(loaded.getEstado()).isEqualTo(EstadoConteo.CERRADO);
        assertThat(loaded.getDetalles()).hasSize(1);
        assertThat(countDetails.findById(countDetailId).orElseThrow().getDiferencia()).isEqualByComparingTo("2.000");
        AjusteInventario saved = adjustments.findByClaveIdempotencia(key).orElseThrow();
        assertThat(saved.getId()).isEqualTo(adjustmentId);
        assertThat(saved.getEstado()).isEqualTo(EstadoAjuste.APROBADO);
        assertThat(saved.getAutorizadoPorUsuarioId()).isEqualTo(approver);
        assertThat(adjustmentDetails.findById(detailId).orElseThrow().getCostoUnitario()).isEqualByComparingTo("999999999999.9900");
        MovimientoInventario movement = movements.findById(movementId).orElseThrow();
        assertThat(movement.getDetalleAjusteId()).isEqualTo(detailId);
        assertThat(movement.getDetalleRecepcionId()).isNull();
        MovimientoInventario receiptSource = movements.findById(receiptMovement).orElseThrow();
        assertThat(receiptSource.getDetalleRecepcionId()).isEqualTo(receiptDetail);
        assertThat(receiptSource.getDetalleAjusteId()).isNull();
        assertThat(db.queryForObject("select count(*) from existencias where producto_id=?", Integer.class, product)).isZero();
    }
}
