package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.contabilidad.application.PagosProveedorService;
import com.techsupply.erp_backend.contabilidad.application.dto.PagoProveedorRequests.*;
import jakarta.validation.Valid;
import com.techsupply.erp_backend.contabilidad.application.dto.PagoProveedorResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/supplier-payments")
public class PagosProveedorController {
    private final PagosProveedorService service;
    public PagosProveedorController(PagosProveedorService service) { this.service=service; }
    @PostMapping public PagoProveedorResponse registrar(@Valid @RequestBody Registrar request) { return service.registrar(request); }
    @GetMapping("/{id}") public PagoProveedorResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @PostMapping("/{id}/confirm") public PagoProveedorResponse confirmar(@PathVariable Long id,@Valid @RequestBody Confirmar request) { return service.confirmar(id,request); }
    @GetMapping("/accounts") public Configurar configuracion() { return service.configuracion(); }
    @PutMapping("/accounts") public void configurar(@Valid @RequestBody Configurar request) { service.configurar(request); }
}
