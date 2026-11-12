package com.techsupply.erp_backend.compras.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

/** Deterministic calculation: no persistence, clock or mutable state. */
public final class CalculoOrden {
    private static final BigDecimal CIEN = new BigDecimal("100");
    private static final BigDecimal MAX_TOTAL = new BigDecimal("999999999999.99");
    private CalculoOrden() {}

    public record Linea(BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuentoPct, BigDecimal tasaImpuesto) {
        public Linea {
            validar(cantidad, 11, 3, true, null);
            validar(precioUnitario, 12, 2, false, null);
            validar(descuentoPct, 3, 2, false, CIEN);
            validar(tasaImpuesto, 3, 2, false, CIEN);
        }
    }
    public record Totales(BigDecimal subtotal, BigDecimal impuestos, BigDecimal total) {}

    public static Totales calcular(List<Linea> lineas) {
        Objects.requireNonNull(lineas, "lineas");
        if (lineas.isEmpty()) throw new IllegalArgumentException("La orden debe tener líneas");
        BigDecimal subtotal = BigDecimal.ZERO.setScale(2);
        BigDecimal impuestos = BigDecimal.ZERO.setScale(2);
        for (Linea linea : lineas) {
            Objects.requireNonNull(linea, "linea");
            BigDecimal bruto = linea.cantidad().multiply(linea.precioUnitario());
            BigDecimal base = bruto.subtract(bruto.multiply(linea.descuentoPct()).movePointLeft(2));
            subtotal = subtotal.add(base.setScale(2, RoundingMode.HALF_UP));
            impuestos = impuestos.add(base.multiply(linea.tasaImpuesto()).movePointLeft(2).setScale(2, RoundingMode.HALF_UP));
        }
        BigDecimal total = subtotal.add(impuestos);
        if (subtotal.compareTo(MAX_TOTAL) > 0 || impuestos.compareTo(MAX_TOTAL) > 0 || total.compareTo(MAX_TOTAL) > 0)
            throw new IllegalArgumentException("Importe excede numeric(14,2)");
        return new Totales(subtotal, impuestos, total);
    }

    public static BigDecimal pendiente(BigDecimal ordenada, BigDecimal aceptada) {
        validar(ordenada, 11, 3, true, null);
        validar(aceptada, 11, 3, false, null);
        if (aceptada.compareTo(ordenada) > 0) throw new IllegalArgumentException("Aceptada excede ordenada");
        return ordenada.subtract(aceptada);
    }

    private static void validar(BigDecimal valor, int enteros, int decimales, boolean positivo, BigDecimal maximo) {
        Objects.requireNonNull(valor, "valor");
        BigDecimal normalizado = valor.stripTrailingZeros();
        int fraccion = Math.max(normalizado.scale(), 0);
        int entero = Math.max(normalizado.precision() - normalizado.scale(), 0);
        if (valor.signum() < (positivo ? 1 : 0) || entero > enteros || fraccion > decimales
                || (maximo != null && valor.compareTo(maximo) > 0))
            throw new IllegalArgumentException("Valor fuera de los límites del esquema");
    }
}
