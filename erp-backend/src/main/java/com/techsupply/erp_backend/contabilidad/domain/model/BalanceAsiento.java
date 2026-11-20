package com.techsupply.erp_backend.contabilidad.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/** Comprobación aritmética; no contabiliza ni comprueba periodo, cuentas o permisos. */
public final class BalanceAsiento {
    private BalanceAsiento() {}

    public static Totales calcular(List<ImporteContable.Partida> partidas) {
        var copia = List.copyOf(Objects.requireNonNull(partidas, "Partidas requeridas"));
        BigDecimal debe = BigDecimal.ZERO;
        BigDecimal haber = BigDecimal.ZERO;
        for (var partida : copia) {
            debe = debe.add(partida.debe());
            haber = haber.add(partida.haber());
        }
        return new Totales(debe, haber, copia.size());
    }

    public record Totales(BigDecimal debe, BigDecimal haber, int numeroPartidas) {
        public boolean equilibrado() { return numeroPartidas >= 2 && debe.compareTo(haber) == 0; }
    }
}
