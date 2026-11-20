package com.techsupply.erp_backend.compras.api;

import com.techsupply.erp_backend.compras.application.FacturaProveedorService;
import com.techsupply.erp_backend.compras.application.dto.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1")
public class FacturaProveedorController {
    private final FacturaProveedorService service;
    public FacturaProveedorController(FacturaProveedorService service) { this.service=service; }

    @PostMapping("/supplier-invoices") @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_REGISTRAR')")
    public ResponseEntity<FacturaProveedorResponse> registrar(@Valid @RequestBody FacturaProveedorWriteRequest request) {
        var value=service.registrar(request);
        return ResponseEntity.created(URI.create("/api/v1/supplier-invoices/"+value.id())).body(value);
    }
    @PutMapping("/supplier-invoices/{id}") @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_REGISTRAR')")
    public FacturaProveedorResponse editar(@PathVariable Long id,@Valid @RequestBody FacturaProveedorWriteRequest request) {
        return service.editar(id,request);
    }
    @GetMapping("/supplier-invoices/{id}") @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_LEER')")
    public FacturaProveedorResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @PostMapping("/supplier-invoices/{id}/validate") @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_VALIDAR')")
    public FacturaProveedorResponse validar(@PathVariable Long id) { return service.validar(id); }
    @GetMapping("/accounting/payables/{id}") @PreAuthorize("hasAuthority('CONTABILIDAD_CXP_LEER')")
    public Map<String,Object> cuentaPorPagar(@PathVariable Long id) { return service.cuentaPorPagar(id); }
    @GetMapping("/accounting/supplier-invoice-accounts") @PreAuthorize("hasAuthority('CONTABILIDAD_CXP_LEER')")
    public Map<String,Long> configuracion() { return service.configuracion(); }
    @PutMapping("/accounting/supplier-invoice-accounts") @PreAuthorize("hasAuthority('CONTABILIDAD_CXP_CONFIGURAR')")
    public Map<String,Long> configurar(@Valid @RequestBody Configuracion request) {
        return service.configurar(request.ivaAcreditableCuentaId(),request.cuentasPorPagarCuentaId());
    }
    public record Configuracion(@NotNull @Positive Long ivaAcreditableCuentaId,
        @NotNull @Positive Long cuentasPorPagarCuentaId) {}
}
