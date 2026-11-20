package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.contabilidad.domain.model.*;
import com.techsupply.erp_backend.contabilidad.infrastructure.persistence.AsientoContableRepository;
import com.techsupply.erp_backend.contabilidad.infrastructure.persistence.PeriodoContableRepository;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PeriodoContableService {
    private static final Map<String, String> SORT_FIELDS = Map.of("id", "id", "fechaInicio", "fechaInicio", "fechaFin", "fechaFin");
    private final PeriodoContableRepository periodos;
    private final AsientoContableRepository asientos;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    private final EntityManager em;
    private final Clock clock;
    private final JdbcTemplate db;

    public PeriodoContableService(PeriodoContableRepository periodos, AsientoContableRepository asientos,
            AuthenticatedUserProvider users, AuditTrail audit, EntityManager em, Clock clock, JdbcTemplate db) {
        this.periodos = periodos; this.asientos = asientos; this.users = users; this.audit = audit;
        this.em = em; this.clock = clock; this.db = db;
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_PERIODOS_ADMIN')")
    public PeriodoContableResponse crear(PeriodoContableCreateRequest request) {
        if (request.fechaInicio().isAfter(request.fechaFin()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango de fechas inválido");
        // Serialize cooperative inserts; GiST exclusion remains the database-wide integrity guard.
        db.execute("select pg_advisory_xact_lock(141474, 41005)");
        var periodo = periodos.save(new PeriodoContable(request.fechaInicio(), request.fechaFin()));
        em.flush(); // La exclusión GiST decide también las creaciones concurrentes.
        audit.change(AuditAction.ACCOUNTING_PERIOD_CREATED, periodo.getId());
        return PeriodoContableResponse.from(periodo);
    }

    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('CONTABILIDAD_PERIODOS_LEER')")
    public PeriodoContableResponse obtener(Long id) {
        return PeriodoContableResponse.from(periodos.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Periodo no encontrado")));
    }

    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('CONTABILIDAD_PERIODOS_LEER')")
    public PageResponse<PeriodoContableResponse> listar(int page, int size, String sort, String direction) {
        return PageResponse.from(periodos.findAll(PageRequests.of(page, size, sort, direction, SORT_FIELDS, "id")), PeriodoContableResponse::from);
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_PERIODOS_CERRAR')")
    public PeriodoContableResponse cerrar(Long id) {
        // Future draft/posting operations must acquire the same row lock before checking state.
        var periodo = periodos.bloquearPorId(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Periodo no encontrado"));
        if (periodo.getEstado() == EstadoPeriodoContable.CERRADO) return PeriodoContableResponse.from(periodo);
        if (asientos.existsByPeriodoIdAndEstado(id, EstadoAsientoContable.BORRADOR))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El periodo tiene asientos en borrador");
        Long actor = users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida")).usuarioId();
        // PostgreSQL timestamps store microseconds; retry must return the exact persisted timestamp.
        periodo.cerrar(actor, clock.instant().truncatedTo(ChronoUnit.MICROS));
        em.flush();
        audit.change(AuditAction.ACCOUNTING_PERIOD_CLOSED, id, Set.of(AuditField.ESTADO), AuditState.ABIERTO, AuditState.CERRADO);
        return PeriodoContableResponse.from(periodo);
    }
}
