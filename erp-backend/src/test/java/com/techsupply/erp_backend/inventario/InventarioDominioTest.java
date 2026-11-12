package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.domain.model.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class InventarioDominioTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static ConteoInventario conteo() { return new ConteoInventario("CN-1", 1L, 2L, LocalDate.of(2026, 11, 10), ClaseConteo.CICLICO); }
    private static AjusteInventario ajuste() { return new AjusteInventario("AJ-1", 1L, "Diferencia", 2L, "key", Instant.parse("2026-11-10T10:00:00Z")); }
    @Test void diferenciaDecimalYLimites() {
        assertThat(CalculoConteo.diferencia(n("20"), n("18"))).isEqualByComparingTo("-2.000");
        assertThat(CalculoConteo.diferencia(n("0"), n("99999999999.999"))).isEqualByComparingTo("99999999999.999");
        assertThat(CalculoConteo.costo(n("999999999999.9999"))).isEqualByComparingTo("999999999999.9999");
        assertThatThrownBy(() -> CalculoConteo.cantidad(n("0.0001"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoConteo.cantidad(n("100000000000"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoConteo.cantidad(n("-1"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CalculoConteo.costo(n("1000000000000"))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void conteoValidaReferenciaRecapturaYCierre() {
        ConteoInventario count = conteo();
        assertThatThrownBy(count::cerrar).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> count.capturar(3L, n("1"), n("2"), false, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> count.capturar(3L, n("0"), n("2"), true, null)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count.getEstado()).isEqualTo(EstadoConteo.PROGRAMADO);
        count.capturar(3L, n("0"), n("2"), false, null);
        count.capturar(3L, n("4"), n("3"), true, 7);
        assertThat(count.getDetalles()).hasSize(1);
        assertThat(count.getDetalles().getFirst().getDiferencia()).isEqualByComparingTo("-1");
        assertThat(count.getDetalles().getFirst().getVersionExistencia()).isEqualTo(7);
        assertThatThrownBy(() -> count.capturar(3L, n("4"), n("3.0001"), true, 8)).isInstanceOf(IllegalArgumentException.class);
        assertThat(count.getDetalles().getFirst().getVersionExistencia()).isEqualTo(7);
        count.cerrar();
        assertThatThrownBy(() -> count.capturar(3L, n("0"), n("0"), false, null)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(count::cancelar).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> count.getDetalles().clear()).isInstanceOf(UnsupportedOperationException.class);
        ConteoInventario canceled = conteo(); canceled.cancelar();
        assertThatThrownBy(() -> canceled.capturar(1L, n("0"), n("0"), false, null)).isInstanceOf(IllegalStateException.class);
    }
    @Test void ajusteControlaEstadosActoresYValores() {
        AjusteInventario adjustment = ajuste(); Instant now = Instant.parse("2026-11-10T11:00:00Z");
        assertThatThrownBy(() -> adjustment.aprobar(3L, now)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adjustment.agregarDetalle(3L, TipoAjuste.POSITIVO, n("1"), null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adjustment.agregarDetalle(3L, TipoAjuste.NEGATIVO, n("1"), n("10"), null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adjustment.agregarDetalle(3L, TipoAjuste.NEGATIVO, n("0"), null, null, null)).isInstanceOf(IllegalArgumentException.class);
        adjustment.agregarDetalle(3L, TipoAjuste.POSITIVO, n("1"), n("999999999999.9999"), null, null);
        assertThatThrownBy(() -> adjustment.agregarDetalle(3L, TipoAjuste.NEGATIVO, n("1"), null, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> adjustment.aprobar(2L, now)).isInstanceOf(IllegalArgumentException.class);
        adjustment.aprobar(3L, now);
        assertThat(adjustment.getEstado()).isEqualTo(EstadoAjuste.APROBADO);
        assertThat(adjustment.getAutorizadoPorUsuarioId()).isEqualTo(3L);
        assertThatThrownBy(() -> adjustment.rechazar(3L, now, "No")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> adjustment.agregarDetalle(4L, TipoAjuste.NEGATIVO, n("1"), null, null, null)).isInstanceOf(IllegalStateException.class);
        AjusteInventario rejected = ajuste(); rejected.agregarDetalle(4L, TipoAjuste.NEGATIVO, n("2"), null, null, null);
        assertThatThrownBy(() -> rejected.rechazar(3L, now, " ")).isInstanceOf(IllegalArgumentException.class);
        rejected.rechazar(3L, now, "Recontar");
        assertThat(rejected.getEstado()).isEqualTo(EstadoAjuste.RECHAZADO);
        assertThat(rejected.getMotivoRechazo()).isEqualTo("Recontar");
    }
}
