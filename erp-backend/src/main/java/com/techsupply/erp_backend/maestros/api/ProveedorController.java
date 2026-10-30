package com.techsupply.erp_backend.maestros.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.ProveedorService;
import com.techsupply.erp_backend.maestros.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/suppliers")
public class ProveedorController {
    private final ProveedorService service;

    public ProveedorController(ProveedorService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAuthority('MAESTROS_PROVEEDORES_ADMIN')")
    public ResponseEntity<ProveedorResponse> crear(@Valid @RequestBody ProveedorWriteRequest request) {
        ProveedorResponse created = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/suppliers/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MAESTROS_PROVEEDORES_LEER')")
    public ProveedorResponse obtener(@PathVariable Long id) { return service.obtener(id); }

    @GetMapping
    @PreAuthorize("hasAuthority('MAESTROS_PROVEEDORES_LEER')")
    public PageResponse<ProveedorResponse> listar(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "codigo") String sort,
        @RequestParam(defaultValue = "asc") String direction,
        @RequestParam(required = false) String codigo,
        @RequestParam(required = false) String activo) {
        return service.listar(page, size, sort, direction, MasterCatalogSearchCriteria.from(codigo, activo));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MAESTROS_PROVEEDORES_ADMIN')")
    public ProveedorResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProveedorWriteRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('MAESTROS_PROVEEDORES_ADMIN')")
    public ProveedorResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoMaestroRequest request) {
        return service.cambiarEstado(id, request.activo());
    }
}
