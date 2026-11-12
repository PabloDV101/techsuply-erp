package com.techsupply.erp_backend.ventas.application;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import com.techsupply.erp_backend.ventas.application.dto.CotizacionCreateRequest;
import com.techsupply.erp_backend.ventas.application.dto.CotizacionResponse;
import com.techsupply.erp_backend.ventas.domain.model.*;
import com.techsupply.erp_backend.ventas.infrastructure.persistence.CotizacionRepository;
import com.techsupply.erp_backend.ventas.infrastructure.persistence.DetalleCotizacionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated
public class CotizacionService {
    private final CotizacionRepository cotizaciones;
    private final DetalleCotizacionRepository detalles;
    private final AuditTrail audit;
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate namedDb;
    private final AuthenticatedUserProvider users;
    private final Clock clock;
    public CotizacionService(CotizacionRepository cotizaciones, DetalleCotizacionRepository detalles, AuditTrail audit,
            JdbcTemplate db, NamedParameterJdbcTemplate namedDb, AuthenticatedUserProvider users, Clock clock) {
        this.cotizaciones = cotizaciones; this.detalles = detalles; this.audit = audit;
        this.db = db; this.namedDb = namedDb; this.users = users; this.clock = clock;
    }
    public record Resultado(CotizacionResponse cotizacion, boolean creada) {}

