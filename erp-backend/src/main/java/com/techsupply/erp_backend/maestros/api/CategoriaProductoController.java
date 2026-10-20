package com.techsupply.erp_backend.maestros.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.CategoriaProductoService;
import com.techsupply.erp_backend.maestros.application.dto.ActualizarCategoriaRequest;
import com.techsupply.erp_backend.maestros.application.dto.CategoriaResponse;
import com.techsupply.erp_backend.maestros.application.dto.CrearCategoriaRequest;
import com.techsupply.erp_backend.maestros.application.dto.EstadoCategoriaRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoriaProductoController {
    private final CategoriaProductoService service;

    public CategoriaProductoController(CategoriaProductoService service) {
        this.service = service;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('MAESTROS_CATEGORIAS_ADMIN')")
    public ResponseEntity<CategoriaResponse> crear(@Valid @RequestBody CrearCategoriaRequest request) {
        CategoriaResponse created = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + created.id())).body(created);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MAESTROS_CATEGORIAS_LEER')")
    public CategoriaResponse obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('MAESTROS_CATEGORIAS_LEER')")
    public PageResponse<CategoriaResponse> listar(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size,
        @RequestParam(defaultValue = "codigo") String sort,
        @RequestParam(defaultValue = "asc") String direction) {
        return service.listar(page, size, sort, direction);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MAESTROS_CATEGORIAS_ADMIN')")
    public CategoriaResponse actualizar(@PathVariable Long id, @Valid @RequestBody ActualizarCategoriaRequest request) {
        return service.actualizar(id, request);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('MAESTROS_CATEGORIAS_ADMIN')")
    public CategoriaResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoCategoriaRequest request) {
        return service.cambiarEstado(id, request.activa());
    }
}
