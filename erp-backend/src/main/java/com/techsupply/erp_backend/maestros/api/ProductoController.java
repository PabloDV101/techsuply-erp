package com.techsupply.erp_backend.maestros.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.ProductoService;
import com.techsupply.erp_backend.maestros.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductoController {
    private final ProductoService service;
    public ProductoController(ProductoService service) { this.service = service; }
    @PostMapping
    @PreAuthorize("hasAuthority('MAESTROS_PRODUCTOS_ADMIN')")
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoWriteRequest request) {
        ProductoResponse product = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + product.id())).body(product);
    }
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('MAESTROS_PRODUCTOS_LEER')")
    public ProductoResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @GetMapping
    @PreAuthorize("hasAuthority('MAESTROS_PRODUCTOS_LEER')")
    public PageResponse<ProductoResponse> listar(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "sku") String sort,
        @RequestParam(defaultValue = "asc") String direction, @RequestParam(required = false) String sku,
        @RequestParam(required = false) Long categoriaId, @RequestParam(required = false) String activo) {
        return service.listar(page, size, sort, direction, sku, categoriaId, activo);
    }
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('MAESTROS_PRODUCTOS_ADMIN')")
    public ProductoResponse actualizar(@PathVariable Long id, @Valid @RequestBody ProductoWriteRequest request) {
        return service.actualizar(id, request);
    }
    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('MAESTROS_PRODUCTOS_ADMIN')")
    public ProductoResponse cambiarEstado(@PathVariable Long id, @Valid @RequestBody EstadoMaestroRequest request) {
        return service.cambiarEstado(id, request.activo());
    }
}
