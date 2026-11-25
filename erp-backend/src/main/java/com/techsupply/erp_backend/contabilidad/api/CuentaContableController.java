package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.contabilidad.application.ContabilidadService;
import com.techsupply.erp_backend.contabilidad.application.dto.CuentaContableRequest;
import com.techsupply.erp_backend.contabilidad.application.dto.CuentaContableResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/cuentas-contables")
public class CuentaContableController {
    private final ContabilidadService service;
    public CuentaContableController(ContabilidadService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('CONTABILIDAD_LEER')")
    public List<CuentaContableResponse> listar(){return service.listarCuentas();}
    @PostMapping @PreAuthorize("hasAuthority('CONTABILIDAD_ADMIN')")
    public ResponseEntity<CuentaContableResponse> crear(@Valid @RequestBody CuentaContableRequest r){var v=service.crearCuenta(r);return ResponseEntity.created(URI.create("/api/v1/cuentas-contables/"+v.id())).body(v);}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('CONTABILIDAD_ADMIN')")
    public CuentaContableResponse actualizar(@PathVariable Long id,@Valid @RequestBody CuentaContableRequest r){return service.actualizarCuenta(id,r);}
    @PatchMapping("/{id}/estado") @PreAuthorize("hasAuthority('CONTABILIDAD_ADMIN')")
    public CuentaContableResponse estado(@PathVariable Long id,@RequestBody java.util.Map<String,Boolean> body){return service.estadoCuenta(id,Boolean.TRUE.equals(body.get("activa")));}
}
