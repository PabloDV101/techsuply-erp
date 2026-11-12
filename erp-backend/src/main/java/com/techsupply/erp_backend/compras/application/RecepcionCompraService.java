package com.techsupply.erp_backend.compras.application;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.audit.application.AuditField;
import com.techsupply.erp_backend.audit.application.AuditState;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.compras.application.dto.CrearRecepcionRequest;
import com.techsupply.erp_backend.compras.application.dto.RecepcionCompraResponse;
import com.techsupply.erp_backend.compras.domain.model.*;
import com.techsupply.erp_backend.compras.infrastructure.persistence.OrdenCompraRepository;
import com.techsupply.erp_backend.compras.infrastructure.persistence.RecepcionCompraRepository;
import com.techsupply.erp_backend.maestros.application.CatalogoParaCompras;
import com.techsupply.erp_backend.inventario.application.InventarioService;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RecepcionCompraService {
    private final RecepcionCompraRepository receipts;
    private final OrdenCompraRepository orders;
    private final CatalogoParaCompras catalog;
    private final EntityManager em;
    private final JdbcTemplate jdbc;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    private final InventarioService inventory;
    public RecepcionCompraService(RecepcionCompraRepository receipts, OrdenCompraRepository orders, CatalogoParaCompras catalog,
            EntityManager em, JdbcTemplate jdbc, AuthenticatedUserProvider users, AuditTrail audit, InventarioService inventory) {
        this.receipts = receipts; this.orders = orders; this.catalog = catalog; this.em = em;
        this.jdbc = jdbc; this.users = users; this.audit = audit;
        this.inventory = inventory;
    }
    public record Resultado(RecepcionCompraResponse recepcion, boolean creada) {}

    @Transactional @PreAuthorize("hasAuthority('COMPRAS_RECEPCIONES_REGISTRAR')")
    public Resultado crear(CrearRecepcionRequest request) {
        String key = request.claveIdempotencia().trim();
        if (key.isEmpty()) throw bad("Clave inválida");
        List<Long> ids = request.detalles().stream().map(CrearRecepcionRequest.Linea::detalleOrdenCompraId).toList();
        if (new HashSet<>(ids).size() != ids.size()) throw conflict("Detalle duplicado");
        // Transaction-scoped key lock serializes all concurrent attempts, including different orders.
        jdbc.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(?, 917463))", Integer.class, key);
        var existing = receipts.findByClaveIdempotencia(key);
        if (existing.isPresent()) {
            if (!equivalente(existing.get(), request)) throw conflict("La clave ya corresponde a otro contenido");
            return new Resultado(response(existing.get()), false);
        }
        OrdenCompra order = orders.bloquearPorId(request.ordenCompraId()).orElseThrow(() -> missing("Orden"));
        if (order.getEstado() != EstadoOrdenCompra.ENVIADA && order.getEstado() != EstadoOrdenCompra.PARCIALMENTE_RECIBIDA)
            throw conflict("La orden no admite recepciones");
        var warehouse = catalog.almacenActivo(request.almacenId());
        Map<Long, DetalleOrdenCompra> orderLines = order.getDetalles().stream().collect(Collectors.toMap(DetalleOrdenCompra::getId, d -> d));
        for (Long id : ids) if (!orderLines.containsKey(id)) throw missing("Detalle de orden");
        long seq = Objects.requireNonNull(jdbc.queryForObject("select nextval('recepcion_compra_folio_seq')", Long.class));
        if (seq > 999999999999L) throw conflict("Secuencia de folios agotada");
        Long actorId = users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida")).usuarioId();
        Usuario actor = em.find(Usuario.class, actorId);
        if (actor == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida");
        RecepcionCompra receipt = new RecepcionCompra("RC-" + String.format(Locale.ROOT, "%012d", seq), order, warehouse,
            actor, key, Instant.now(), optional(request.referenciaExterna()));
        try {
            for (CrearRecepcionRequest.Linea line : request.detalles()) receipt.agregarDetalle(new DetalleRecepcion(
                orderLines.get(line.detalleOrdenCompraId()), line.cantidadRecibida(), line.cantidadAceptada(),
                line.cantidadRechazada(), line.precioUnitarioRecibido(), optional(line.motivoRechazo())));
        } catch (IllegalArgumentException ex) { throw bad("Cantidades inválidas"); }
        receipt = receipts.saveAndFlush(receipt);
        audit.change(AuditAction.PURCHASE_RECEIPT_CREATED, receipt.getId());
        return new Resultado(response(receipt), true);
    }
    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('COMPRAS_RECEPCIONES_LEER')")
    public RecepcionCompraResponse obtener(Long id) { return response(receipts.findById(id).orElseThrow(() -> missing("Recepción"))); }

    @Transactional @PreAuthorize("hasAuthority('COMPRAS_RECEPCIONES_CONFIRMAR')")
    public RecepcionCompraResponse confirmar(Long id) {
        List<Long> orderIds = jdbc.query("select orden_compra_id from recepciones_compra where id=?", (rs, n) -> rs.getLong(1), id);
        if (orderIds.isEmpty()) throw missing("Recepción");
        OrdenCompra order = orders.bloquearPorId(orderIds.getFirst()).orElseThrow(() -> missing("Orden"));
        RecepcionCompra receipt = receipts.bloquearPorId(id).orElseThrow(() -> missing("Recepción"));
        if (!receipt.getOrden().getId().equals(order.getId())) throw conflict("Orden de recepción inconsistente");
        if (receipt.getEstado() == EstadoRecepcionCompra.CONFIRMADA) return response(receipt);
        if (receipt.getEstado() != EstadoRecepcionCompra.PENDIENTE ||
                (order.getEstado() != EstadoOrdenCompra.ENVIADA && order.getEstado() != EstadoOrdenCompra.PARCIALMENTE_RECIBIDA))
            throw conflict("Estado no permite confirmar recepción");
        catalog.almacenActivo(receipt.getAlmacen().getId());
        jdbc.query("select id from detalles_orden_compra where orden_compra_id=? order by id for update", rs -> {}, order.getId());
        if (receipt.getDetalles().isEmpty() || order.getDetalles().isEmpty()) throw conflict("Documento sin líneas");
        Map<Long, BigDecimal> accepted = new HashMap<>();
        jdbc.query("select d.detalle_orden_compra_id, sum(d.cantidad_aceptada) as cantidad from detalles_recepcion d "
            + "join recepciones_compra r on r.id=d.recepcion_id where r.orden_compra_id=? and r.estado='CONFIRMADA' group by d.detalle_orden_compra_id",
            rs -> { accepted.put(rs.getLong(1), rs.getBigDecimal(2)); }, order.getId());
        Map<Long, DetalleOrdenCompra> lines = order.getDetalles().stream().collect(Collectors.toMap(DetalleOrdenCompra::getId, d -> d));
        for (DetalleRecepcion detail : receipt.getDetalles()) {
            DetalleOrdenCompra line = lines.get(detail.getDetalleOrden().getId());
            if (line == null || detail.getCantidadAceptada().add(detail.getCantidadRechazada()).compareTo(detail.getCantidadRecibida()) != 0)
                throw conflict("Cantidades o líneas de recepción inválidas");
            BigDecimal cumulative = accepted.getOrDefault(line.getId(), BigDecimal.ZERO).add(detail.getCantidadAceptada());
            if (cumulative.compareTo(line.getCantidad()) > 0) throw conflict("Cantidad aceptada supera el saldo pendiente");
            accepted.put(line.getId(), cumulative);
        }
        for (DetalleOrdenCompra line : lines.values()) {
            if (accepted.getOrDefault(line.getId(), BigDecimal.ZERO).compareTo(line.getCantidad()) > 0)
                throw conflict("Saldo de orden inconsistente");
        }
        boolean complete = lines.values().stream().allMatch(l -> accepted.getOrDefault(l.getId(), BigDecimal.ZERO).compareTo(l.getCantidad()) == 0);
        boolean anyAccepted = accepted.values().stream().anyMatch(v -> v.signum() > 0);
        for (DetalleRecepcion detail : receipt.getDetalles().stream()
                .filter(d -> d.getCantidadAceptada().signum() > 0)
                .sorted(Comparator.comparing(d -> d.getDetalleOrden().getProducto().getId())).toList()) {
            inventory.registrarEntradaRecepcion(detail.getId());
        }
        EstadoOrdenCompra previous = order.getEstado();
        order.registrarRecepcion(complete, anyAccepted);
        receipt.confirmar(Instant.now());
        receipts.saveAndFlush(receipt);
        orders.saveAndFlush(order);
        audit.change(AuditAction.PURCHASE_RECEIPT_CONFIRMED, id, Set.of(AuditField.ESTADO), AuditState.PENDIENTE, AuditState.CONFIRMADA);
        if (order.getEstado() != previous) audit.change(AuditAction.PURCHASE_ORDER_RECEIPT_STATUS_CHANGED, order.getId(), Set.of(AuditField.ESTADO),
            AuditState.valueOf(previous.name()), AuditState.valueOf(order.getEstado().name()));
        return response(receipt);
    }

    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('COMPRAS_RECEPCIONES_LEER')")
    public PageResponse<RecepcionCompraResponse> listar(int page, int size, String sort, String direction, Long ordenCompraId) {
        if (ordenCompraId != null && ordenCompraId < 1) throw bad("Orden inválida");
        Specification<RecepcionCompra> spec = (root, query, cb) -> ordenCompraId == null ? cb.conjunction() : cb.equal(root.get("orden").get("id"), ordenCompraId);
        var source = receipts.findAll(spec, PageRequests.of(page, size, sort, direction, Map.of("id", "id", "folio", "folio", "fecha", "fecha"), "id"));
        List<Long> ids = source.getContent().stream().map(RecepcionCompra::getId).toList();
        if (ids.isEmpty()) return new PageResponse<>(List.of(), source.getNumber(), source.getSize(), source.getTotalElements(), source.getTotalPages());
        var loaded = receipts.conDetalles(ids).stream().collect(Collectors.toMap(RecepcionCompra::getId, r -> r));
        return PageResponse.from(source, r -> response(loaded.get(r.getId())));
    }
    private static boolean equivalente(RecepcionCompra receipt, CrearRecepcionRequest request) {
        if (!receipt.getOrden().getId().equals(request.ordenCompraId()) || !receipt.getAlmacen().getId().equals(request.almacenId())
                || !Objects.equals(receipt.getReferenciaExterna(), optional(request.referenciaExterna())) || receipt.getDetalles().size() != request.detalles().size()) return false;
        var previous = receipt.getDetalles().stream().collect(Collectors.toMap(d -> d.getDetalleOrden().getId(), d -> d));
        return request.detalles().stream().allMatch(line -> {
            DetalleRecepcion old = previous.get(line.detalleOrdenCompraId());
            return old != null && old.getCantidadRecibida().compareTo(line.cantidadRecibida()) == 0
                && old.getCantidadAceptada().compareTo(line.cantidadAceptada()) == 0 && old.getCantidadRechazada().compareTo(line.cantidadRechazada()) == 0
                && old.getPrecioUnitarioRecibido().compareTo(line.precioUnitarioRecibido()) == 0 && Objects.equals(old.getMotivoRechazo(), optional(line.motivoRechazo()));
        });
    }
    private static String optional(String value) { if (value == null) return null; String result = value.trim(); return result.isEmpty() ? null : result; }
    private static RecepcionCompraResponse response(RecepcionCompra r) {
        var lines = r.getDetalles().stream().sorted(Comparator.comparing(d -> d.getDetalleOrden().getId())).map(d -> new RecepcionCompraResponse.Linea(
            d.getId(), d.getDetalleOrden().getId(), d.getCantidadRecibida(), d.getCantidadAceptada(), d.getCantidadRechazada(), d.getPrecioUnitarioRecibido(), d.getMotivoRechazo())).toList();
        return new RecepcionCompraResponse(r.getId(), r.getFolio(), r.getOrden().getId(), r.getAlmacen().getId(), r.getRegistradaPor().getId(),
            r.getClaveIdempotencia(), r.getFecha(), r.getEstado(), r.getReferenciaExterna(), r.getConfirmadaEn(), lines);
    }
    private static ResponseStatusException bad(String text) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, text); }
    private static ResponseStatusException missing(String text) { return new ResponseStatusException(HttpStatus.NOT_FOUND, text + " no encontrado"); }
    private static ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT, text); }
}
