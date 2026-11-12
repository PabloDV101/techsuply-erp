package com.techsupply.erp_backend.audit.application;

import com.techsupply.erp_backend.audit.domain.model.AuditResult;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public record AuditSearchCriteria(Long usuarioId, String modulo, String entidad, Long entidadId,
        Instant desde, Instant hasta, AuditResult resultado) {
    public static AuditSearchCriteria from(Long usuarioId, String modulo, String entidad, Long entidadId,
            String desde, String hasta, String resultado) {
        if ((usuarioId != null && usuarioId <= 0) || (entidadId != null && entidadId <= 0)) throw invalid();
        try {
            Instant start = desde == null ? null : Instant.parse(desde);
            Instant end = hasta == null ? null : Instant.parse(hasta);
            if (start != null && end != null && start.isAfter(end)) throw invalid();
            return new AuditSearchCriteria(usuarioId, code(modulo, 40), code(entidad, 80), entidadId,
                start, end, resultado == null ? null : AuditResult.valueOf(resultado));
        } catch (DateTimeParseException | IllegalArgumentException ex) {
            throw invalid();
        }
    }

    private static String code(String value, int max) {
        if (value == null) return null;
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (normalized.isEmpty() || normalized.length() > max || !normalized.matches("[A-Z0-9_]+")) throw invalid();
        return normalized;
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de auditoría no válido");
    }
}
