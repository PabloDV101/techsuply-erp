package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.AsientoContableService;
import com.techsupply.erp_backend.contabilidad.application.dto.AsientoContableCreateRequest;
import com.techsupply.erp_backend.contabilidad.application.dto.AsientoContableEditRequest;
import com.techsupply.erp_backend.contabilidad.application.dto.AsientoContableCancelRequest;
import com.techsupply.erp_backend.contabilidad.application.dto.AsientoContableReverseRequest;
import com.techsupply.erp_backend.contabilidad.application.dto.AsientoContableResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/entries")
public class AsientoContableController {
    private final AsientoContableService service;
    public AsientoContableController(AsientoContableService service) { this.service = service; }

    @PostMapping @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR')")
    public ResponseEntity<AsientoContableResponse> crear(@Valid @RequestBody AsientoContableCreateRequest request) {
        var result = service.crear(request);
        if (result.repetida()) return ResponseEntity.ok(result.asiento());
        return ResponseEntity.created(URI.create("/api/v1/accounting/entries/" + result.asiento().id())).body(result.asiento());
    }
    @GetMapping @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_LEER')")
    public PageResponse<AsientoContableResponse> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "fechaContable") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return service.listar(page, size, sort, direction);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_LEER')")
    public AsientoContableResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @PutMapping("/{id}/edit") @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR')")
    public AsientoContableResponse editar(@PathVariable Long id, @Valid @RequestBody AsientoContableEditRequest request) {
        return service.editar(id, request);
    }
    @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR')")
    public AsientoContableResponse cancelar(@PathVariable Long id, @Valid @RequestBody AsientoContableCancelRequest request) {
        return service.cancelar(id, request);
    }
    @PostMapping("/{id}/post") @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_CONTABILIZAR')")
    public AsientoContableResponse contabilizar(@PathVariable Long id) { return service.contabilizar(id); }
    @PostMapping("/{id}/reverse") @PreAuthorize("hasAuthority('CONTABILIDAD_ASIENTOS_REVERTIR')")
    public AsientoContableResponse revertir(@PathVariable Long id, @Valid @RequestBody AsientoContableReverseRequest request) {
        return service.revertir(id, request);
    }
}
