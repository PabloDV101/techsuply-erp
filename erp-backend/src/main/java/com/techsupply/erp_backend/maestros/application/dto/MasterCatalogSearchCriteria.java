package com.techsupply.erp_backend.maestros.application.dto;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Same validated code/status filters used by the three V6 catalogs. */
public record MasterCatalogSearchCriteria(String codigo, Boolean activo) {
    public static MasterCatalogSearchCriteria from(String codigo, String activo) {
        String normalizedCode = null;
        if (codigo != null) {
            normalizedCode = codigo.trim().toUpperCase(Locale.ROOT);
            if (normalizedCode.isEmpty() || normalizedCode.length() > 20) throw invalid();
        }
        Boolean active = null;
        if (activo != null) {
            if (!activo.equals("true") && !activo.equals("false")) throw invalid();
            active = Boolean.valueOf(activo);
        }
        return new MasterCatalogSearchCriteria(normalizedCode, active);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de catálogo no válido");
    }
}
