package com.techsupply.erp_backend.inventario.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Pure weighted average of physical stock, independent of reservations. */
public final class ValoracionInventario {
    private static final BigDecimal MAX_CANTIDAD = new BigDecimal("99999999999.999");
    private static final BigDecimal MAX_COSTO = new BigDecimal("9999999999.9999");
    private ValoracionInventario() {}
    public record Resultado(BigDecimal cantidadFisica, BigDecimal costoPromedio) {}
    public static Resultado entrada(BigDecimal fisica, BigDecimal promedio, BigDecimal cantidad, BigDecimal costo) {
        Objects.requireNonNull(fisica); Objects.requireNonNull(promedio); Objects.requireNonNull(cantidad); Objects.requireNonNull(costo);
        if (fisica.signum() < 0 || promedio.signum() < 0 || cantidad.signum() <= 0 || costo.signum() < 0
                || costo.compareTo(MAX_COSTO) > 0 || promedio.compareTo(MAX_COSTO) > 0)
            throw new IllegalArgumentException("Cantidades o costos inválidos");
        BigDecimal total = fisica.add(cantidad);
        if (total.compareTo(MAX_CANTIDAD) > 0) throw new IllegalArgumentException("Existencia excede numeric(14,3)");
        BigDecimal result = fisica.multiply(promedio).add(cantidad.multiply(costo)).divide(total, 4, RoundingMode.HALF_UP);
        if (result.compareTo(MAX_COSTO) > 0) throw new IllegalArgumentException("Costo excede numeric(14,4)");
        return new Resultado(total, result);
    }
}
