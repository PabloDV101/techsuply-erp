package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditField;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.audit.application.AuditState;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.contabilidad.domain.model.AsientoContable;
import com.techsupply.erp_backend.contabilidad.domain.model.EstadoAsientoContable;
import com.techsupply.erp_backend.contabilidad.domain.model.EstadoPeriodoContable;
import com.techsupply.erp_backend.contabilidad.domain.model.PartidaAsiento;
import com.techsupply.erp_backend.contabilidad.infrastructure.persistence.*;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import jakarta.persistence.EntityManager;
import java.util.*;
import java.util.stream.Collectors;
import java.time.Instant;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AsientoContableService {
    private static final Map<String, String> SORT_FIELDS = Map.of(
        "id", "id", "folio", "folio", "fechaContable", "fechaContable");
    private final AsientoContableRepository asientos;
    private final PartidaAsientoRepository partidas;
    private final CuentaContableRepository cuentas;
    private final PeriodoContableRepository periodos;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    private final JdbcTemplate db;
    private final EntityManager em;

    public AsientoContableService(AsientoContableRepository asientos, PartidaAsientoRepository partidas,
            CuentaContableRepository cuentas, PeriodoContableRepository periodos,
            AuthenticatedUserProvider users, AuditTrail audit, JdbcTemplate db, EntityManager em) {
        this.asientos = asientos; this.partidas = partidas; this.cuentas = cuentas; this.periodos = periodos;
        this.users = users; this.audit = audit; this.db = db; this.em = em;
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR')")
    public Creacion crear(AsientoContableCreateRequest request) {
        return crear(request, "MANUAL", "MANUAL", null);
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR')")
    public Creacion crearDesdeOrigen(AsientoContableCreateRequest request, String origenModulo,
            String origenTipo, Long origenId) {
        boolean invoice = "VENTAS".equals(origenModulo) && "FACTURA_VENTA".equals(origenTipo);
        boolean receipt = "CONTABILIDAD".equals(origenModulo) && "COBRO_CLIENTE".equals(origenTipo);
        boolean application = "CONTABILIDAD".equals(origenModulo) && "APLICACION_COBRO".equals(origenTipo);
        boolean purchaseReceipt = "COMPRAS".equals(origenModulo) && "RECEPCION_COMPRA".equals(origenTipo);
        boolean dispatch = "VENTAS".equals(origenModulo) && "DESPACHO_VENTA".equals(origenTipo);
        boolean adjustment = "INVENTARIO".equals(origenModulo) && "AJUSTE_INVENTARIO".equals(origenTipo);
        if (!(invoice || receipt || application || purchaseReceipt || dispatch || adjustment) || origenId == null || origenId < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origen contable inválido");
        return crear(request, origenModulo, origenTipo, origenId);
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_PAGOS_PROVEEDOR_CONFIRMAR') and hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR')")
    public Creacion crearDesdePagoProveedor(AsientoContableCreateRequest request, Long pagoId) {
        if (pagoId == null || pagoId < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Pago inválido");
        return crear(request, "CONTABILIDAD", "PAGO_PROVEEDOR", pagoId);
    }

    @Transactional @PreAuthorize("(#p1 == 'COMPRAS' and hasAuthority('COMPRAS_RECEPCIONES_CONFIRMAR')) or " +
        "(#p1 == 'VENTAS' and hasAuthority('VENTAS_DESPACHOS_CONFIRMAR')) or " +
        "(#p1 == 'INVENTARIO' and hasAuthority('INVENTARIO_AJUSTES_APLICAR'))")
    public AsientoContableResponse contabilizarOrigenInventario(AsientoContableCreateRequest request,
            String origenModulo, String origenTipo, Long origenId) {
        boolean valid = "COMPRAS".equals(origenModulo) && "RECEPCION_COMPRA".equals(origenTipo)
            || "VENTAS".equals(origenModulo) && "DESPACHO_VENTA".equals(origenTipo)
            || "INVENTARIO".equals(origenModulo) && "AJUSTE_INVENTARIO".equals(origenTipo);
        if (!valid || origenId == null || origenId < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Origen contable de Inventario inválido");
        Creacion creation = crear(request, origenModulo, origenTipo, origenId);
        if (creation.repetida() && creation.asiento().estado() != EstadoAsientoContable.CONTABILIZADO)
            throw conflicto("Existe un asiento de origen que no quedó contabilizado");
        return contabilizarInterno(creation.asiento().id());
    }

    @Transactional @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_VALIDAR')")
    public AsientoContableResponse contabilizarFacturaProveedor(AsientoContableCreateRequest request, Long facturaId) {
        if (facturaId == null || facturaId < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Factura de proveedor inválida");
        Creacion creation = crear(request, "COMPRAS", "FACTURA_PROVEEDOR", facturaId);
        if (creation.repetida() && creation.asiento().estado() != EstadoAsientoContable.CONTABILIZADO)
            throw conflicto("Existe un asiento de origen sin contabilizar");
        return contabilizarInterno(creation.asiento().id());
    }

    private Creacion crear(AsientoContableCreateRequest request, String origenModulo,
            String origenTipo, Long origenId) {
        String key = request.claveIdempotencia().trim();
        db.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(?, 6182050))", Integer.class, key);
        var existente = asientos.findByClaveIdempotencia(key);
        if (existente.isPresent()) {
            if (!mismoContenido(existente.get(), request, origenModulo, origenTipo, origenId)) throw conflicto("La clave de idempotencia ya se usó con otro contenido");
            return new Creacion(respuesta(existente.get()), true);
        }

        // Lock order: idempotency key, account catalog, then period row. Account admins use
        // the same catalog lock; period close takes only the period row and checks for drafts.
        db.execute("select pg_advisory_xact_lock(141474, 41004)");
        var periodo = periodos.bloquearPorId(request.periodoId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Periodo no encontrado"));
        if (periodo.getEstado() != EstadoPeriodoContable.ABIERTO)
            throw conflicto("El periodo está cerrado");
        if (!periodo.contiene(request.fechaContable()))
            throw conflicto("La fecha contable está fuera del periodo");

        var cuentasPorId = cuentas.findAllByIdIn(request.partidas().stream()
            .map(PartidaAsientoCreateRequest::cuentaId).collect(Collectors.toSet())).stream()
            .collect(Collectors.toMap(cuenta -> cuenta.getId(), cuenta -> cuenta));
        for (var linea : request.partidas()) {
            var cuenta = cuentasPorId.get(linea.cuentaId());
            if (cuenta == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta contable no encontrada");
            if (!cuenta.isActiva() || !cuenta.isAceptaMovimientos())
                throw conflicto("Todas las cuentas deben estar activas y aceptar movimientos");
            if (linea.debe() == null || linea.haber() == null)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Debe y haber son requeridos");
            try { new com.techsupply.erp_backend.contabilidad.domain.model.ImporteContable.Partida(linea.debe(), linea.haber()); }
            catch (IllegalArgumentException | NullPointerException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Importe de partida inválido");
            }
            if (linea.descripcion() != null && linea.descripcion().length() > 200)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Descripción de partida demasiado larga");
        }
        Long actor = users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "Autenticación requerida")).usuarioId();
        long consecutivo = db.queryForObject("select nextval('asiento_contable_folio_seq')", Long.class);
        String folio = "AS-" + String.format(Locale.ROOT, "%010d", consecutivo);
        var asiento = asientos.save(new AsientoContable(folio, periodo.getId(), request.fechaContable(),
            request.descripcion(), key, actor, origenModulo, origenTipo, origenId));
        em.flush();
        short numero = 1;
        for (var linea : request.partidas()) {
            partidas.save(new PartidaAsiento(asiento.getId(), numero++, linea.cuentaId(), linea.debe(),
                linea.haber(), normalizar(linea.descripcion())));
        }
        em.flush();
        audit.change(AuditAction.ACCOUNTING_ENTRY_DRAFT_CREATED, asiento.getId());
        return new Creacion(respuesta(asiento), false);
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR')")
    public AsientoContableResponse editar(Long id, AsientoContableEditRequest request) {
        var actual = buscar(id);
        db.execute("select pg_advisory_xact_lock(141474, 41004)");
        var periodo = periodos.bloquearPorId(actual.getPeriodoId()).orElseThrow(() -> noEncontrado("Periodo no encontrado"));
        var asiento = asientos.bloquearPorId(id).orElseThrow(() -> noEncontrado("Asiento no encontrado"));
        requireDraft(asiento);
        validarFecha(periodo, request.fechaContable());
        validarPartidas(request.partidas());
        Long actor = actorId();
        var antes = asiento.getEstado();
        asiento.editar(request.fechaContable(), request.descripcion().trim());
        asiento.registrarEdicion(request.motivo(), actor, Instant.now());
        asientos.save(asiento);
        partidas.deleteByAsientoId(id);
        em.flush();
        guardarPartidas(id, request.partidas());
        history(id, null, actor, "BORRADOR_EDITADO", null, null, request.motivo());
        audit.change(AuditAction.ACCOUNTING_ENTRY_DRAFT_EDITED, id,
            Set.of(AuditField.FECHA_CONTABLE, AuditField.DESCRIPCION, AuditField.PARTIDAS, AuditField.MOTIVO),
            AuditState.valueOf(antes.name()), AuditState.BORRADOR);
        em.flush();
        return respuesta(asiento);
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR')")
    public AsientoContableResponse cancelar(Long id, AsientoContableCancelRequest request) {
        var actual = buscar(id);
        db.execute("select pg_advisory_xact_lock(141474, 41004)");
        periodos.bloquearPorId(actual.getPeriodoId()).orElseThrow(() -> noEncontrado("Periodo no encontrado"));
        var asiento = asientos.bloquearPorId(id).orElseThrow(() -> noEncontrado("Asiento no encontrado"));
        requireDraft(asiento);
        Long actor = actorId();
        asiento.cancelar(actor, Instant.now(), request.motivo());
        asientos.save(asiento);
        history(id, null, actor, "BORRADOR_CANCELADO", null, null, request.motivo());
        audit.change(AuditAction.ACCOUNTING_ENTRY_DRAFT_CANCELLED, id, Set.of(AuditField.ESTADO, AuditField.MOTIVO),
            AuditState.BORRADOR, AuditState.CANCELADO);
        em.flush();
        return respuesta(asiento);
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_CONTABILIZAR')")
    public AsientoContableResponse contabilizar(Long id) {
        return contabilizarInterno(id);
    }

    private AsientoContableResponse contabilizarInterno(Long id) {
        var actual = buscar(id);
        db.execute("select pg_advisory_xact_lock(141474, 41004)");
        var periodo = periodos.bloquearPorId(actual.getPeriodoId()).orElseThrow(() -> noEncontrado("Periodo no encontrado"));
        var asiento = asientos.bloquearPorId(id).orElseThrow(() -> noEncontrado("Asiento no encontrado"));
        if (asiento.getEstado() == EstadoAsientoContable.CONTABILIZADO) return respuesta(asiento);
        requireDraft(asiento);
        validarFecha(periodo, asiento.getFechaContable());
        var lineas = partidas.findByAsientoIdOrderByNumeroLineaAsc(id);
        validarBalance(lineas);
        Long actor = actorId();
        asiento.contabilizar(actor, Instant.now());
        asientos.save(asiento);
        history(id, null, actor, "CONTABILIZADO", null, null, null);
        audit.change(AuditAction.ACCOUNTING_ENTRY_POSTED, id, Set.of(AuditField.ESTADO), AuditState.BORRADOR, AuditState.CONTABILIZADO);
        em.flush();
        return respuesta(asiento);
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_REVERTIR')")
    public AsientoContableResponse revertir(Long id, AsientoContableReverseRequest request) {
        String key = request.claveIdempotencia().trim();
        String hash = huella(id, request.fechaContable(), request.motivo());
        db.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(?, 6182050))", Integer.class, key);
        var previa = db.query("select asiento_id, resultado_asiento_id, huella_solicitud from historial_asientos_contables where clave_idempotencia=?",
            (rs, row) -> new Object[]{rs.getLong(1), rs.getLong(2), rs.getString(3)}, key);
        if (!previa.isEmpty()) {
            Object[] value = previa.getFirst();
            if (((Number)value[0]).longValue() != id || !hash.equals(((String)value[2]).trim()))
                throw conflicto("La clave de idempotencia ya se usó con otra solicitud");
            return respuesta(buscar(((Number)value[1]).longValue()));
        }
        var actual = buscar(id);
        db.execute("select pg_advisory_xact_lock(141474, 41004)");
        var periodo = periodos.bloquearPorId(periodoIdDeFecha(request.fechaContable()))
            .orElseThrow(() -> conflicto("No existe un periodo abierto que incluya la fecha del contraasiento"));
        var original = asientos.bloquearPorId(id).orElseThrow(() -> noEncontrado("Asiento no encontrado"));
        if ("CONTABILIDAD".equals(original.getOrigenModulo()) && "PAGO_PROVEEDOR".equals(original.getOrigenTipo()))
            throw conflicto("La reversión de pagos a proveedores aún no está habilitada");
        if (original.getEstado() != EstadoAsientoContable.CONTABILIZADO) throw conflicto("Solo puede revertirse un asiento contabilizado");
        validarFecha(periodo, request.fechaContable());
        Long actor = actorId(); Instant now = Instant.now();
        long seq = db.queryForObject("select nextval('asiento_contable_folio_seq')", Long.class);
        String folio = "AS-" + String.format(Locale.ROOT, "%010d", seq);
        var contra = AsientoContable.contraasiento(folio, periodo.getId(), request.fechaContable(),
            "Contraasiento de " + original.getFolio(), key, actor, id, request.motivo(), now);
        contra = asientos.save(contra); em.flush();
        var originales = partidas.findByAsientoIdOrderByNumeroLineaAsc(id);
        short n = 1;
        for (var linea : originales) partidas.save(new PartidaAsiento(contra.getId(), n++, linea.getCuentaContableId(),
            linea.getHaber(), linea.getDebe(), linea.getDescripcion()));
        original.revertir(request.motivo()); asientos.save(original);
        history(id, contra.getId(), actor, "REVERSADO", key, hash, request.motivo());
        audit.change(AuditAction.ACCOUNTING_ENTRY_REVERSED, id,
            Set.of(AuditField.ESTADO, AuditField.MOTIVO), AuditState.CONTABILIZADO, AuditState.REVERSADO);
        audit.change(AuditAction.ACCOUNTING_COUNTERENTRY_POSTED, contra.getId(),
            Set.of(AuditField.ESTADO, AuditField.FECHA_CONTABLE), AuditState.BORRADOR, AuditState.CONTABILIZADO);
        em.flush();
        return respuesta(contra);
    }

    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_LEER')")
    public AsientoContableResponse obtener(Long id) { return respuesta(buscar(id)); }

    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_LEER')")
    public PageResponse<AsientoContableResponse> listar(int page, int size, String sort, String direction) {
        Page<AsientoContable> resultado = asientos.findAll(PageRequests.of(page, size, sort, direction, SORT_FIELDS, "id"));
        var ids = resultado.getContent().stream().map(AsientoContable::getId).toList();
        var lineas = partidas.findByAsientoIdInOrderByAsientoIdAscNumeroLineaAsc(ids);
        var porAsiento = lineas.stream().collect(Collectors.groupingBy(PartidaAsiento::getAsientoId));
        return PageResponse.from(resultado, asiento -> AsientoContableResponse.from(asiento,
            porAsiento.getOrDefault(asiento.getId(), List.of()).stream().map(PartidaAsientoResponse::from).toList()));
    }

    private AsientoContable buscar(Long id) {
        return asientos.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Asiento no encontrado"));
    }
    private AsientoContableResponse respuesta(AsientoContable asiento) {
        return AsientoContableResponse.from(asiento,
            partidas.findByAsientoIdOrderByNumeroLineaAsc(asiento.getId()).stream().map(PartidaAsientoResponse::from).toList());
    }
    private boolean mismoContenido(AsientoContable existente, AsientoContableCreateRequest request,
            String origenModulo, String origenTipo, Long origenId) {
        if (!existente.getPeriodoId().equals(request.periodoId())
                || !existente.getFechaContable().equals(request.fechaContable())
                || !existente.getDescripcion().equals(request.descripcion().trim())
                || !existente.getOrigenModulo().equals(origenModulo)
                || !existente.getOrigenTipo().equals(origenTipo)
                || !Objects.equals(existente.getOrigenId(), origenId)) return false;
        var guardadas = partidas.findByAsientoIdOrderByNumeroLineaAsc(existente.getId());
        if (guardadas.size() != request.partidas().size()) return false;
        for (int i = 0; i < guardadas.size(); i++) {
            var guardada = guardadas.get(i); var solicitada = request.partidas().get(i);
            if (!guardada.getCuentaContableId().equals(solicitada.cuentaId())
                    || guardada.getDebe().compareTo(solicitada.debe()) != 0
                    || guardada.getHaber().compareTo(solicitada.haber()) != 0
                    || !Objects.equals(guardada.getDescripcion(), normalizar(solicitada.descripcion()))) return false;
        }
        return true;
    }
    private static String normalizar(String value) { return value == null ? null : value.trim(); }
    private void validarPartidas(List<PartidaAsientoCreateRequest> lineas) {
        var accounts = cuentas.findAllByIdIn(lineas.stream().map(PartidaAsientoCreateRequest::cuentaId).collect(Collectors.toSet()))
            .stream().collect(Collectors.toMap(c -> c.getId(), c -> c));
        for (var l : lineas) {
            var cuenta = accounts.get(l.cuentaId());
            if (cuenta == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta contable no encontrada");
            if (!cuenta.isActiva() || !cuenta.isAceptaMovimientos()) throw conflicto("La cuenta debe estar activa y aceptar movimientos");
            try { new com.techsupply.erp_backend.contabilidad.domain.model.ImporteContable.Partida(l.debe(), l.haber()); }
            catch (RuntimeException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Importe de partida inválido"); }
        }
    }
    private void guardarPartidas(Long id, List<PartidaAsientoCreateRequest> lineas) {
        short n = 1;
        for (var l : lineas) partidas.save(new PartidaAsiento(id, n++, l.cuentaId(), l.debe(), l.haber(), normalizar(l.descripcion())));
    }
    private void validarBalance(List<PartidaAsiento> lineas) {
        var debe = lineas.stream().map(PartidaAsiento::getDebe).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        var haber = lineas.stream().map(PartidaAsiento::getHaber).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
        if (lineas.size() < 2 || debe.compareTo(haber) != 0) throw conflicto("El asiento requiere al menos dos partidas equilibradas");
    }
    private void requireDraft(AsientoContable asiento) {
        if (asiento.getEstado() != EstadoAsientoContable.BORRADOR) throw conflicto("La operación solo admite asientos en borrador");
    }
    private Long actorId() { return users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
        "Autenticación requerida")).usuarioId(); }
    private void validarFecha(com.techsupply.erp_backend.contabilidad.domain.model.PeriodoContable p, java.time.LocalDate f) {
        if (p.getEstado() != EstadoPeriodoContable.ABIERTO || !p.contiene(f)) throw conflicto("La fecha debe estar dentro de un periodo abierto");
    }
    private Long periodoIdDeFecha(java.time.LocalDate fecha) {
        return db.query("select id from periodos_contables where estado='ABIERTO' and fecha_inicio<=? and fecha_fin>=?",
            (rs, row) -> rs.getLong(1), fecha, fecha).stream().findFirst()
            .orElseThrow(() -> conflicto("No existe un periodo abierto que incluya la fecha del contraasiento"));
    }
    private void history(Long id, Long result, Long actor, String action, String key, String hash, String reason) {
        db.update("insert into historial_asientos_contables(asiento_id,resultado_asiento_id,actor_usuario_id,accion,clave_idempotencia,huella_solicitud,motivo,registrado_en) values (?,?,?,?,?,?,?,?)",
            id, result, actor, action, key, hash, reason, java.sql.Timestamp.from(Instant.now()));
    }
    private static String huella(Long id, java.time.LocalDate fecha, String motivo) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
            .digest((id + "|" + fecha + "|" + motivo.trim()).getBytes(StandardCharsets.UTF_8))); }
        catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    private static ResponseStatusException noEncontrado(String mensaje) { return new ResponseStatusException(HttpStatus.NOT_FOUND, mensaje); }
    private static ResponseStatusException conflicto(String mensaje) { return new ResponseStatusException(HttpStatus.CONFLICT, mensaje); }

    public record Creacion(AsientoContableResponse asiento, boolean repetida) {}
}
