package com.techsupply.erp_backend.common.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** Deterministic per-line discount and tax calculation shared by approved sales and purchase rules. */
public final class CalculoImportes {
    private static final BigDecimal MAXIMO = new BigDecimal("999999999999.99");

    private CalculoImportes() {}

    public record Linea(BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuentoPct,
                        BigDecimal tasaImpuesto) {
        public Linea {
            Objects.requireNonNull(cantidad, "cantidad");
            Objects.requireNonNull(precioUnitario, "precioUnitario");
            Objects.requireNonNull(descuentoPct, "descuentoPct");
            Objects.requireNonNull(tasaImpuesto, "tasaImpuesto");
        }
    }

    public record Totales(BigDecimal subtotal, BigDecimal impuestos, BigDecimal total) {}

    /** Rounds each line's base and tax to two decimals with HALF_UP, then sums those rounded values. */
    public static Totales calcular(List<Linea> lineas) {
        Objects.requireNonNull(lineas, "lineas");
        if (lineas.isEmpty()) throw new IllegalArgumentException("Se requiere al menos una línea");
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        BigDecimal impuestos = BigDecimal.ZERO.setScale(2);
        for (Linea linea : lineas) {
            Objects.requireNonNull(linea, "linea");
            BigDecimal bruto = linea.cantidad().multiply(linea.precioUnitario());
            BigDecimal base = bruto.subtract(bruto.multiply(linea.descuentoPct()).movePointLeft(2));
            subtotal = subtotal.add(base.setScale(2, RoundingMode.HALF_UP));
            impuestos = impuestos.add(base.multiply(linea.tasaImpuesto()).movePointLeft(2)
                .setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal total = subtotal.add(impuestos);
        if (subtotal.compareTo(MAXIMO) > 0 || impuestos.compareTo(MAXIMO) > 0 || total.compareTo(MAXIMO) > 0)
            throw new IllegalArgumentException("Importe excede numeric(14,2)");
        return new Totales(subtotal, impuestos, total);
    }
}
