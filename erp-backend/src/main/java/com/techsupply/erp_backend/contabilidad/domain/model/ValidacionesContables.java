package com.techsupply.erp_backend.contabilidad.domain.model;

import java.util.Objects;

final class ValidacionesContables {
    private ValidacionesContables() {}

    static long id(Long value) {
        if (value == null || value < 1) throw new IllegalArgumentException("Identificador inválido");
        return value;
    }

    static String texto(String value, int max) {
        Objects.requireNonNull(value, "Texto requerido");
        String result = value.trim();
        if (result.isBlank() || result.length() > max) throw new IllegalArgumentException("Texto inválido");
        return result;
    }
}
