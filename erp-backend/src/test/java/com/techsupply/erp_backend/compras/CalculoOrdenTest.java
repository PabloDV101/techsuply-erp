package com.techsupply.erp_backend.compras;

import com.techsupply.erp_backend.compras.domain.model.CalculoOrden;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CalculoOrdenTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static CalculoOrden.Linea line(String q, String price, String discount, String tax) {
        return new CalculoOrden.Linea(n(q), n(price), n(discount), n(tax));
    }
    @Test void calculatesDiscountAndTaxPerLineUsingHalfUp() {
        var result = CalculoOrden.calcular(List.of(line("3", "10.00", "10", "16")));
        assertThat(result.subtotal()).isEqualByComparingTo("27.00");
        assertThat(result.impuestos()).isEqualByComparingTo("4.32");
        assertThat(result.total()).isEqualByComparingTo("31.32");
        var rounding = CalculoOrden.calcular(List.of(line("0.500", "0.01", "0", "100"), line("0.500", "0.01", "0", "100")));
        assertThat(rounding.subtotal()).isEqualByComparingTo("0.02");
        assertThat(rounding.impuestos()).isEqualByComparingTo("0.02");
        assertThat(CalculoOrden.calcular(List.of(line("1", "10", "100", "100"))).total()).isEqualByComparingTo("0");
    }
    @Test void rejectsInvalidPrecisionRangesAndTotalOverflow() {
        assertThatThrownBy(() -> line("0", "1", "0", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("1.0001", "1", "0", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("1", "-1", "0", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> line("1", "1", "101", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoOrden.calcular(List.of(line("2", "999999999999.99", "0", "0")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoOrden.calcular(List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(CalculoOrden.calcular(List.of(line("1", "999999999999.99", "0", "0"))).total()).isEqualByComparingTo("999999999999.99");
    }
    @Test void remainingUsesAcceptedUnitsAndRejectsExcess() {
        assertThat(CalculoOrden.pendiente(n("20.000"), n("18.000"))).isEqualByComparingTo("2.000");
        assertThatThrownBy(() -> CalculoOrden.pendiente(n("20"), n("21"))).isInstanceOf(IllegalArgumentException.class);
    }
}
