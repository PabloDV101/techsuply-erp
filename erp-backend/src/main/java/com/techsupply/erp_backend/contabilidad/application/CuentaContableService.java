package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.contabilidad.domain.model.CuentaContable;
import com.techsupply.erp_backend.contabilidad.domain.model.JerarquiaCuentas;
import com.techsupply.erp_backend.contabilidad.infrastructure.persistence.CuentaContableRepository;
import com.techsupply.erp_backend.contabilidad.infrastructure.persistence.PartidaAsientoRepository;
import jakarta.persistence.EntityManager;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CuentaContableService {
    private static final Map<String, String> SORT_FIELDS = Map.of("id", "id", "codigo", "codigo", "nombre", "nombre");
    private final CuentaContableRepository cuentas;
    private final PartidaAsientoRepository partidas;
    private final JdbcTemplate db;
    private final EntityManager em;
    private final AuditTrail audit;

    public CuentaContableService(CuentaContableRepository cuentas, PartidaAsientoRepository partidas,
            JdbcTemplate db, EntityManager em, AuditTrail audit) {
        this.cuentas = cuentas; this.partidas = partidas; this.db = db; this.em = em; this.audit = audit;
    }

    // All account mutations use this transaction-scoped lock; future writers must share this protocol.
    private void bloquearCatalogo() { db.execute("select pg_advisory_xact_lock(141474, 41004)"); }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_CUENTAS_ADMIN')")
    public CuentaContableResponse crear(CuentaContableWriteRequest request) {
        bloquearCatalogo();
        String codigo = codigo(request.codigo());
        if (cuentas.existsByCodigoIgnoreCase(codigo)) throw conflicto("Código duplicado");
        short nivel = validarPadre(null, request.cuentaPadreId());
        var cuenta = cuentas.save(new CuentaContable(codigo, request.nombre(), request.tipo(), request.naturaleza(),
            nivel, request.cuentaPadreId(), request.aceptaMovimientos()));
        em.flush();
        audit.change(AuditAction.ACCOUNTING_ACCOUNT_CREATED, cuenta.getId());
        return CuentaContableResponse.from(cuenta);
    }

    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('CONTABILIDAD_CUENTAS_LEER')")
    public CuentaContableResponse obtener(Long id) { return CuentaContableResponse.from(buscar(id)); }

    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('CONTABILIDAD_CUENTAS_LEER')")
    public PageResponse<CuentaContableResponse> listar(int page, int size, String sort, String direction) {
        return PageResponse.from(cuentas.findAll(PageRequests.of(page, size, sort, direction, SORT_FIELDS, "id")), CuentaContableResponse::from);
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_CUENTAS_ADMIN')")
    public CuentaContableResponse actualizar(Long id, CuentaContableWriteRequest request) {
        bloquearCatalogo();
        var cuenta = bloquear(id);
        String normalizado = codigo(request.codigo());
        // V14 permitió códigos existentes en minúsculas; un cambio de nombre no debe reescribirlos.
        String codigo = cuenta.getCodigo().equalsIgnoreCase(normalizado) ? cuenta.getCodigo() : normalizado;
        String nombre = request.nombre().trim();
        if (cuentas.existsByCodigoIgnoreCaseAndIdNot(codigo, id)) throw conflicto("Código duplicado");
        boolean cambioPadre = !Objects.equals(cuenta.getCuentaPadreId(), request.cuentaPadreId());
        boolean hijos = cuentas.existsByCuentaPadreId(id);
        boolean historia = partidas.existsByCuentaContableId(id);
        if (hijos && request.aceptaMovimientos()) throw conflicto("Una cuenta padre no acepta movimientos");
        if (hijos && cambioPadre) throw conflicto("No se cambia la jerarquía de una cuenta con descendientes");
        if (historia && (!cuenta.getCodigo().equals(codigo) || cuenta.getTipo() != request.tipo()
                || cuenta.getNaturaleza() != request.naturaleza() || cambioPadre
                || cuenta.isAceptaMovimientos() != request.aceptaMovimientos()))
            throw conflicto("La cuenta tiene partidas y no permite cambios estructurales");
        short nivel = cambioPadre ? validarPadre(id, request.cuentaPadreId()) : cuenta.getNivel();
        Set<AuditField> fields = new HashSet<>();
        if (!cuenta.getCodigo().equals(codigo)) fields.add(AuditField.CODIGO);
        if (!cuenta.getNombre().equals(nombre)) fields.add(AuditField.NOMBRE);
        if (cuenta.getTipo() != request.tipo()) fields.add(AuditField.TIPO_CUENTA);
        if (cuenta.getNaturaleza() != request.naturaleza()) fields.add(AuditField.NATURALEZA_CUENTA);
        if (cambioPadre) fields.add(AuditField.CUENTA_PADRE);
        if (cuenta.isAceptaMovimientos() != request.aceptaMovimientos()) fields.add(AuditField.ACEPTA_MOVIMIENTOS);
        cuenta.actualizar(codigo, nombre, request.tipo(), request.naturaleza(), nivel, request.cuentaPadreId(), request.aceptaMovimientos());
        em.flush();
        if (!fields.isEmpty()) audit.change(AuditAction.ACCOUNTING_ACCOUNT_UPDATED, id, fields, null, null);
        return CuentaContableResponse.from(cuenta);
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_CUENTAS_ADMIN')")
    public CuentaContableResponse cambiarEstado(Long id, boolean activa) {
        bloquearCatalogo();
        var cuenta = bloquear(id);
        boolean antes = cuenta.isActiva();
        if (antes && !activa && partidas.existsByCuentaContableId(id))
            throw conflicto("La cuenta tiene partidas y no puede darse de baja");
        cuenta.cambiarEstado(activa);
        em.flush();
        if (antes != activa) audit.change(AuditAction.ACCOUNTING_ACCOUNT_STATUS_CHANGED, id, Set.of(AuditField.ACTIVA),
            antes ? AuditState.ACTIVO : AuditState.INACTIVO, activa ? AuditState.ACTIVO : AuditState.INACTIVO);
        return CuentaContableResponse.from(cuenta);
    }

    private short validarPadre(Long cuentaId, Long padreId) {
        if (padreId == null) return 1;
        var padres = new HashMap<Long, Long>();
        Long cursor = padreId;
        CuentaContable padre = null;
        while (cursor != null) {
            if (Objects.equals(cursor, cuentaId) || padres.containsKey(cursor)) throw conflicto("Ciclo en jerarquía");
            var actual = cuentas.findById(cursor).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta padre no encontrada"));
            if (padre == null) padre = actual;
            padres.put(cursor, actual.getCuentaPadreId());
            cursor = actual.getCuentaPadreId();
        }
        try { JerarquiaCuentas.validarPadre(cuentaId, padreId, padres); }
        catch (IllegalArgumentException ex) { throw conflicto("Jerarquía inválida"); }
        if (!padre.isActiva() || padre.isAceptaMovimientos() || partidas.existsByCuentaContableId(padreId))
            throw conflicto("La cuenta padre debe estar activa, ser agrupadora y no tener partidas");
        if (padre.getNivel() == Short.MAX_VALUE) throw conflicto("Nivel máximo de jerarquía");
        return (short) (padre.getNivel() + 1);
    }

    private CuentaContable buscar(Long id) {
        return cuentas.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
    }
    private CuentaContable bloquear(Long id) {
        return cuentas.bloquearPorId(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
    }
    private static String codigo(String raw) {
        String result = raw.trim().toUpperCase(Locale.ROOT);
        if (result.isBlank() || result.length() > 30) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código inválido");
        return result;
    }
    private static ResponseStatusException conflicto(String reason) { return new ResponseStatusException(HttpStatus.CONFLICT, reason); }
}
