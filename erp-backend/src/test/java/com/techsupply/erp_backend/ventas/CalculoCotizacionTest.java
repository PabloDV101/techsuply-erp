package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.compras.domain.model.CalculoOrden;
import com.techsupply.erp_backend.ventas.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CalculoCotizacionTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static ValoresVenta.Linea line(String q, String p, String d, String t) { return new ValoresVenta.Linea(n(q), n(p), n(d), n(t)); }
    @Test void coincideConComprasEnDescuentosYEmpatesPorLinea() {
        var lines = List.of(line("3", "10", "10", "16"), line("0.5", "0.01", "0", "100"), line("0.5", "0.01", "0", "100"));
        var result = CalculoCotizacion.calcular(lines);
        var compras = CalculoOrden.calcular(lines.stream().map(l -> new CalculoOrden.Linea(l.cantidad(), l.precioUnitario(), l.descuentoPct(), l.tasaImpuesto())).toList());
        assertThat(result.subtotal()).isEqualByComparingTo("27.02");
        assertThat(result.impuestos()).isEqualByComparingTo("4.34");
        assertThat(result.total()).isEqualByComparingTo("31.36");
        assertThat(result.subtotal()).isEqualByComparingTo(compras.subtotal());
        assertThat(result.impuestos()).isEqualByComparingTo(compras.impuestos());
        assertThat(result.total()).isEqualByComparingTo(compras.total());
        assertThatThrownBy(() -> CalculoCotizacion.calcular(List.of(line("2", "999999999999.99", "0", "0")))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoCotizacion.calcular(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void medianocheDeCiudadDeMexicoDefineLaFechaDeNegocio() {
        assertThat(VigenciaCotizacion.fechaNegocio(Instant.parse("2026-12-01T05:59:59Z"))).isEqualTo(LocalDate.of(2026, 11, 30));
        assertThat(VigenciaCotizacion.fechaNegocio(Instant.parse("2026-12-01T06:00:00Z"))).isEqualTo(LocalDate.of(2026, 12, 1));
    }
    @Test void fechaLimiteEsInclusivaYLasTransicionesRespetanEstado() {
        var fecha = LocalDate.of(2026, 11, 30);
        var quote = new Cotizacion("COT-1", 1L, 2L, Instant.parse("2026-11-01T00:00:00Z"), fecha, "key", CalculoCotizacion.calcular(List.of(line("1", "10", "0", "0"))));
        assertThatThrownBy(() -> quote.aceptar(fecha)).isInstanceOf(IllegalStateException.class);
        quote.emitir(fecha);
        assertThatThrownBy(() -> quote.aceptar(fecha.plusDays(1))).isInstanceOf(IllegalStateException.class);
        quote.aceptar(fecha);
        assertThat(quote.getEstado()).isEqualTo(EstadoCotizacion.ACEPTADA);
        assertThatThrownBy(quote::cancelar).isInstanceOf(IllegalStateException.class);
    }
}
