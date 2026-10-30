package com.techsupply.erp_backend.maestros.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.DepartamentoService;
import com.techsupply.erp_backend.maestros.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/departments")
public class DepartamentoController {
    private final DepartamentoService service;

    public DepartamentoController(DepartamentoService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAuthority('MAESTROS_DEPARTAMENTOS_ADMIN')")
    public ResponseEntity<DepartamentoResponse> crear(@Valid @RequestBody DepartamentoWriteRequest request) {
        DepartamentoResponse created = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/departments/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MAESTROS_DEPARTAMENTOS_LEER')")
    public DepartamentoResponse obtener(@PathVariable Long id) { return service.obtener(id); }

    @GetMapping
    @PreAuthorize("hasAuthority('MAESTROS_DEPARTAMENTOS_LEER')")
    public PageResponse<DepartamentoResponse> listar(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "codigo") String sort,
        @RequestParam(defaultValue = "asc") String direction,
        @RequestParam(required = false) String codigo,
        @RequestParam(required = false) String activo) {
        return service.listar(page, size, sort, direction, MasterCatalogSearchCriteria.from(codigo, activo));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MAESTROS_DEPARTAMENTOS_ADMIN')")
    public DepartamentoResponse actualizar(@PathVariable Long id, @Valid @RequestBody DepartamentoWriteRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('MAESTROS_DEPARTAMENTOS_ADMIN')")
    public DepartamentoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoMaestroRequest request) {
        return service.cambiarEstado(id, request.activo());
    }
}
