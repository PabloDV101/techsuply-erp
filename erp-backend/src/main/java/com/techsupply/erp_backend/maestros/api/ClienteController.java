package com.techsupply.erp_backend.maestros.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.ClienteService;
import com.techsupply.erp_backend.maestros.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/customers")
public class ClienteController {
    private final ClienteService service;
    public ClienteController(ClienteService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_ADMIN')")
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteWriteRequest request) {
        ClienteResponse result = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/customers/" + result.id())).body(result);
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_LEER')")
    public ClienteResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @GetMapping
    @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_LEER')")
    public PageResponse<ClienteResponse> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) String activo) {
        return service.listar(page, size, MasterCatalogSearchCriteria.from(null, activo));
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_ADMIN')")
    public ClienteResponse actualizar(@PathVariable Long id, @Valid @RequestBody ClienteWriteRequest request) {
        return service.actualizar(id, request);
    }
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_ADMIN')")
    public ClienteResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoMaestroRequest request) {
        return service.cambiarEstado(id, request.activo());
    }
}
