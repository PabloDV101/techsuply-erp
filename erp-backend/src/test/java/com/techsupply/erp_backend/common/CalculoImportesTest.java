package com.techsupply.erp_backend.common;

import com.techsupply.erp_backend.common.domain.CalculoImportes;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CalculoImportesTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }

    @Test void aplicaDescuentoImpuestoYRedondeoHalfUpPorLinea() {
        var values = List.of(
            new CalculoImportes.Linea(n("3"), n("10.00"), n("10"), n("16")),
            new CalculoImportes.Linea(n("0.500"), n("0.01"), n("0"), n("100")),
            new CalculoImportes.Linea(n("0.500"), n("0.01"), n("0"), n("100")));

        var totals = CalculoImportes.calcular(values);

        assertThat(totals.subtotal()).isEqualByComparingTo("27.02");
        assertThat(totals.impuestos()).isEqualByComparingTo("4.34");
        assertThat(totals.total()).isEqualByComparingTo("31.36");
    }

    @Test void rechazaListaNulaVaciaYDesbordamientoDelEsquema() {
        assertThatThrownBy(() -> CalculoImportes.calcular(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> CalculoImportes.calcular(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoImportes.calcular(List.of(
            new CalculoImportes.Linea(n("2"), n("999999999999.99"), n("0"), n("0")))))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
