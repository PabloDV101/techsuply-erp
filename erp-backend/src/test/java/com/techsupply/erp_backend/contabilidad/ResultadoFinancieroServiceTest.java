package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.contabilidad.application.EstadosFinancierosService;
import com.techsupply.erp_backend.contabilidad.application.ResultadoFinancieroService;
import com.techsupply.erp_backend.contabilidad.application.dto.EstadoFinancieroResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResultadoFinancieroServiceTest {
    private final EstadosFinancierosService statements=mock(EstadosFinancierosService.class);
    private final ResultadoFinancieroService service=new ResultadoFinancieroService(statements);
    private final LocalDate from=LocalDate.of(2026,1,1),to=LocalDate.of(2026,1,31);
    @Test void devuelveResultadoDelEstadoSinRecalcular() {
        when(statements.resultados(1L)).thenReturn(new EstadoFinancieroResponse(1L,from,to,"DISPONIBLE",null,List.of(
            new EstadoFinancieroResponse.Rubro("INGRESOS",new BigDecimal("150")),new EstadoFinancieroResponse.Rubro("RESULTADO",new BigDecimal("-12.50")))));
        var result=service.consultar(1L);
        assertThat(result.periodoId()).isEqualTo(1L); assertThat(result.inicio()).isEqualTo(from);
        assertThat(result.fin()).isEqualTo(to); assertThat(result.resultado()).isEqualByComparingTo("-12.50");
    }
    @Test void periodoInvalidoNoConsultaYEstadoIncompletoNoInventaResultado() {
        for(Long period:java.util.Arrays.asList(null,0L,-1L))
            assertThatThrownBy(()->service.consultar(period)).isInstanceOfSatisfying(ResponseStatusException.class,e->assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verifyNoInteractions(statements);
        when(statements.resultados(2L)).thenReturn(new EstadoFinancieroResponse(2L,from,to,"NO_DISPONIBLE",null,List.of()));
        when(statements.resultados(3L)).thenReturn(new EstadoFinancieroResponse(3L,from,to,"DISPONIBLE",null,List.of()));
        assertThatThrownBy(()->service.consultar(2L)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(()->service.consultar(3L)).isInstanceOf(IllegalStateException.class);
    }
}
