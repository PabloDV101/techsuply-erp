package com.techsupply.erp_backend.audit.api;

import com.techsupply.erp_backend.audit.application.AuditEventResponse;
import com.techsupply.erp_backend.audit.application.AuditQueryService;
import com.techsupply.erp_backend.audit.application.AuditSearchCriteria;
import com.techsupply.erp_backend.common.api.PageResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit/events")
public class AuditController {
    private final AuditQueryService service;
    public AuditController(AuditQueryService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('SEGURIDAD_AUDITORIA_LEER')")
    public PageResponse<AuditEventResponse> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "fecha") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) Long usuarioId,
            @RequestParam(required = false) String modulo,
            @RequestParam(required = false) String entidad,
            @RequestParam(required = false) Long entidadId,
            @RequestParam(required = false) String desde,
            @RequestParam(required = false) String hasta,
            @RequestParam(required = false) String resultado) {
        return service.search(page, size, sort, direction,
            AuditSearchCriteria.from(usuarioId, modulo, entidad, entidadId, desde, hasta, resultado));
    }
}
