package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.CuentaContableService;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/accounts")
public class CuentaContableController {
    private final CuentaContableService service;
    public CuentaContableController(CuentaContableService service) { this.service = service; }

    @PostMapping @PreAuthorize("hasAuthority('CONTABILIDAD_CUENTAS_ADMIN')")
    public ResponseEntity<CuentaContableResponse> crear(@Valid @RequestBody CuentaContableWriteRequest request) {
        var cuenta = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/accounting/accounts/" + cuenta.id())).body(cuenta);
    }
    @GetMapping @PreAuthorize("hasAuthority('CONTABILIDAD_CUENTAS_LEER')")
    public PageResponse<CuentaContableResponse> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "codigo") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return service.listar(page, size, sort, direction);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('CONTABILIDAD_CUENTAS_LEER')")
    public CuentaContableResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('CONTABILIDAD_CUENTAS_ADMIN')")
    public CuentaContableResponse actualizar(@PathVariable Long id, @Valid @RequestBody CuentaContableWriteRequest request) {
        return service.actualizar(id, request);
    }
    @PatchMapping("/{id}/status") @PreAuthorize("hasAuthority('CONTABILIDAD_CUENTAS_ADMIN')")
    public CuentaContableResponse estado(@PathVariable Long id, @Valid @RequestBody CuentaContableEstadoRequest request) {
        return service.cambiarEstado(id, request.activa());
    }
}
