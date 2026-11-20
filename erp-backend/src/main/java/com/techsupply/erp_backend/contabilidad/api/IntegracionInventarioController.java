package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.contabilidad.application.IntegracionInventarioContableService;
import com.techsupply.erp_backend.contabilidad.application.dto.ConfiguracionCuentasInventarioRequest;
import com.techsupply.erp_backend.contabilidad.application.dto.ConfiguracionCuentasInventarioResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/inventory-account-mappings")
public class IntegracionInventarioController {
    private final IntegracionInventarioContableService service;
    public IntegracionInventarioController(IntegracionInventarioContableService service) { this.service = service; }
    @GetMapping public ConfiguracionCuentasInventarioResponse obtener() { return service.configuracion(); }
    @PutMapping public ConfiguracionCuentasInventarioResponse configurar(@Valid @RequestBody ConfiguracionCuentasInventarioRequest request) {
        return service.configurar(request);
    }
}
