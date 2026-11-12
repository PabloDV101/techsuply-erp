package com.techsupply.erp_backend.compras.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.compras.application.RecepcionCompraService;
import com.techsupply.erp_backend.compras.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/purchase-receipts")
public class RecepcionCompraController {
    private final RecepcionCompraService service;
    public RecepcionCompraController(RecepcionCompraService service) { this.service = service; }
    @PostMapping @PreAuthorize("hasAuthority('COMPRAS_RECEPCIONES_REGISTRAR')")
    public ResponseEntity<RecepcionCompraResponse> crear(@Valid @RequestBody CrearRecepcionRequest request) {
        var result = service.crear(request);
        return result.creada() ? ResponseEntity.created(URI.create("/api/v1/purchase-receipts/" + result.recepcion().id())).body(result.recepcion())
            : ResponseEntity.ok(result.recepcion());
    }
    @GetMapping @PreAuthorize("hasAuthority('COMPRAS_RECEPCIONES_LEER')")
    public PageResponse<RecepcionCompraResponse> listar(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "fecha") String sort,
        @RequestParam(defaultValue = "desc") String direction, @RequestParam(required = false) Long ordenCompraId) {
        return service.listar(page, size, sort, direction, ordenCompraId);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('COMPRAS_RECEPCIONES_LEER')")
    public RecepcionCompraResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @PostMapping("/{id}/confirm") @PreAuthorize("hasAuthority('COMPRAS_RECEPCIONES_CONFIRMAR')")
    public RecepcionCompraResponse confirmar(@PathVariable Long id) { return service.confirmar(id); }
}
