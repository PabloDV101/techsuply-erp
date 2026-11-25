package com.techsupply.erp_backend.reporteria;

import com.techsupply.erp_backend.reporteria.application.IndicadorDashboard;
import com.techsupply.erp_backend.reporteria.application.PrepararDashboardService;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import com.techsupply.erp_backend.security.infrastructure.security.SecurityActor;
import java.time.LocalDate;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class PrepararDashboardIT extends PostgresIntegrationSupport {
    @Autowired PrepararDashboardService service;
    private final LocalDate inicio = LocalDate.of(2026, 11, 1);
    private final LocalDate fin = LocalDate.of(2026, 11, 30);

    private void authenticate(String... permissions) {
        var authorities = Arrays.stream(permissions).map(SimpleGrantedAuthority::new).toList();
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(new SecurityActor(1L, "prueba"), null, authorities));
    }

    @AfterEach void clearContext() { SecurityContextHolder.clearContext(); }

    @Test void filtraCadaDominioPorSuAutoridadYNoInfiereAccesoFinanciero() {
        for (var indicator : IndicadorDashboard.values()) {
            authenticate(indicator.permiso());
            var criteria = service.preparar(inicio, fin, 9L);
            assertThat(criteria.indicadores()).containsExactly(indicator);
            assertThat(criteria.from()).isEqualTo(inicio);
            assertThat(criteria.to()).isEqualTo(fin);
            assertThat(criteria.periodId()).isEqualTo(9L);
            assertThatThrownBy(() -> criteria.indicadores().clear()).isInstanceOf(UnsupportedOperationException.class);
        }
        authenticate("VENTAS_FACTURAS_LEER", "INVENTARIO_ALERTAS_LEER");
        assertThat(service.preparar(inicio, fin, null).indicadores()).containsExactlyInAnyOrder(
            IndicadorDashboard.VENTAS_FACTURADO_SIN_IMPUESTOS, IndicadorDashboard.INVENTARIO_PRODUCTOS_ALERTA);
    }

    @Test void deniegaSinPermisoAntesDeValidarCriterios() {
        authenticate("CONTABILIDAD_ASIENTOS_LEER");
        assertThatThrownBy(() -> service.preparar(null, null, -1L)).isInstanceOf(AccessDeniedException.class);
        SecurityContextHolder.clearContext();
        assertThatThrownBy(() -> service.preparar(inicio, fin, null))
            .isInstanceOf(org.springframework.security.core.AuthenticationException.class);
    }

    @Test void validaFechasInclusivasYPeriodoOpcionalPositivo() {
        authenticate("VENTAS_FACTURAS_LEER");
        assertThat(service.preparar(inicio, inicio, null).from()).isEqualTo(inicio);
        assertBadRequest(null, fin, null);
        assertBadRequest(inicio, null, null);
        assertBadRequest(fin, inicio, null);
        assertBadRequest(inicio, fin, 0L);
        assertBadRequest(inicio, fin, -1L);
    }

    private void assertBadRequest(LocalDate from, LocalDate to, Long periodId) {
        assertThatThrownBy(() -> service.preparar(from, to, periodId))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                exception -> assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
