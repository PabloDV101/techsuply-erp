package com.techsupply.erp_backend.compras.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.*;
import com.techsupply.erp_backend.compras.application.dto.*;
import com.techsupply.erp_backend.compras.domain.model.*;
import com.techsupply.erp_backend.compras.infrastructure.persistence.OrdenCompraRepository;
import com.techsupply.erp_backend.maestros.domain.model.*;
import com.techsupply.erp_backend.maestros.application.CatalogoParaCompras;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrdenCompraService {
    private final OrdenCompraRepository orders;
    private final EntityManager em;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate namedJdbc;
    private final CatalogoParaCompras catalog;
    public OrdenCompraService(OrdenCompraRepository orders, EntityManager em, AuthenticatedUserProvider users,
            AuditTrail audit, JdbcTemplate jdbc, NamedParameterJdbcTemplate namedJdbc, CatalogoParaCompras catalog) {
        this.orders = orders; this.em = em; this.users = users; this.audit = audit; this.jdbc = jdbc; this.namedJdbc = namedJdbc;
        this.catalog = catalog;
    }

    @Transactional
    @PreAuthorize("hasAuthority('COMPRAS_ORDENES_ADMIN')")
    public OrdenCompraResponse crear(CrearOrdenRequest request) {
        Proveedor supplier = catalog.proveedorActivo(request.proveedorId());
        Departamento department = catalog.departamento(request.departamentoId());
        List<Long> ids = request.detalles().stream().map(CrearOrdenRequest.Linea::productoId).toList();
        if (new HashSet<>(ids).size() != ids.size()) throw conflict("Producto duplicado en la orden");
        Map<Long, Producto> products = new HashMap<>();
        for (Long id : ids.stream().sorted().toList()) {
            products.put(id, catalog.productoActivo(id));
        }
        long sequence = Objects.requireNonNull(jdbc.queryForObject("select nextval('orden_compra_folio_seq')", Long.class));
        if (sequence > 999999999999L) throw conflict("Secuencia de folios agotada");
        String folio = "OC-" + String.format(Locale.ROOT, "%012d", sequence);
        OrdenCompra order = new OrdenCompra(folio, supplier, department, actor(), Instant.now(), request.observaciones());
        try {
            for (CrearOrdenRequest.Linea line : request.detalles()) order.agregarDetalle(new DetalleOrdenCompra(
                products.get(line.productoId()), line.cantidad(), line.precioUnitario(), line.descuentoPct(), line.tasaImpuesto()));
        } catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Líneas o importes inválidos"); }
        order = orders.saveAndFlush(order);
        audit.change(AuditAction.PURCHASE_ORDER_CREATED, order.getId());
        return response(order, Map.of());
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('COMPRAS_ORDENES_LEER')")
    public OrdenCompraResponse obtener(Long id) {
        OrdenCompra order = orders.findById(id).orElseThrow(() -> missing("Orden"));
        return response(order, accepted(List.of(id)));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('COMPRAS_ORDENES_LEER')")
    public PageResponse<OrdenCompraResponse> listar(int page, int size, String sort, String direction, Long proveedorId, String estado) {
        if (proveedorId != null && proveedorId < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Proveedor inválido");
        EstadoOrdenCompra status = null;
        if (estado != null) try { status = EstadoOrdenCompra.valueOf(estado); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido"); }
        EstadoOrdenCompra selected = status;
        Specification<OrdenCompra> spec = (root, query, cb) -> {
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (proveedorId != null) predicates.add(cb.equal(root.get("proveedor").get("id"), proveedorId));
            if (selected != null) predicates.add(cb.equal(root.get("estado"), selected));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var source = orders.findAll(spec, PageRequests.of(page, size, sort, direction,
            Map.of("id", "id", "folio", "folio", "fecha", "fecha"), "id"));
        List<Long> ids = source.getContent().stream().map(OrdenCompra::getId).toList();
        if (ids.isEmpty()) return new PageResponse<>(List.of(), source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages());
        Map<Long, OrdenCompra> loaded = orders.conDetalles(ids).stream().collect(Collectors.toMap(OrdenCompra::getId, x -> x));
        Map<Long, BigDecimal> accepted = accepted(ids);
        return PageResponse.from(source, order -> response(loaded.get(order.getId()), accepted));
    }

    @Transactional @PreAuthorize("hasAuthority('COMPRAS_ORDENES_ADMIN')")
    public OrdenCompraResponse enviarAprobacion(Long id) { return transition(id, AuditAction.PURCHASE_ORDER_SUBMITTED, OrdenCompra::enviarAprobacion); }
    @Transactional @PreAuthorize("hasAuthority('COMPRAS_ORDENES_APROBAR')")
    public OrdenCompraResponse aprobar(Long id) { return transition(id, AuditAction.PURCHASE_ORDER_APPROVED, o -> o.aprobar(actor(), Instant.now())); }
    @Transactional @PreAuthorize("hasAuthority('COMPRAS_ORDENES_ADMIN')")
    public OrdenCompraResponse enviar(Long id) { return transition(id, AuditAction.PURCHASE_ORDER_SENT, OrdenCompra::registrarEnvio); }

    private OrdenCompraResponse transition(Long id, AuditAction action, java.util.function.Consumer<OrdenCompra> change) {
        OrdenCompra order = orders.bloquearPorId(id).orElseThrow(() -> missing("Orden"));
        try { change.accept(order); }
        catch (IllegalArgumentException | IllegalStateException ex) { throw conflict("Transición de orden no permitida"); }
        orders.saveAndFlush(order);
        audit.change(action, id, Set.of(AuditField.ESTADO), null, null);
        return response(order, accepted(List.of(id)));
    }
    private Usuario actor() {
        Long id = users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida")).usuarioId();
        Usuario user = em.find(Usuario.class, id);
        if (user == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida");
        return user;
    }
    private Map<Long, BigDecimal> accepted(List<Long> ids) {
        Map<Long, BigDecimal> result = new HashMap<>();
        namedJdbc.query("select d.id, coalesce(sum(r.cantidad_aceptada),0) as cantidad from detalles_orden_compra d "
            + "left join (detalles_recepcion r join recepciones_compra h on h.id=r.recepcion_id and h.estado='CONFIRMADA') "
            + "on r.detalle_orden_compra_id=d.id where d.orden_compra_id in (:ids) group by d.id", Map.of("ids", ids),
            rs -> { result.put(rs.getLong("id"), rs.getBigDecimal("cantidad")); });
        return result;
    }
    private static OrdenCompraResponse response(OrdenCompra order, Map<Long, BigDecimal> accepted) {
        var lines = order.getDetalles().stream().map(line -> {
            BigDecimal quantity = accepted.getOrDefault(line.getId(), BigDecimal.ZERO);
            return new OrdenCompraResponse.Linea(line.getId(), line.getProducto().getId(), line.getCantidad(), line.getPrecioUnitario(),
                line.getDescuentoPct(), line.getTasaImpuesto(), quantity, CalculoOrden.pendiente(line.getCantidad(), quantity));
        }).toList();
        return new OrdenCompraResponse(order.getId(), order.getFolio(), order.getProveedor().getId(), order.getDepartamento().getId(),
            order.getCreadaPor().getId(), order.getAprobadaPor() == null ? null : order.getAprobadaPor().getId(), order.getAprobadaEn(),
            order.getMotivoRechazo(), order.getFecha(), order.getEstado(), order.getSubtotal(), order.getImpuestos(), order.getTotal(), order.getObservaciones(), lines);
    }
    private static ResponseStatusException missing(String resource) { return new ResponseStatusException(HttpStatus.NOT_FOUND, resource + " no encontrado"); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
