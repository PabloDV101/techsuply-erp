package com.techsupply.erp_backend.compras.api;

import com.techsupply.erp_backend.compras.application.FacturacionProveedorReportService;
import com.techsupply.erp_backend.compras.application.dto.FacturacionProveedorReportResponse;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/purchases/invoiced")
public class FacturacionProveedorReportController {
    private final FacturacionProveedorReportService service;

    public FacturacionProveedorReportController(FacturacionProveedorReportService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_LEER')")
    public FacturacionProveedorReportResponse consultar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return service.consultar(from, to);
    }
}
