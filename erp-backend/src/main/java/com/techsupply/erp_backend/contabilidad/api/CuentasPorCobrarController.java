package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.CuentasPorCobrarService;
import com.techsupply.erp_backend.contabilidad.application.dto.CuentaPorCobrarResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounting/accounts-receivable")
public class CuentasPorCobrarController {
    private final CuentasPorCobrarService service;

    public CuentasPorCobrarController(CuentasPorCobrarService service) { this.service = service; }

    @GetMapping
    public PageResponse<CuentaPorCobrarResponse> listar(@RequestParam(required = false) Long clienteId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.listar(clienteId, page, size);
    }

    @GetMapping("/{id}")
    public CuentaPorCobrarResponse obtener(@PathVariable Long id) { return service.obtener(id); }
}
