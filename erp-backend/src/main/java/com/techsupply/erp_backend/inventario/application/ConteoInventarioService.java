package com.techsupply.erp_backend.inventario.application;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditField;
import com.techsupply.erp_backend.audit.application.AuditState;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.dto.CapturarConteoRequest;
import com.techsupply.erp_backend.inventario.application.dto.ConteoResponse;
import com.techsupply.erp_backend.inventario.application.dto.CrearConteoRequest;
import com.techsupply.erp_backend.inventario.domain.model.ConteoInventario;
import com.techsupply.erp_backend.inventario.domain.model.DetalleConteo;
import com.techsupply.erp_backend.inventario.domain.model.EstadoConteo;
import com.techsupply.erp_backend.inventario.infrastructure.persistence.ConteoInventarioRepository;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ConteoInventarioService {
    private final ConteoInventarioRepository counts;
    private final JdbcTemplate db;
    private final EntityManager em;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    public ConteoInventarioService(ConteoInventarioRepository counts, JdbcTemplate db, EntityManager em, AuthenticatedUserProvider users, AuditTrail audit) {
        this.counts = counts; this.db = db; this.em = em; this.users = users; this.audit = audit;
    }
    @Transactional @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_ADMIN')")
    public ConteoResponse crear(CrearConteoRequest request) {
        activo("almacenes", request.almacenId());
        long seq = Objects.requireNonNull(db.queryForObject("select nextval('conteo_inventario_folio_seq')", Long.class));
        if (seq > 999999999999L) throw conflict("Secuencia de folios agotada");
        Long actor = users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida")).usuarioId();
        ConteoInventario count = new ConteoInventario("CI-" + String.format(Locale.ROOT, "%012d", seq), request.almacenId(), actor, request.fechaProgramada(), request.clase());
        counts.save(count); em.flush();
        audit.change(AuditAction.INVENTORY_COUNT_CREATED, count.getId());
        return response(count);
    }
    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_LEER')")
    public ConteoResponse obtener(Long id) { return response(counts.findById(validarId(id)).orElseThrow(() -> missing("Conteo"))); }
    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_LEER')")
    public PageResponse<ConteoResponse> listar(int page, int size, Long almacenId, String estado) {
        if (almacenId != null) validarId(almacenId);
        EstadoConteo filtro = null;
        if (estado != null) {
            try { filtro = EstadoConteo.valueOf(estado); }
            catch (IllegalArgumentException ex) { throw bad("Estado inválido"); }
        }
        Pageable paging = PageRequests.of(page, size, "id", "desc", Map.of("id", "id"));
        Page<ConteoInventario> result;
        if (almacenId != null && filtro != null) result = counts.findByAlmacenIdAndEstado(almacenId, filtro, paging);
        else if (almacenId != null) result = counts.findByAlmacenId(almacenId, paging);
        else if (filtro != null) result = counts.findByEstado(filtro, paging);
        else result = counts.findAll(paging);
        if (!result.isEmpty()) counts.conDetalles(result.getContent().stream().map(ConteoInventario::getId).toList());
        return PageResponse.from(result, ConteoInventarioService::response);
    }
    @Transactional @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_ADMIN')")
    public ConteoResponse capturar(Long id, Long productoId, CapturarConteoRequest request) {
        ConteoInventario count = bloquear(id);
        activo("almacenes", count.getAlmacenId()); activo("productos", validarId(productoId));
        var stock = db.query("select cantidad_fisica, version from existencias where producto_id=? and almacen_id=? for update",
            (rs, row) -> new Referencia(rs.getBigDecimal("cantidad_fisica"), rs.getInt("version")), productoId, count.getAlmacenId());
        EstadoConteo before = count.getEstado();
        try { count.capturar(productoId, stock.isEmpty() ? BigDecimal.ZERO : stock.getFirst().cantidad(), request.cantidadContada(),
                !stock.isEmpty(), stock.isEmpty() ? null : stock.getFirst().version()); }
        catch (IllegalArgumentException ex) { throw bad("Cantidad o referencia inválida"); }
        catch (IllegalStateException ex) { throw conflict("Estado de conteo inválido"); }
        em.flush();
        DetalleConteo detail = count.getDetalles().stream().filter(d -> d.getProductoId().equals(productoId)).findFirst().orElseThrow();
        audit.change(AuditAction.INVENTORY_COUNT_LINE_CAPTURED, detail.getId(), Set.of(AuditField.CANTIDAD_CONTADA, AuditField.CANTIDAD_SISTEMA, AuditField.VERSION_EXISTENCIA), null, null);
        if (before != count.getEstado()) audit.change(AuditAction.INVENTORY_COUNT_STARTED, count.getId(), Set.of(AuditField.ESTADO), AuditState.valueOf(before.name()), AuditState.valueOf(count.getEstado().name()));
        return response(count);
    }
    @Transactional @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_ADMIN')")
    public ConteoResponse cerrar(Long id) { return cambiarEstado(id, true); }
    @Transactional @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_ADMIN')")
    public ConteoResponse cancelar(Long id) { return cambiarEstado(id, false); }
    private ConteoResponse cambiarEstado(Long id, boolean cerrar) {
        ConteoInventario count = bloquear(id); EstadoConteo before = count.getEstado();
        try { if (cerrar) count.cerrar(); else count.cancelar(); }
        catch (IllegalStateException ex) { throw conflict("Estado de conteo inválido"); }
        em.flush();
        audit.change(cerrar ? AuditAction.INVENTORY_COUNT_CLOSED : AuditAction.INVENTORY_COUNT_CANCELLED, count.getId(),
            Set.of(AuditField.ESTADO), AuditState.valueOf(before.name()), AuditState.valueOf(count.getEstado().name()));
        return response(count);
    }
    private ConteoInventario bloquear(Long id) { return counts.bloquearPorId(validarId(id)).orElseThrow(() -> missing("Conteo")); }
    private static Long validarId(Long id) { if (id == null || id < 1) throw bad("Identificador inválido"); return id; }
    private void activo(String table, Long id) {
        validarId(id);
        // Table name comes only from the two fixed internal call sites, never HTTP input.
        String sql = switch (table) {
            case "almacenes" -> "select activo from almacenes where id=? for share";
            case "productos" -> "select activo from productos where id=? for share";
            default -> throw new IllegalArgumentException("Catálogo no admitido");
        };
        List<Boolean> values = db.query(sql, (rs, row) -> rs.getBoolean(1), id);
        if (values.isEmpty()) throw missing("Referencia");
        if (!values.getFirst()) throw conflict("Referencia inactiva");
    }
    private static ConteoResponse response(ConteoInventario count) {
        var lines = count.getDetalles().stream().sorted(Comparator.comparing(DetalleConteo::getProductoId)).map(d ->
            new ConteoResponse.Linea(d.getId(), d.getProductoId(), d.getCantidadSistema(), d.getCantidadContada(), d.getDiferencia(), d.isExistenciaPrevia(), d.getVersionExistencia())).toList();
        return new ConteoResponse(count.getId(), count.getFolio(), count.getAlmacenId(), count.getCreadoPorUsuarioId(), count.getFechaProgramada(), count.getClase(), count.getEstado(), lines);
    }
    private record Referencia(BigDecimal cantidad, int version) {}
    private static ResponseStatusException bad(String msg) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg); }
    private static ResponseStatusException missing(String msg) { return new ResponseStatusException(HttpStatus.NOT_FOUND, msg); }
    private static ResponseStatusException conflict(String msg) { return new ResponseStatusException(HttpStatus.CONFLICT, msg); }
}
