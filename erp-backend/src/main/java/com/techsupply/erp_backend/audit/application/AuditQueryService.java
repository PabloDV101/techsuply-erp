package com.techsupply.erp_backend.audit.application;

import com.techsupply.erp_backend.audit.domain.model.AuditEvent;
import com.techsupply.erp_backend.audit.infrastructure.persistence.AuditEventRepository;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditQueryService {
    private static final Map<String, String> SORT_FIELDS = Map.of("fecha", "fecha", "id", "id");
    private final AuditEventRepository repository;

    public AuditQueryService(AuditEventRepository repository) { this.repository = repository; }

    @Transactional(readOnly = true)
    public PageResponse<AuditEventResponse> search(int page, int size, String sort, String direction,
            AuditSearchCriteria criteria) {
        Specification<AuditEvent> spec = (root, query, cb) -> cb.conjunction();
        if (criteria.usuarioId() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("usuarioId"), criteria.usuarioId()));
        if (criteria.modulo() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("modulo"), criteria.modulo()));
        if (criteria.entidad() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("entidad"), criteria.entidad()));
        if (criteria.entidadId() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("entidadId"), criteria.entidadId()));
        if (criteria.desde() != null) spec = spec.and((r, q, cb) -> cb.greaterThanOrEqualTo(r.get("fecha"), criteria.desde()));
        if (criteria.hasta() != null) spec = spec.and((r, q, cb) -> cb.lessThanOrEqualTo(r.get("fecha"), criteria.hasta()));
        if (criteria.resultado() != null) spec = spec.and((r, q, cb) -> cb.equal(r.get("resultado"), criteria.resultado()));
        return PageResponse.from(repository.findAll(spec, PageRequests.of(page, size, sort, direction, SORT_FIELDS, "id")),
            AuditEventResponse::from);
    }
}
