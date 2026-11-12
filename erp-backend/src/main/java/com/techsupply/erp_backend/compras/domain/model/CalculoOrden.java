package com.techsupply.erp_backend.compras.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import com.techsupply.erp_backend.common.domain.CalculoImportes;

/** Deterministic calculation: no persistence, clock or mutable state. */
public final class CalculoOrden {
    private static final BigDecimal CIEN = new BigDecimal("100");
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
        var value = CalculoImportes.calcular(lineas.stream().map(line -> new CalculoImportes.Linea(
            line.cantidad(), line.precioUnitario(), line.descuentoPct(), line.tasaImpuesto())).toList());
        return new Totales(value.subtotal(), value.impuestos(), value.total());
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
