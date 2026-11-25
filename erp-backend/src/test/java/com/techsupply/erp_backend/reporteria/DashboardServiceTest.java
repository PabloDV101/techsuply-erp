package com.techsupply.erp_backend.reporteria;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.compras.application.FacturacionProveedorReportService;
import com.techsupply.erp_backend.compras.application.dto.FacturacionProveedorReportResponse;
import com.techsupply.erp_backend.contabilidad.application.ResultadoFinancieroService;
import com.techsupply.erp_backend.contabilidad.application.dto.ResultadoFinancieroResponse;
import com.techsupply.erp_backend.inventario.application.AlertaStockService;
import com.techsupply.erp_backend.inventario.application.dto.ResumenAlertaStockResponse;
import com.techsupply.erp_backend.reporteria.application.*;
import com.techsupply.erp_backend.reporteria.application.dto.*;
import com.techsupply.erp_backend.ventas.application.FacturacionVentaReportService;
import com.techsupply.erp_backend.ventas.application.dto.FacturacionVentaReportResponse;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DashboardServiceTest {
    private final PrepararDashboardService prepare=mock(PrepararDashboardService.class);
    private final FacturacionProveedorReportService purchases=mock(FacturacionProveedorReportService.class);
    private final FacturacionVentaReportService sales=mock(FacturacionVentaReportService.class);
    private final AlertaStockService inventory=mock(AlertaStockService.class);
    private final ResultadoFinancieroService finance=mock(ResultadoFinancieroService.class);
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final DashboardService service=new DashboardService(prepare,purchases,sales,inventory,finance,db);
    private final LocalDate from=LocalDate.of(2026,11,1), to=LocalDate.of(2026,11,30);
    private final Instant snapshot=Instant.parse("2026-11-24T12:00:00Z");

    @Test void consultaSoloFuentesAutorizadasYNoInterpretaPeriodoAusente() {
        when(prepare.preparar(from,to,null)).thenReturn(new CriteriosDashboard(from,to,null,Set.of(IndicadorDashboard.VENTAS_FACTURADO_SIN_IMPUESTOS,IndicadorDashboard.FINANZAS_RESULTADO)));
        when(db.queryForObject("select statement_timestamp()",Timestamp.class)).thenReturn(Timestamp.from(snapshot));
        when(sales.consultar(from,to)).thenReturn(new FacturacionVentaReportResponse(from,to,new BigDecimal("27.25")));
        var items=service.consultar(from,to,null).indicators();
        assertThat(items).extracting(IndicadorDashboardResponse::code).containsExactly(IndicadorDashboard.VENTAS_FACTURADO_SIN_IMPUESTOS,IndicadorDashboard.FINANZAS_RESULTADO);
        assertThat(items.get(0).value()).isEqualByComparingTo("27.25");
        assertThat(items.get(1).availability()).isEqualTo("NO_DISPONIBLE");
        assertThat(items.get(1).value()).isNull();
        verifyNoInteractions(purchases,inventory,finance);
    }
    @Test void combinaIndicadoresConSnapshotYResultadosDeFuentes() {
        when(prepare.preparar(from,to,7L)).thenReturn(new CriteriosDashboard(from,to,7L,Set.of(IndicadorDashboard.values())));
        when(db.queryForObject("select statement_timestamp()",Timestamp.class)).thenReturn(Timestamp.from(snapshot));
        when(purchases.consultar(from,to)).thenReturn(new FacturacionProveedorReportResponse(from,to,new BigDecimal("123.45")));
        when(sales.consultar(from,to)).thenReturn(new FacturacionVentaReportResponse(from,to,new BigDecimal("200.00")));
        when(inventory.resumir()).thenReturn(new ResumenAlertaStockResponse(snapshot.minusSeconds(5),3));
        when(finance.consultar(7L)).thenReturn(new ResultadoFinancieroResponse(7L,from,to,new BigDecimal("-9.20")));
        var items=service.consultar(from,to,7L).indicators();
        assertThat(items).extracting(IndicadorDashboardResponse::code).containsExactly(IndicadorDashboard.values());
        assertThat(items).extracting(IndicadorDashboardResponse::value).containsExactly(new BigDecimal("123.45"),new BigDecimal("200.00"),new BigDecimal("3"),new BigDecimal("-9.20"));
        assertThat(items.get(2).asOf()).isEqualTo(snapshot);
        assertThat(items.get(3).from()).isEqualTo(from);
    }
    @Test void falloDeAutorizacionNoConsultaBaseNiFuentes() {
        when(prepare.preparar(from,to,1L)).thenThrow(new AccessDeniedException("denegado"));
        assertThatThrownBy(()->service.consultar(from,to,1L)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(db,purchases,sales,inventory,finance);
    }
    @Test void csvEscapaComillasConservaNulosYExigeAuditoria() {
        DashboardService dashboard=mock(DashboardService.class); AuditTrail audit=mock(AuditTrail.class);
        var indicator=new IndicadorDashboardResponse(IndicadorDashboard.FINANZAS_RESULTADO,"fuente \"A\",contable",null,null,null,"NO_DISPONIBLE",null,"IMPORTE","Sin periodo");
        when(dashboard.consultar(from,to,null)).thenReturn(new DashboardResponse(from,to,List.of(indicator)));
        var csv=new String(new DashboardCsvService(dashboard,audit).exportar(from,to,null),StandardCharsets.UTF_8);
        assertThat(csv).contains("\"fuente \"\"A\"\",contable\"").contains(",,,\"NO_DISPONIBLE\",,\"IMPORTE\",\"Sin periodo\"\r\n");
        verify(audit).change(AuditAction.DASHBOARD_CSV_GENERATED,null);
        doThrow(new IllegalStateException("auditoria")).when(audit).change(AuditAction.DASHBOARD_CSV_GENERATED,null);
        assertThatThrownBy(()->new DashboardCsvService(dashboard,audit).exportar(from,to,null)).isInstanceOf(IllegalStateException.class);
    }
}
