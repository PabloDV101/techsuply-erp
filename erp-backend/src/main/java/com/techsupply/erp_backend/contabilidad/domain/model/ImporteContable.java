package com.techsupply.erp_backend.contabilidad.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/** Límites de V14: no redondea ni acepta escala superior a dos, aun si son ceros. */
public final class ImporteContable {
    private static final BigDecimal MAXIMO = new BigDecimal("999999999999.99");
    private ImporteContable() {}

    public static BigDecimal validar(BigDecimal valor) {
        Objects.requireNonNull(valor, "Importe requerido");
        if (valor.signum() < 0 || valor.compareTo(MAXIMO) > 0 || valor.scale() > 2)
            throw new IllegalArgumentException("Importe fuera de rango o precisión inválida");
        return valor.setScale(2, RoundingMode.UNNECESSARY);
    }

    public record Partida(BigDecimal debe, BigDecimal haber) {
        public Partida {
            debe = validar(debe);
            haber = validar(haber);
            if ((debe.signum() > 0) == (haber.signum() > 0))
                throw new IllegalArgumentException("Una partida requiere un único lado positivo");
        }
    }
}
