package com.techsupply.erp_backend.inventario.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Deterministic decimal validation and count difference, without persistence or clock. */
public final class CalculoConteo {
    private static final BigDecimal MAX_CANTIDAD = new BigDecimal("99999999999.999");
    private static final BigDecimal MAX_COSTO = new BigDecimal("999999999999.9999");
    private CalculoConteo() {}

    public static BigDecimal cantidad(BigDecimal valor) { return decimal(valor, MAX_CANTIDAD, 3, false); }
    public static BigDecimal cantidadPositiva(BigDecimal valor) { return decimal(valor, MAX_CANTIDAD, 3, true); }
    public static BigDecimal costo(BigDecimal valor) { return decimal(valor, MAX_COSTO, 4, false); }
    public static BigDecimal diferencia(BigDecimal sistema, BigDecimal contada) {
        return cantidad(contada).subtract(cantidad(sistema));
    }
    private static BigDecimal decimal(BigDecimal valor, BigDecimal maximo, int escala, boolean positivo) {
        Objects.requireNonNull(valor, "Valor requerido");
        if (valor.signum() < 0 || (positivo && valor.signum() == 0) || valor.compareTo(maximo) > 0)
            throw new IllegalArgumentException("Valor fuera del rango permitido");
        try { return valor.setScale(escala, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Precisión decimal inválida", ex); }
    }
}
