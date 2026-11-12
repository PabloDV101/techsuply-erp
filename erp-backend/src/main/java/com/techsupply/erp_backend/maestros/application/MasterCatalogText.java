package com.techsupply.erp_backend.maestros.application;

import java.util.Locale;

/** Deterministic normalization shared by three current catalog services. */
final class MasterCatalogText {
    private MasterCatalogText() {}

    static String code(String value) { return value.trim().toUpperCase(Locale.ROOT); }
    static String required(String value) { return value.trim(); }
    static String optional(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
