package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.maestros.domain.model.Cliente;
import com.techsupply.erp_backend.ventas.domain.model.ValoresVenta;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ValoresVentaTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }

    @Test void validaYNormalizaLineaSinRedondear() {
        var linea = new ValoresVenta.Linea(n("0.5000"), n("12.500"), n("100"), n("16"));
        assertThat(linea.cantidad()).isEqualTo(n("0.500"));
        assertThat(linea.precioUnitario()).isEqualTo(n("12.50"));
        assertThat(linea.descuentoPct()).isEqualTo(n("100.00"));
        assertThat(new ValoresVenta.Linea(n("99999999999.999"), n("999999999999.99"), n("0"), n("0"))).isNotNull();
        assertThatThrownBy(() -> new ValoresVenta.Linea(n("0"), n("1"), n("0"), n("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValoresVenta.Linea(n("1.0001"), n("1"), n("0"), n("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValoresVenta.Linea(n("1"), n("1.001"), n("0"), n("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValoresVenta.Linea(n("1"), n("1"), n("100.01"), n("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValoresVenta.Linea(n("100000000000"), n("1"), n("0"), n("0"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new ValoresVenta.Linea(n("1"), n("-0.01"), n("0"), n("0"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void pendienteYReservaRespetanLoDespachado() {
        assertThat(ValoresVenta.pendiente(n("10"), n("4"))).isEqualTo(n("6.000"));
        assertThatCode(() -> ValoresVenta.validarReserva(n("10"), n("6"), n("4"))).doesNotThrowAnyException();
        assertThatThrownBy(() -> ValoresVenta.validarReserva(n("10"), n("6.001"), n("4"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValoresVenta.pendiente(n("10"), n("10.001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ValoresVenta.validarReserva(n("10"), n("-1"), n("0"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void clienteInicialTieneDatosCanonicosSinLimiteDeCredito() {
        var cliente = new Cliente(" cli-1 ", " Cliente ");
        assertThat(cliente.getCodigo()).isEqualTo("CLI-1");
        assertThat(cliente.getNombreRazonSocial()).isEqualTo("Cliente");
        assertThat(cliente.getLimiteCredito()).isEqualByComparingTo("0");
        assertThat(cliente.getPlazoCreditoDias()).isEqualTo((short) 30);
        assertThat(cliente.isActivo()).isTrue();
        assertThatThrownBy(() -> new Cliente(" ", "Cliente")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Cliente("CLI", " ")).isInstanceOf(IllegalArgumentException.class);
    }
}
