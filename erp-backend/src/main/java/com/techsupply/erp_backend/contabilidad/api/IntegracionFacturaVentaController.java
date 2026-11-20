package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.IntegracionFacturaVentaService;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/integrations/sales-invoices")
public class IntegracionFacturaVentaController {
    private final IntegracionFacturaVentaService service;
    public IntegracionFacturaVentaController(IntegracionFacturaVentaService service) { this.service = service; }

    @GetMapping
    public PageResponse<IntegracionFacturaVentaResponse> pendientes(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) { return service.pendientes(page, size); }

    @GetMapping("/accounts")
    public ConfiguracionFacturaVentaResponse configuracion() { return service.configuracion(); }

    @PutMapping("/accounts")
    public ConfiguracionFacturaVentaResponse configurar(@Valid @RequestBody ConfiguracionFacturaVentaRequest request) {
        return service.configurar(request);
    }

    @GetMapping("/{invoiceId}")
    public IntegracionFacturaVentaResponse obtener(@PathVariable Long invoiceId) { return service.obtener(invoiceId); }

    @PostMapping("/{invoiceId}/retry")
    public IntegracionFacturaVentaResponse reintentar(@PathVariable Long invoiceId,
            @RequestBody(required = false) ReintentoFacturaVentaRequest request) {
        return service.reintentar(invoiceId, request);
    }
}
