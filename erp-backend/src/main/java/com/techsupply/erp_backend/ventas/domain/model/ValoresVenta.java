package com.techsupply.erp_backend.ventas.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Exact bounds shared by sales value objects; no tax, price or rounding policy. */
public final class ValoresVenta {
    private static final BigDecimal MAX_CANTIDAD = new BigDecimal("99999999999.999");
    private static final BigDecimal MAX_PRECIO = new BigDecimal("999999999999.99");
    private static final BigDecimal MAX_PORCENTAJE = new BigDecimal("100");
    private ValoresVenta() {}
    private static BigDecimal decimal(BigDecimal value, BigDecimal max, int scale, boolean positive) {
        Objects.requireNonNull(value, "valor requerido");
        if (value.signum() < 0 || (positive && value.signum() == 0) || value.compareTo(max) > 0)
            throw new IllegalArgumentException("Valor fuera de rango");
        try { return value.setScale(scale, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Precisión inválida", ex); }
    }
    public static BigDecimal cantidad(BigDecimal value) { return decimal(value, MAX_CANTIDAD, 3, false); }
    public static BigDecimal cantidadPositiva(BigDecimal value) { return decimal(value, MAX_CANTIDAD, 3, true); }
    public static BigDecimal precio(BigDecimal value) { return decimal(value, MAX_PRECIO, 2, false); }
    public static BigDecimal porcentaje(BigDecimal value) { return decimal(value, MAX_PORCENTAJE, 2, false); }

    public record Linea(BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuentoPct, BigDecimal tasaImpuesto) {
        public Linea {
            cantidad = cantidadPositiva(cantidad); precioUnitario = precio(precioUnitario);
            descuentoPct = porcentaje(descuentoPct); tasaImpuesto = porcentaje(tasaImpuesto);
        }
    }
    /** Pending includes reserved and unreserved quantities and never implies a reservation. */
    public static BigDecimal pendiente(BigDecimal cantidad, BigDecimal despachada) {
        BigDecimal total = cantidadPositiva(cantidad), sent = cantidad(despachada);
        if (sent.compareTo(total) > 0) throw new IllegalArgumentException("Despachada excede cantidad");
        return total.subtract(sent);
    }
    public static void validarReserva(BigDecimal cantidad, BigDecimal reservada, BigDecimal despachada) {
        BigDecimal pending = pendiente(cantidad, despachada);
        if (cantidad(reservada).compareTo(pending) > 0) throw new IllegalArgumentException("Reserva excede pendiente");
    }
}
