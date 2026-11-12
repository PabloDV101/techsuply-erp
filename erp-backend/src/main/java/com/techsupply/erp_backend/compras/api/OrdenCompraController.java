package com.techsupply.erp_backend.compras.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.compras.application.OrdenCompraService;
import com.techsupply.erp_backend.compras.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/purchase-orders")
public class OrdenCompraController {
    private final OrdenCompraService service;
    public OrdenCompraController(OrdenCompraService service) { this.service = service; }
    @PostMapping @PreAuthorize("hasAuthority('COMPRAS_ORDENES_ADMIN')")
    public ResponseEntity<OrdenCompraResponse> crear(@Valid @RequestBody CrearOrdenRequest request) {
        var result = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/purchase-orders/" + result.id())).body(result);
    }
    @GetMapping @PreAuthorize("hasAuthority('COMPRAS_ORDENES_LEER')")
    public PageResponse<OrdenCompraResponse> listar(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "fecha") String sort,
        @RequestParam(defaultValue = "desc") String direction, @RequestParam(required = false) Long proveedorId,
        @RequestParam(required = false) String estado) {
        return service.listar(page, size, sort, direction, proveedorId, estado);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('COMPRAS_ORDENES_LEER')")
    public OrdenCompraResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @PostMapping("/{id}/submit") @PreAuthorize("hasAuthority('COMPRAS_ORDENES_ADMIN')")
    public OrdenCompraResponse enviarAprobacion(@PathVariable Long id) { return service.enviarAprobacion(id); }
    @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('COMPRAS_ORDENES_APROBAR')")
    public OrdenCompraResponse aprobar(@PathVariable Long id) { return service.aprobar(id); }
    @PostMapping("/{id}/send") @PreAuthorize("hasAuthority('COMPRAS_ORDENES_ADMIN')")
    public OrdenCompraResponse enviar(@PathVariable Long id) { return service.enviar(id); }
}
