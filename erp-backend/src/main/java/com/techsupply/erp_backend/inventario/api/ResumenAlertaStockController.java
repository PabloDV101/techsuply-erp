package com.techsupply.erp_backend.inventario.api;

import com.techsupply.erp_backend.inventario.application.AlertaStockService;
import com.techsupply.erp_backend.inventario.application.dto.ResumenAlertaStockResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/inventory/low-stock-summary")
public class ResumenAlertaStockController {
    private final AlertaStockService service;

    public ResumenAlertaStockController(AlertaStockService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('INVENTARIO_ALERTAS_LEER')")
    public ResumenAlertaStockResponse consultar() { return service.resumir(); }
}
