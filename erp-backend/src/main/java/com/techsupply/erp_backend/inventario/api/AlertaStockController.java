package com.techsupply.erp_backend.inventario.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.AlertaStockService;
import com.techsupply.erp_backend.inventario.application.dto.AlertaStockResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/low-stock-alerts")
public class AlertaStockController {
    private final AlertaStockService service;
    public AlertaStockController(AlertaStockService service) { this.service = service; }

    @GetMapping @PreAuthorize("hasAuthority('INVENTARIO_ALERTAS_LEER')")
    public PageResponse<AlertaStockResponse> listar(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Long productoId) {
        return service.listar(page, size, productoId);
    }
}