    @Transactional
    @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_REGISTRAR')")
    public Resultado crear(@NotNull @Valid CotizacionCreateRequest request) {
        String key = request.claveIdempotencia().trim();
        if (key.isEmpty()) throw bad("Clave inválida");
        List<Long> products = request.detalles().stream().map(CotizacionCreateRequest.Linea::productoId).toList();
        if (new HashSet<>(products).size() != products.size()) throw conflict("Producto duplicado");
        Long actor = users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida")).usuarioId();
        db.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(?, 6182047))", Integer.class, key);
        var existing = cotizaciones.findByClaveIdempotencia(key);
        if (existing.isPresent()) {
            Cotizacion prior = existing.get();
            var lines = detalles.findByCotizacionIdOrderByIdAsc(prior.getId());
            if (!equivalente(prior, lines, request, actor)) throw conflict("La clave corresponde a otro contenido");
            return new Resultado(response(prior, lines), false);
        }
        Instant instante = clock.instant();
        if (request.vigenciaHasta().isBefore(VigenciaCotizacion.fechaNegocio(instante))) throw conflict("Vigencia anterior a la fecha de negocio");
        validarClienteActivo(request.clienteId());
        validarProductosActivos(products);
        List<ValoresVenta.Linea> values;
        CalculoCotizacion.Totales totals;
        try {
            values = request.detalles().stream().map(CotizacionService::valores).toList();
            totals = CalculoCotizacion.calcular(values);
        } catch (IllegalArgumentException ex) { throw bad("Importes o líneas inválidos"); }
        long seq = Objects.requireNonNull(db.queryForObject("select nextval('cotizacion_folio_seq')", Long.class));
        if (seq > 999999999999L) throw conflict("Secuencia de folios agotada");
        Cotizacion quote = cotizaciones.save(new Cotizacion("COT-" + String.format(Locale.ROOT, "%012d", seq),
            request.clienteId(), actor, instante, request.vigenciaHasta(), key, totals));
        for (int i = 0; i < products.size(); i++) detalles.save(new DetalleCotizacion(quote.getId(), products.get(i), values.get(i)));
        cotizaciones.flush();
        audit.change(AuditAction.SALES_QUOTE_CREATED, quote.getId());
        return new Resultado(response(quote, detalles.findByCotizacionIdOrderByIdAsc(quote.getId())), true);
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_LEER')")
    public CotizacionResponse obtener(Long id) {
        validarId(id);
        Cotizacion quote = cotizaciones.findById(id).orElseThrow(CotizacionService::missing);
        return response(quote, detalles.findByCotizacionIdOrderByIdAsc(id));
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_LEER')")
    public PageResponse<CotizacionResponse> listar(int page, int size, Long clienteId) {
        if (clienteId != null) validarId(clienteId);
        var paging = PageRequests.of(page, size, "id", "desc", Map.of("id", "id"));
        var source = clienteId == null ? cotizaciones.findAll(paging) : cotizaciones.findByClienteId(clienteId, paging);
        List<Long> ids = source.getContent().stream().map(Cotizacion::getId).toList();
        Map<Long, List<DetalleCotizacion>> byQuote = ids.isEmpty() ? Map.of() : detalles.findByCotizacionIdInOrderByCotizacionIdAscIdAsc(ids)
            .stream().collect(Collectors.groupingBy(DetalleCotizacion::getCotizacionId));
        return PageResponse.from(source, q -> response(q, byQuote.getOrDefault(q.getId(), List.of())));
    }
    @Transactional @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_ADMIN')")
    public CotizacionResponse emitir(Long id) { return transition(id, AuditAction.SALES_QUOTE_ISSUED, Cotizacion::emitir); }
    @Transactional @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_ADMIN')")
    public CotizacionResponse aceptar(Long id) { return transition(id, AuditAction.SALES_QUOTE_ACCEPTED, Cotizacion::aceptar); }
    private CotizacionResponse transition(Long id, AuditAction action, java.util.function.BiConsumer<Cotizacion, LocalDate> change) {
        validarId(id);
        Cotizacion quote = cotizaciones.bloquearPorId(id).orElseThrow(CotizacionService::missing);
        validarClienteActivo(quote.getClienteId());
        var lines = detalles.findByCotizacionIdOrderByIdAsc(id);
        if (lines.isEmpty()) throw conflict("Cotización sin líneas");
        try { change.accept(quote, fechaNegocio()); }
        catch (IllegalStateException ex) { throw conflict("Estado o vigencia incompatibles"); }
        audit.change(action, id);
        return response(quote, lines);
    }
    @Transactional @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_ADMIN')")
    public CotizacionResponse cancelar(Long id) {
        validarId(id);
        Cotizacion quote = cotizaciones.bloquearPorId(id).orElseThrow(CotizacionService::missing);
        try { quote.cancelar(); }
        catch (IllegalStateException ex) { throw conflict("Estado de cotización incompatible"); }
        audit.change(AuditAction.SALES_QUOTE_CANCELLED, id);
        return response(quote, detalles.findByCotizacionIdOrderByIdAsc(id));
    }
    private void validarClienteActivo(Long id) {
        List<Boolean> result = db.query("select activo from clientes where id=? for share", (rs, row) -> rs.getBoolean(1), id);
        if (result.isEmpty()) throw missing();
        if (!result.getFirst()) throw conflict("Cliente inactivo");
    }
    private void validarProductosActivos(List<Long> ids) {
        List<Map<String, Object>> rows = namedDb.queryForList("select id, activo from productos where id in (:ids) order by id for share", Map.of("ids", ids));
        if (rows.size() != ids.size()) throw missing();
        if (rows.stream().anyMatch(row -> !Boolean.TRUE.equals(row.get("activo")))) throw conflict("Producto inactivo");
    }
    private static boolean equivalente(Cotizacion quote, List<DetalleCotizacion> lines, CotizacionCreateRequest request, Long actor) {
        if (!quote.getVendedorUsuarioId().equals(actor) || !quote.getClienteId().equals(request.clienteId())
                || !quote.getVigenciaHasta().equals(request.vigenciaHasta()) || lines.size() != request.detalles().size()) return false;
        var byProduct = lines.stream().collect(Collectors.toMap(DetalleCotizacion::getProductoId, x -> x));
        return request.detalles().stream().allMatch(line -> {
            DetalleCotizacion old = byProduct.get(line.productoId());
            return old != null && old.getCantidad().compareTo(line.cantidad()) == 0
                && old.getPrecioUnitario().compareTo(line.precioUnitario()) == 0
                && old.getDescuentoPct().compareTo(line.descuentoPct()) == 0
                && old.getTasaImpuesto().compareTo(line.tasaImpuesto()) == 0;
        });
    }
    private static ValoresVenta.Linea valores(CotizacionCreateRequest.Linea l) {
        return new ValoresVenta.Linea(l.cantidad(), l.precioUnitario(), l.descuentoPct(), l.tasaImpuesto());
    }
    private LocalDate fechaNegocio() { return VigenciaCotizacion.fechaNegocio(clock.instant()); }
    private static void validarId(Long id) { if (id == null || id < 1) throw bad("Identificador inválido"); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Recurso no encontrado"); }
    private static CotizacionResponse response(Cotizacion quote, List<DetalleCotizacion> lines) {
        return new CotizacionResponse(quote.getId(), quote.getFolio(), quote.getClienteId(), quote.getVendedorUsuarioId(),
            quote.getFecha(), quote.getVigenciaHasta(), quote.getEstado(), quote.getSubtotal(), quote.getImpuestos(), quote.getTotal(),
            lines.stream().map(l -> new CotizacionResponse.Linea(l.getId(), l.getProductoId(), l.getCantidad(),
                l.getPrecioUnitario(), l.getDescuentoPct(), l.getTasaImpuesto())).toList());
    }
}
