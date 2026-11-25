package com.techsupply.erp_backend.reporteria.api;

import com.techsupply.erp_backend.reporteria.application.DashboardCsvService;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/reports/dashboard/export.csv")
public class DashboardCsvController {
    private final DashboardCsvService service;
    public DashboardCsvController(DashboardCsvService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> exportar(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long periodId) {
        return ResponseEntity.ok().header("Content-Type", "text/csv;charset=UTF-8")
            .header("Content-Disposition", "attachment; filename=dashboard.csv")
            .header("Cache-Control", "no-store")
            .body(service.exportar(from, to, periodId));
    }
}
