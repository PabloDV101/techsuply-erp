package com.techsupply.erp_backend.ventas.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

/** Conversión determinista de instante a fecha de negocio de TechSupply. */
public final class VigenciaCotizacion {
    private static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    private VigenciaCotizacion() {}
    public static LocalDate fechaNegocio(Instant instante) { return LocalDate.ofInstant(Objects.requireNonNull(instante), ZONA); }
    public static boolean vigente(LocalDate limite, LocalDate fecha) {
        return !Objects.requireNonNull(fecha).isAfter(Objects.requireNonNull(limite));
    }
}
