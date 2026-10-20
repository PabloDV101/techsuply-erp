package com.techsupply.erp_backend.maestros.application.dto;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** Validated, normalized optional filters for the category catalog. */
public record CategoriaSearchCriteria(String codigo, String nombre, Boolean activa, Long categoriaPadreId) {
    public static CategoriaSearchCriteria from(String codigo, String nombre, String activa, Long categoriaPadreId) {
        if (categoriaPadreId != null && categoriaPadreId <= 0) {
            throw invalid();
        }
        Boolean active = null;
        if (activa != null) {
            if (!activa.equals("true") && !activa.equals("false")) throw invalid();
            active = Boolean.valueOf(activa);
        }
        return new CategoriaSearchCriteria(
            normalize(codigo, 20, true), normalize(nombre, 100, false), active, categoriaPadreId);
    }

    private static String normalize(String value, int maxLength, boolean uppercase) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maxLength) throw invalid();
        return uppercase ? normalized.toUpperCase(Locale.ROOT) : normalized.toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de búsqueda no válido");
    }
}
