package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.contabilidad.domain.model.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ContabilidadDominioTest {
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static ImporteContable.Partida linea(String debe, String haber) {
        return new ImporteContable.Partida(n(debe), n(haber));
    }

    @Test void importesRechazanRedondeoExcesoYLineasSinUnicoLadoPositivo() {
        assertThat(ImporteContable.validar(n("999999999999.99"))).isEqualByComparingTo("999999999999.99");
        assertThat(ImporteContable.validar(n("1"))).isEqualTo(n("1.00"));
        for (String invalid : List.of("1.000", "0.001", "-0.01", "1000000000000.00"))
            assertThatThrownBy(() -> ImporteContable.validar(n(invalid))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> linea("0", "0")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> linea("1", "1")).isInstanceOf(IllegalArgumentException.class);
        assertThatNullPointerException().isThrownBy(() -> ImporteContable.validar(null));
    }

    @Test void balanceExactoRequiereDosPartidasYSumaSinDesbordar() {
        var balanced = BalanceAsiento.calcular(List.of(linea("999999999999.99", "0"), linea("1", "0"), linea("0", "999999999999.99"), linea("0", "1")));
        assertThat(balanced.equilibrado()).isTrue();
        assertThat(balanced.debe()).isEqualByComparingTo("1000000000000.99");
        assertThat(BalanceAsiento.calcular(List.of(linea("100", "0"), linea("0", "99.99"))).equilibrado()).isFalse();
        assertThat(BalanceAsiento.calcular(List.of(linea("100", "0"))).equilibrado()).isFalse();
        assertThat(BalanceAsiento.calcular(List.of()).equilibrado()).isFalse();
        assertThatNullPointerException().isThrownBy(() -> BalanceAsiento.calcular(null));
    }

    @Test void jerarquiaDetectaCiclosDirectosIndirectosYAncestrosFaltantes() {
        var padres = new HashMap<Long, Long>();
        padres.put(1L, null); padres.put(2L, 1L); padres.put(3L, 2L);
        JerarquiaCuentas.validarPadre(4L, 3L, padres);
        JerarquiaCuentas.validarPadre(null, null, padres);
        assertThatThrownBy(() -> JerarquiaCuentas.validarPadre(1L, 3L, padres)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JerarquiaCuentas.validarPadre(2L, 2L, padres)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> JerarquiaCuentas.validarPadre(4L, 99L, padres)).isInstanceOf(IllegalArgumentException.class);
        padres.put(1L, 3L);
        assertThatThrownBy(() -> JerarquiaCuentas.validarPadre(4L, 3L, padres)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void periodoIncluyeExtremosYRechazaRangoInvertido() {
        var inicio = LocalDate.of(2026, 11, 1); var fin = LocalDate.of(2026, 11, 30);
        var periodo = new PeriodoContable(inicio, fin);
        assertThat(periodo.getEstado()).isEqualTo(EstadoPeriodoContable.ABIERTO);
        assertThat(periodo.contiene(inicio)).isTrue();
        assertThat(periodo.contiene(fin)).isTrue();
        assertThat(periodo.contiene(fin.plusDays(1))).isFalse();
        assertThat(periodo.contiene(inicio.minusDays(1))).isFalse();
        assertThatThrownBy(() -> new PeriodoContable(fin, inicio)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void cierreDeDominioConservaActorYNoPermiteUnaSegundaTransicion() {
        var periodo = new PeriodoContable(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));
        var instante = Instant.parse("2026-02-01T12:00:00Z");
        assertThatThrownBy(() -> periodo.cerrar(0L, instante)).isInstanceOf(IllegalArgumentException.class);
        assertThat(periodo.getEstado()).isEqualTo(EstadoPeriodoContable.ABIERTO);
        periodo.cerrar(3L, instante);
        assertThat(periodo.getEstado()).isEqualTo(EstadoPeriodoContable.CERRADO);
        assertThat(periodo.getCerradoPorUsuarioId()).isEqualTo(3L);
        assertThat(periodo.getCerradoEn()).isEqualTo(instante);
        assertThatThrownBy(() -> periodo.cerrar(4L, instante)).isInstanceOf(IllegalStateException.class);
    }

    @Test void constructoresInicialesSoloPermitenDatosEstructuralesValidos() {
        var cuenta = new CuentaContable(" A ", " Caja ", TipoCuenta.ACTIVO, NaturalezaCuenta.DEUDORA, (short) 1, null, true);
        assertThat(cuenta.getCodigo()).isEqualTo("A");
        assertThat(cuenta.isActiva()).isTrue();
        assertThatThrownBy(() -> new CuentaContable("A", "Caja", TipoCuenta.ACTIVO, NaturalezaCuenta.DEUDORA, (short) 0, null, true)).isInstanceOf(IllegalArgumentException.class);
        var asiento = new AsientoContable("F", 1L, LocalDate.of(2026, 11, 1), "Manual", "K", 2L);
        assertThat(asiento.getEstado()).isEqualTo(EstadoAsientoContable.BORRADOR);
        assertThat(asiento.getOrigenModulo()).isEqualTo("MANUAL");
        assertThat(asiento.getOrigenId()).isNull();
        assertThatThrownBy(() -> new PartidaAsiento(1L, (short) 0, 2L, n("1"), n("0"), null)).isInstanceOf(IllegalArgumentException.class);
    }
}
