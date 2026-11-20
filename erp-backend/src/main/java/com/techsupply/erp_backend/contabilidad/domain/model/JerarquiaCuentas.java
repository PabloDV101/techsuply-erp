package com.techsupply.erp_backend.contabilidad.domain.model;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;

/** Valida una fotografía completa de ancestros; el servicio deberá coordinar concurrencia. */
public final class JerarquiaCuentas {
    private JerarquiaCuentas() {}

    public static void validarPadre(Long cuentaId, Long padreId, Map<Long, Long> padres) {
        Objects.requireNonNull(padres, "Jerarquía requerida");
        if (cuentaId != null) ValidacionesContables.id(cuentaId);
        var vistos = new HashSet<Long>();
        Long actual = padreId;
        while (actual != null) {
            ValidacionesContables.id(actual);
            if (actual.equals(cuentaId) || !vistos.add(actual)) throw new IllegalArgumentException("Ciclo en jerarquía");
            if (!padres.containsKey(actual)) throw new IllegalArgumentException("Ancestro inexistente");
            actual = padres.get(actual);
        }
    }
}
