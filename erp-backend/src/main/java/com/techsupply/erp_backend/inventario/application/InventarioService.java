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
import java.util.Objects;
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

    /** Reserve an order line; the caller must confirm its parent in this same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_CONFIRMAR')")
    public void reservarLineaPedido(long detalleId) {
        var sources = db.query("""
            select d.producto_id,p.almacen_id,d.cantidad,d.cantidad_reservada,d.cantidad_despachada,
                   p.estado,w.activo as almacen_activo,pr.activo as producto_activo
            from detalles_pedido d join pedidos_venta p on p.id=d.pedido_id
            join almacenes w on w.id=p.almacen_id join productos pr on pr.id=d.producto_id
            where d.id=? for update of p,d for share of w,pr
            """, (rs,row)->new FuenteReserva(rs.getLong("producto_id"),rs.getLong("almacen_id"),
                rs.getBigDecimal("cantidad"),rs.getBigDecimal("cantidad_reservada"),rs.getBigDecimal("cantidad_despachada"),
                rs.getString("estado"),rs.getBoolean("almacen_activo"),rs.getBoolean("producto_activo")), detalleId);
        if (sources.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Detalle de pedido no encontrado");
        FuenteReserva source=sources.getFirst();
        if (!source.estado().equals("BORRADOR") || !source.almacenActivo() || !source.productoActivo()
                || source.cantidad().signum()<=0 || source.reservada().signum()!=0 || source.despachada().signum()!=0)
            throw reservaConflicto();
        var stocks=db.query("select cantidad_fisica,cantidad_reservada,version from existencias where producto_id=? and almacen_id=? for update",
            (rs,row)->new ReservaStock(rs.getBigDecimal(1),rs.getBigDecimal(2),rs.getInt(3)),source.producto(),source.almacen());
        if (stocks.isEmpty()) throw reservaConflicto();
        ReservaStock stock=stocks.getFirst();
        if (stock.version()==Integer.MAX_VALUE || stock.fisica().subtract(stock.reservada()).compareTo(source.cantidad())<0)
            throw reservaConflicto();
        db.update("update existencias set cantidad_reservada=cantidad_reservada+?,version=version+1 where producto_id=? and almacen_id=?",
            source.cantidad(),source.producto(),source.almacen());
        db.update("update detalles_pedido set cantidad_reservada=cantidad where id=?",detalleId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                Boolean valid=db.queryForObject("""
                    select exists(select 1 from detalles_pedido d join pedidos_venta p on p.id=d.pedido_id
                    where d.id=? and p.estado='CONFIRMADO' and d.cantidad_reservada=d.cantidad and d.cantidad_despachada=0)
                    """,Boolean.class,detalleId);
                if (!Boolean.TRUE.equals(valid)) throw new IllegalStateException("El pedido debe confirmarse en la misma transacción");
            }
        });
    }
    private static ResponseStatusException reservaConflicto() { return new ResponseStatusException(HttpStatus.CONFLICT,"Pedido o disponibilidad incompatible"); }
    private record FuenteReserva(long producto,long almacen,BigDecimal cantidad,BigDecimal reservada,BigDecimal despachada,
            String estado,boolean almacenActivo,boolean productoActivo) {}
    private record ReservaStock(BigDecimal fisica,BigDecimal reservada,int version) {}

    /** Release the outstanding reservation; caller must cancel the order in this transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_CANCELAR')")
    public void liberarReservaPedido(long detalleId) {
        var sources=db.query("""
            select d.producto_id,p.almacen_id,d.cantidad,d.cantidad_reservada,d.cantidad_despachada,p.estado
            from detalles_pedido d join pedidos_venta p on p.id=d.pedido_id
            where d.id=? for update of p,d
            """,(rs,row)->new FuenteLiberacion(rs.getLong("producto_id"),rs.getLong("almacen_id"),rs.getBigDecimal("cantidad"),
                rs.getBigDecimal("cantidad_reservada"),rs.getBigDecimal("cantidad_despachada"),rs.getString("estado")),detalleId);
        if (sources.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Detalle de pedido no encontrado");
        FuenteLiberacion f=sources.getFirst();
        if (!(f.estado().equals("CONFIRMADO") || f.estado().equals("PARCIALMENTE_DESPACHADO")) || f.reservada().signum()<=0
                || f.reservada().compareTo(f.cantidad().subtract(f.despachada()))!=0) throw liberacionConflicto();
        var stocks=db.query("select cantidad_fisica,cantidad_reservada,version from existencias where producto_id=? and almacen_id=? for update",
            (rs,row)->new ReservaStock(rs.getBigDecimal(1),rs.getBigDecimal(2),rs.getInt(3)),f.producto(),f.almacen());
        if (stocks.isEmpty() || stocks.getFirst().version()==Integer.MAX_VALUE || stocks.getFirst().reservada().compareTo(f.reservada())<0)
            throw liberacionConflicto();
        db.update("update existencias set cantidad_reservada=cantidad_reservada-?,version=version+1 where producto_id=? and almacen_id=?",
            f.reservada(),f.producto(),f.almacen());
        db.update("update detalles_pedido set cantidad_reservada=0 where id=?",detalleId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                Boolean valid=db.queryForObject("""
                    select exists(select 1 from detalles_pedido d join pedidos_venta p on p.id=d.pedido_id
                    where d.id=? and p.estado='CANCELADO' and d.cantidad_reservada=0 and d.cantidad_despachada=?)
                    """,Boolean.class,detalleId,f.despachada());
                if (!Boolean.TRUE.equals(valid)) throw new IllegalStateException("El pedido debe cancelarse en la misma transacción");
            }
        });
    }
    private static ResponseStatusException liberacionConflicto() { return new ResponseStatusException(HttpStatus.CONFLICT,"Reserva o pedido incompatible"); }
    private record FuenteLiberacion(long producto,long almacen,BigDecimal cantidad,BigDecimal reservada,BigDecimal despachada,String estado) {}

    /** Register one reserved sale exit; its dispatch must be confirmed in the same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_CONFIRMAR')")
    public void registrarSalidaDespacho(long detalleId) {
        Long actor=users.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autenticación requerida")).usuarioId();
        var sources=db.query("""
            select d.cantidad,d.costo_unitario,d.detalle_pedido_id,l.producto_id,l.cantidad as pedida,
                   l.cantidad_reservada,l.cantidad_despachada,s.almacen_id,s.estado as despacho_estado,
                   p.estado as pedido_estado,w.activo as almacen_activo,pr.activo as producto_activo
            from detalles_despacho d join despachos s on s.id=d.despacho_id
            join detalles_pedido l on l.id=d.detalle_pedido_id and l.pedido_id=s.pedido_id
            join pedidos_venta p on p.id=s.pedido_id and p.almacen_id=s.almacen_id
            join almacenes w on w.id=s.almacen_id join productos pr on pr.id=l.producto_id
            where d.id=? for update of p,s,d,l for share of w,pr
            """,(rs,row)->new FuenteDespacho(rs.getBigDecimal("cantidad"),rs.getBigDecimal("costo_unitario"),
                rs.getLong("detalle_pedido_id"),rs.getLong("producto_id"),rs.getBigDecimal("pedida"),
                rs.getBigDecimal("cantidad_reservada"),rs.getBigDecimal("cantidad_despachada"),rs.getLong("almacen_id"),
                rs.getString("despacho_estado"),rs.getString("pedido_estado"),rs.getBoolean("almacen_activo"),rs.getBoolean("producto_activo")),detalleId);
        if (sources.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Detalle de despacho no encontrado");
        FuenteDespacho f=sources.getFirst();
        if (!f.despachoEstado().equals("BORRADOR") || !(f.pedidoEstado().equals("CONFIRMADO") || f.pedidoEstado().equals("PARCIALMENTE_DESPACHADO"))
                || !f.almacenActivo() || !f.productoActivo() || f.costo()!=null || f.cantidad().signum()<=0
                || f.cantidad().compareTo(f.reservada())>0 || f.cantidad().compareTo(f.pedida().subtract(f.despachada()))>0)
            throw despachoConflicto();
        if (Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from movimientos_inventario where detalle_despacho_id=?)",Boolean.class,detalleId)))
            throw despachoConflicto();
        var stock=db.query("select cantidad_fisica,cantidad_reservada,costo_promedio,version from existencias where producto_id=? and almacen_id=? for update",
            (rs,row)->new StockActual(rs.getBigDecimal(1),rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getInt(4)),f.producto(),f.almacen());
        if (stock.isEmpty()) throw despachoConflicto();
        StockActual current=stock.getFirst();
        if (current.version()==Integer.MAX_VALUE || current.fisica().compareTo(f.cantidad())<0 || current.reservada().compareTo(f.cantidad())<0)
            throw despachoConflicto();
        db.update("update detalles_despacho set costo_unitario=? where id=?",current.promedio(),detalleId);
        db.update("update existencias set cantidad_fisica=cantidad_fisica-?,cantidad_reservada=cantidad_reservada-?,version=version+1 where producto_id=? and almacen_id=?",
            f.cantidad(),f.cantidad(),f.producto(),f.almacen());
        db.update("update detalles_pedido set cantidad_reservada=cantidad_reservada-?,cantidad_despachada=cantidad_despachada+? where id=?",
            f.cantidad(),f.cantidad(),f.detallePedido());
        db.update("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_despacho_id,clave_operacion) values (?,?,'SALIDA','DESPACHO_VENTA',?,?,?,?,?)",
            f.producto(),f.almacen(),f.cantidad(),current.promedio(),actor,detalleId,"DESPACHO:"+detalleId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                Boolean valid=db.queryForObject("""
                    select exists(select 1 from detalles_despacho d join despachos s on s.id=d.despacho_id
                    join pedidos_venta p on p.id=s.pedido_id join movimientos_inventario m on m.detalle_despacho_id=d.id
                    where d.id=? and s.estado='CONFIRMADO' and p.estado in ('PARCIALMENTE_DESPACHADO','DESPACHADO')
                    and d.costo_unitario=m.costo_unitario and d.cantidad=m.cantidad)
                    """,Boolean.class,detalleId);
                if (!Boolean.TRUE.equals(valid)) throw new IllegalStateException("El despacho y el pedido deben actualizarse en la misma transacción");
            }
        });
    }
    private static ResponseStatusException despachoConflicto() { return new ResponseStatusException(HttpStatus.CONFLICT,"Despacho o existencia incompatible"); }
    private record FuenteDespacho(BigDecimal cantidad,BigDecimal costo,long detallePedido,long producto,BigDecimal pedida,
            BigDecimal reservada,BigDecimal despachada,long almacen,String despachoEstado,String pedidoEstado,boolean almacenActivo,boolean productoActivo) {}

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
    /** The caller must mark the approved adjustment applied in this same transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_APLICAR')")
    public BigDecimal registrarAjuste(long detalleId) {
        Long actor = users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida")).usuarioId();
        var rows = db.query("""
            select a.id as ajuste_id,a.estado,a.almacen_id,d.producto_id,d.tipo,d.cantidad,d.costo_unitario,d.detalle_conteo_id,
                   p.activo as producto_activo,w.activo as almacen_activo
            from detalles_ajuste_inventario d join ajustes_inventario a on a.id=d.ajuste_inventario_id
            join productos p on p.id=d.producto_id join almacenes w on w.id=a.almacen_id
            where d.id=? for update of a,d for share of p,w
            """, (rs, row) -> new FuenteAjuste(rs.getLong("ajuste_id"),rs.getString("estado"),rs.getLong("almacen_id"),
                rs.getLong("producto_id"),rs.getString("tipo"),rs.getBigDecimal("cantidad"),rs.getBigDecimal("costo_unitario"),
                rs.getObject("detalle_conteo_id",Long.class),rs.getBoolean("producto_activo"),rs.getBoolean("almacen_activo")), detalleId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Detalle de ajuste no encontrado");
        FuenteAjuste f = rows.getFirst();
        if (!f.estado().equals("APROBADO") || !f.productoActivo() || !f.almacenActivo()) throw ajusteConflicto();
        if (Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from movimientos_inventario where detalle_ajuste_id=?)", Boolean.class, detalleId))) throw ajusteConflicto();
        ReferenciaConteo reference = null;
        if (f.detalleConteoId() != null) {
            var references = db.query("""
                select c.estado,c.almacen_id,d.producto_id,d.diferencia,d.existencia_previa,d.version_existencia
                from detalles_conteo d join conteos_inventario c on c.id=d.conteo_id where d.id=? for update of d
                """, (rs, row) -> new ReferenciaConteo(rs.getString("estado"),rs.getLong("almacen_id"),rs.getLong("producto_id"),
                    rs.getBigDecimal("diferencia"),rs.getBoolean("existencia_previa"),rs.getObject("version_existencia",Integer.class)), f.detalleConteoId());
            if (references.isEmpty()) throw ajusteConflicto();
            reference = references.getFirst();
            boolean sign = reference.diferencia().signum() > 0 && f.tipo().equals("POSITIVO")
                || reference.diferencia().signum() < 0 && f.tipo().equals("NEGATIVO");
            if (!reference.estado().equals("CERRADO") || reference.almacenId()!=f.almacenId() || reference.productoId()!=f.productoId()
                || !sign || reference.diferencia().abs().compareTo(f.cantidad())!=0) throw ajusteConflicto();
            if (Boolean.TRUE.equals(db.queryForObject("""
                select exists(select 1 from detalles_ajuste_inventario d join ajustes_inventario a on a.id=d.ajuste_inventario_id
                where d.detalle_conteo_id=? and a.estado='APLICADO')
                """, Boolean.class, f.detalleConteoId()))) throw ajusteConflicto();
        }
        // A successful insert reserves an absent product/warehouse pair; ON CONFLICT waits for concurrent creators.
        int inserted = db.update("insert into existencias(producto_id,almacen_id) values (?,?) on conflict (producto_id,almacen_id) do nothing", f.productoId(), f.almacenId());
        var stock = db.queryForObject("select cantidad_fisica,cantidad_reservada,costo_promedio,version from existencias where producto_id=? and almacen_id=? for update",
            (rs,row) -> new StockActual(rs.getBigDecimal("cantidad_fisica"),rs.getBigDecimal("cantidad_reservada"),rs.getBigDecimal("costo_promedio"),rs.getInt("version")), f.productoId(),f.almacenId());
        if (stock == null || stock.version()==Integer.MAX_VALUE) throw ajusteConflicto();
        if (reference != null && (reference.existenciaPrevia() ? inserted != 0 || !Objects.equals(reference.version(),stock.version()) : inserted != 1)) throw ajusteConflicto();
        BigDecimal newQuantity, newCost, movementCost;
        if (f.tipo().equals("POSITIVO") && f.costo() != null) {
            try {
                ValoracionInventario.Resultado result = ValoracionInventario.entrada(stock.fisica(),stock.promedio(),f.cantidad(),f.costo());
                newQuantity=result.cantidadFisica(); newCost=result.costoPromedio(); movementCost=f.costo();
            } catch (IllegalArgumentException ex) { throw ajusteConflicto(); }
        } else if (f.tipo().equals("NEGATIVO") && f.cantidad().signum()>0) {
            newQuantity=stock.fisica().subtract(f.cantidad());
            if (newQuantity.compareTo(stock.reservada())<0) throw ajusteConflicto();
            newCost=stock.promedio(); movementCost=stock.promedio();
        } else throw ajusteConflicto();
        db.update("update existencias set cantidad_fisica=?,costo_promedio=?,version=version+1 where producto_id=? and almacen_id=?",
            newQuantity,newCost,f.productoId(),f.almacenId());
        db.update("insert into movimientos_inventario(producto_id,almacen_id,tipo,motivo,cantidad,costo_unitario,usuario_id,detalle_ajuste_id,clave_operacion) values (?,?,?,'AJUSTE',?,?,?,?,?)",
            f.productoId(),f.almacenId(),f.tipo().equals("POSITIVO") ? "ENTRADA" : "SALIDA",f.cantidad(),movementCost,actor,detalleId,"AJUSTE:"+detalleId);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void beforeCommit(boolean readOnly) {
                Boolean valid=db.queryForObject("""
                    select exists(select 1 from detalles_ajuste_inventario d join ajustes_inventario a on a.id=d.ajuste_inventario_id
                    join movimientos_inventario m on m.detalle_ajuste_id=d.id
                    where d.id=? and a.estado='APLICADO' and d.costo_unitario=m.costo_unitario)
                    """,Boolean.class,detalleId);
                if (!Boolean.TRUE.equals(valid)) throw new IllegalStateException("El ajuste y su costo deben confirmarse en la misma transacción");
            }
        });
        return movementCost;
    }
    private static ResponseStatusException ajusteConflicto() { return new ResponseStatusException(HttpStatus.CONFLICT,"Ajuste o existencia incompatible"); }
    private record FuenteAjuste(long ajusteId,String estado,long almacenId,long productoId,String tipo,BigDecimal cantidad,BigDecimal costo,Long detalleConteoId,boolean productoActivo,boolean almacenActivo) {}
    private record ReferenciaConteo(String estado,long almacenId,long productoId,BigDecimal diferencia,boolean existenciaPrevia,Integer version) {}
    private record StockActual(BigDecimal fisica,BigDecimal reservada,BigDecimal promedio,int version) {}
    private record Fuente(long productoId, long almacenId, BigDecimal recibida, BigDecimal aceptada,
        BigDecimal rechazada, BigDecimal costo, String estado, String ordenEstado, boolean almacenActivo) {}
}
