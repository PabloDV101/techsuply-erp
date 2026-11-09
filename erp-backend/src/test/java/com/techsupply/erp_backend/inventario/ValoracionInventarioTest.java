package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.domain.model.ValoracionInventario;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ValoracionInventarioTest {
    private static BigDecimal n(String x) { return new BigDecimal(x); }
    @Test void weightedCostAndRounding() {
        var first = ValoracionInventario.entrada(n("0"), n("0"), n("10"), n("12.50"));
        assertThat(first.cantidadFisica()).isEqualByComparingTo("10");
        assertThat(first.costoPromedio()).isEqualByComparingTo("12.5000");
        var next = ValoracionInventario.entrada(n("10"), n("12.5000"), n("2"), n("20.00"));
        assertThat(next.cantidadFisica()).isEqualByComparingTo("12");
        assertThat(next.costoPromedio()).isEqualByComparingTo("13.7500");
        assertThat(ValoracionInventario.entrada(n("1"), n("0"), n("1"), n("0.0001")).costoPromedio()).isEqualByComparingTo("0.0001");
    }
    @Test void invalidAndOverflowInputsFail() {
        assertThatThrownBy(() -> ValoracionInventario.entrada(n("0"), n("0"), n("0"), n("1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValoracionInventario.entrada(n("99999999999.999"), n("0"), n("1"), n("1"))).isInstanceOf(IllegalArgumentException.class);
        assertThat(ValoracionInventario.entrada(n("0"), n("0"), n("1"), n("999999999999.99")).costoPromedio()).isEqualByComparingTo("999999999999.9900");
        assertThatThrownBy(() -> ValoracionInventario.entrada(n("0"), n("0"), n("1"), n("1000000000000"))).isInstanceOf(IllegalArgumentException.class);
    }
}
