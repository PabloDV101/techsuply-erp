package com.techsupply.erp_backend.ventas.api;

import com.techsupply.erp_backend.ventas.application.FacturacionVentaReportService;
import com.techsupply.erp_backend.ventas.application.dto.FacturacionVentaReportResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/sales/invoiced")
public class FacturacionVentaReportController {
    private final FacturacionVentaReportService service;

    public FacturacionVentaReportController(FacturacionVentaReportService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('VENTAS_FACTURAS_LEER')")
    public FacturacionVentaReportResponse consultar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.consultar(from, to);
    }
}
