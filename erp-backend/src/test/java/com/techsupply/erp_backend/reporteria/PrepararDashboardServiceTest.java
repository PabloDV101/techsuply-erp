package com.techsupply.erp_backend.reporteria;

import com.techsupply.erp_backend.reporteria.application.IndicadorDashboard;
import com.techsupply.erp_backend.reporteria.application.PrepararDashboardService;
import com.techsupply.erp_backend.security.application.AuthenticatedActor;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrepararDashboardServiceTest {
    private final AuthenticatedUserProvider users=mock(AuthenticatedUserProvider.class);
    private final PrepararDashboardService service=new PrepararDashboardService(users);
    private final LocalDate from=LocalDate.of(2026,11,1), to=LocalDate.of(2026,11,30);
    private void actor(String... permissions) {
        when(users.currentUser()).thenReturn(Optional.of(new AuthenticatedActor(1L,"operador",Set.of(permissions))));
    }
    @Test void seleccionaUnicamenteLosPermisosDeFuente() {
        actor("VENTAS_FACTURAS_LEER","INVENTARIO_ALERTAS_LEER","CONTABILIDAD_ASIENTOS_LEER");
        var criteria=service.preparar(from,to,5L);
        assertThat(criteria.indicadores()).containsExactlyInAnyOrder(IndicadorDashboard.VENTAS_FACTURADO_SIN_IMPUESTOS,IndicadorDashboard.INVENTARIO_PRODUCTOS_ALERTA);
        assertThatThrownBy(()->criteria.indicadores().clear()).isInstanceOf(UnsupportedOperationException.class);
    }
    @Test void rechazaSinActorOSinPermisoAntesDeConsultarCriterios() {
        when(users.currentUser()).thenReturn(Optional.empty());
        assertThatThrownBy(()->service.preparar(from,to,null)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        actor("CONTABILIDAD_ASIENTOS_LEER");
        assertThatThrownBy(()->service.preparar(null,null,-1L)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void validaFechasInclusivasYPeriodoPositivo() {
        actor("CONTABILIDAD_REPORTES_LEER");
        assertThat(service.preparar(from,from,null).indicadores()).containsExactly(IndicadorDashboard.FINANZAS_RESULTADO);
        for(var invalid:java.util.List.of(new Object[]{null,to,null},new Object[]{from,null,null},new Object[]{to,from,null},new Object[]{from,to,0L}))
            assertThatThrownBy(()->service.preparar((LocalDate)invalid[0],(LocalDate)invalid[1],(Long)invalid[2]))
                .isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }
}
