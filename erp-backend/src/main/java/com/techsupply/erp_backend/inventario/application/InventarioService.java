package com.techsupply.erp_backend.inventario.application;

import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.dto.ExistenciaResponse;
import com.techsupply.erp_backend.inventario.domain.model.Existencia;
import com.techsupply.erp_backend.inventario.domain.model.ValoracionInventario;
import com.techsupply.erp_backend.inventario.infrastructure.persistence.ExistenciaRepository;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.math.BigDecimal;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventarioService {
    private final ExistenciaRepository stocks;
    private final JdbcTemplate db;
    private final AuthenticatedUserProvider users;
    public InventarioService(ExistenciaRepository stocks, JdbcTemplate db, AuthenticatedUserProvider users) {
        this.stocks = stocks; this.db = db; this.users = users;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INVENTARIO_EXISTENCIAS_LEER')")
    public PageResponse<ExistenciaResponse> listar(int page, int size, String sort, String direction, Long productoId, Long almacenId) {
        if ((productoId != null && productoId < 1) || (almacenId != null && almacenId < 1))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de inventario inválido");
        Specification<Existencia> spec = (root, query, cb) -> {
            var conditions = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (productoId != null) conditions.add(cb.equal(root.get("productoId"), productoId));
            if (almacenId != null) conditions.add(cb.equal(root.get("almacenId"), almacenId));
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PageResponse.from(stocks.findAll(spec, PageRequests.of(page, size, sort, direction,
            Map.of("id", "id", "productoId", "productoId", "almacenId", "almacenId"), "id")),
            e -> new ExistenciaResponse(e.getProductoId(), e.getAlmacenId(), e.getCantidadFisica(),
                e.getCantidadReservada(), e.getCantidadFisica().subtract(e.getCantidadReservada())));
    }

    /** The caller confirms and audits the receipt in this same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasAuthority('COMPRAS_RECEPCIONES_CONFIRMAR')")
    public void registrarEntradaRecepcion(long detalleId) {
        Long actor = users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida")).usuarioId();
        var source = db.query("""
            select d.cantidad_recibida,d.cantidad_aceptada,d.cantidad_rechazada,d.precio_unitario_recibido,
                   l.producto_id,h.almacen_id,h.estado,o.estado as orden_estado,a.activo as almacen_activo
            from detalles_recepcion d join recepciones_compra h on h.id=d.recepcion_id
            join detalles_orden_compra l on l.id=d.detalle_orden_compra_id
            join ordenes_compra o on o.id=h.orden_compra_id and o.id=l.orden_compra_id
            join almacenes a on a.id=h.almacen_id
            where d.id=? for update of h,d for share of a
            """, (rs, row) -> new Fuente(rs.getLong("producto_id"), rs.getLong("almacen_id"),
                rs.getBigDecimal("cantidad_recibida"), rs.getBigDecimal("cantidad_aceptada"),
                rs.getBigDecimal("cantidad_rechazada"), rs.getBigDecimal("precio_unitario_recibido"),
                rs.getString("estado"), rs.getString("orden_estado"), rs.getBoolean("almacen_activo")), detalleId);
        if (source.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Detalle de recepción no encontrado");
        Fuente f = source.getFirst();
        if (!f.almacenActivo() || !f.estado().equals("PENDIENTE") || !(f.ordenEstado().equals("ENVIADA") || f.ordenEstado().equals("PARCIALMENTE_RECIBIDA"))
            || f.aceptada().add(f.rechazada()).compareTo(f.recibida()) != 0 || f.aceptada().signum() <= 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Detalle de recepción no válido para entrada");
        if (Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from movimientos_inventario where detalle_recepcion_id=?)", Boolean.class, detalleId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El detalle ya tiene movimiento");
        db.update("insert into existencias(producto_id,almacen_id) values (?,?) on conflict (producto_id,almacen_id) do nothing", f.productoId(), f.almacenId());
        var current = db.queryForObject("select cantidad_fisica,costo_promedio from existencias where producto_id=? and almacen_id=? for update",
            (rs, row) -> new BigDecimal[] {rs.getBigDecimal(1), rs.getBigDecimal(2)}, f.productoId(), f.almacenId());
        ValoracionInventario.Resultado result;
        try { result = ValoracionInventario.entrada(current[0], current[1], f.aceptada(), f.costo()); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Existencia o costo fuera de límites"); }
        db.update("update existencias set cantidad_fisica=?, costo_promedio=?, version=version+1 where producto_id=? and almacen_id=?",
            result.cantidadFisica(), result.costoPromedio(), f.productoId(), f.almacenId());
        db.update("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_recepcion_id,clave_operacion) values (?,?,'ENTRADA','RECEPCION_COMPRA',?,?,?,?,?)",
            f.productoId(), f.almacenId(), f.aceptada(), f.costo(), actor, detalleId, "RECEPCION:" + detalleId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                Boolean confirmed = db.queryForObject("select exists(select 1 from detalles_recepcion d join recepciones_compra h on h.id=d.recepcion_id where d.id=? and h.estado='CONFIRMADA')", Boolean.class, detalleId);
                if (!Boolean.TRUE.equals(confirmed)) throw new IllegalStateException("La recepción debe confirmarse en la misma transacción");
            }
        });
    }
    private record Fuente(long productoId, long almacenId, BigDecimal recibida, BigDecimal aceptada,
        BigDecimal rechazada, BigDecimal costo, String estado, String ordenEstado, boolean almacenActivo) {}
}
