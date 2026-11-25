package com.techsupply.erp_backend.reporteria.application;

import com.techsupply.erp_backend.compras.application.FacturacionProveedorReportService;
import com.techsupply.erp_backend.contabilidad.application.ResultadoFinancieroService;
import com.techsupply.erp_backend.inventario.application.AlertaStockService;
import com.techsupply.erp_backend.reporteria.application.dto.DashboardResponse;
import com.techsupply.erp_backend.reporteria.application.dto.IndicadorDashboardResponse;
import com.techsupply.erp_backend.ventas.application.FacturacionVentaReportService;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
    private final PrepararDashboardService preparar;
    private final FacturacionProveedorReportService compras;
    private final FacturacionVentaReportService ventas;
    private final AlertaStockService inventario;
    private final ResultadoFinancieroService finanzas;
    private final JdbcTemplate db;

    public DashboardService(PrepararDashboardService preparar, FacturacionProveedorReportService compras,
            FacturacionVentaReportService ventas, AlertaStockService inventario,
            ResultadoFinancieroService finanzas, JdbcTemplate db) {
        this.preparar = preparar; this.compras = compras; this.ventas = ventas;
        this.inventario = inventario; this.finanzas = finanzas; this.db = db;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("isAuthenticated()")
    public DashboardResponse consultar(LocalDate from, LocalDate to, Long periodId) {
        var criterios = preparar.preparar(from, to, periodId);
        // Establish one database snapshot before invoking any of the domain readers.
        Instant snapshotAt = db.queryForObject("select statement_timestamp()", Timestamp.class).toInstant();
        var indicators = new ArrayList<IndicadorDashboardResponse>();
        if (criterios.indicadores().contains(IndicadorDashboard.COMPRAS_FACTURADO_SIN_IMPUESTOS)) {
            var result = compras.consultar(from, to);
            indicators.add(new IndicadorDashboardResponse(IndicadorDashboard.COMPRAS_FACTURADO_SIN_IMPUESTOS,
                "facturas_proveedor: VALIDADA", result.from(), result.to(), null, "DISPONIBLE", result.subtotal(), "IMPORTE", null));
        }
        if (criterios.indicadores().contains(IndicadorDashboard.VENTAS_FACTURADO_SIN_IMPUESTOS)) {
            var result = ventas.consultar(from, to);
            indicators.add(new IndicadorDashboardResponse(IndicadorDashboard.VENTAS_FACTURADO_SIN_IMPUESTOS,
                "facturas_venta: EMITIDA", result.from(), result.to(), null, "DISPONIBLE", result.subtotal(), "IMPORTE", null));
        }
        if (criterios.indicadores().contains(IndicadorDashboard.INVENTARIO_PRODUCTOS_ALERTA)) {
            var result = inventario.resumir();
            indicators.add(new IndicadorDashboardResponse(IndicadorDashboard.INVENTARIO_PRODUCTOS_ALERTA,
                "alerta global de inventario", null, null, snapshotAt, "DISPONIBLE",
                BigDecimal.valueOf(result.productosEnAlerta()), "PRODUCTOS", null));
        }
        if (criterios.indicadores().contains(IndicadorDashboard.FINANZAS_RESULTADO)) {
            if (periodId == null) {
                indicators.add(new IndicadorDashboardResponse(IndicadorDashboard.FINANZAS_RESULTADO,
                    "estado de resultados contable", null, null, null, "NO_DISPONIBLE", null, "IMPORTE", "Seleccione un periodo contable"));
            } else {
                var result = finanzas.consultar(periodId);
                indicators.add(new IndicadorDashboardResponse(IndicadorDashboard.FINANZAS_RESULTADO,
                    "estado de resultados contable", result.inicio(), result.fin(), null, "DISPONIBLE", result.resultado(), "IMPORTE", null));
            }
        }
        return new DashboardResponse(from, to, indicators);
    }
}
