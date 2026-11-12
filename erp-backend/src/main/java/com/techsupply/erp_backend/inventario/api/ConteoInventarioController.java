package com.techsupply.erp_backend.inventario.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.ConteoInventarioService;
import com.techsupply.erp_backend.inventario.application.dto.CapturarConteoRequest;
import com.techsupply.erp_backend.inventario.application.dto.ConteoResponse;
import com.techsupply.erp_backend.inventario.application.dto.CrearConteoRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/inventory/counts")
public class ConteoInventarioController {
    private final ConteoInventarioService service;
    public ConteoInventarioController(ConteoInventarioService service) { this.service = service; }
    @PostMapping @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_ADMIN')")
    public ResponseEntity<ConteoResponse> crear(@Valid @RequestBody CrearConteoRequest request) {
        ConteoResponse result = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/inventory/counts/" + result.id())).body(result);
    }
    @GetMapping @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_LEER')")
    public PageResponse<ConteoResponse> listar(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
        @RequestParam(required = false) Long almacenId, @RequestParam(required = false) String estado) { return service.listar(page, size, almacenId, estado); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_LEER')")
    public ConteoResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @PutMapping("/{id}/lines/{productoId}") @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_ADMIN')")
    public ConteoResponse capturar(@PathVariable Long id, @PathVariable Long productoId, @Valid @RequestBody CapturarConteoRequest request) { return service.capturar(id, productoId, request); }
    @PostMapping("/{id}/close") @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_ADMIN')")
    public ConteoResponse cerrar(@PathVariable Long id) { return service.cerrar(id); }
    @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('INVENTARIO_CONTEOS_ADMIN')")
    public ConteoResponse cancelar(@PathVariable Long id) { return service.cancelar(id); }
}
