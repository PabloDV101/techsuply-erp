package com.techsupply.erp_backend.ventas.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.ventas.application.DespachoService;
import com.techsupply.erp_backend.ventas.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/sales/dispatches")
public class DespachoController {
    private final DespachoService service;
    public DespachoController(DespachoService service) { this.service=service; }
    @PostMapping @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_REGISTRAR')")
    public ResponseEntity<DespachoResponse> crear(@Valid @RequestBody DespachoCreateRequest request) {
        var result=service.crear(request);
        return result.creado() ? ResponseEntity.created(URI.create("/api/v1/sales/dispatches/"+result.despacho().id())).body(result.despacho())
            : ResponseEntity.ok(result.despacho());
    }
    @GetMapping @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_LEER')")
    public PageResponse<DespachoResponse> listar(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) Long pedidoId) { return service.listar(page,size,pedidoId); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_LEER')")
    public DespachoResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @PostMapping("/{id}/confirm") @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_CONFIRMAR')")
    public DespachoResponse confirmar(@PathVariable Long id) { return service.confirmar(id); }
    @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_REGISTRAR')")
    public DespachoResponse cancelar(@PathVariable Long id) { return service.cancelar(id); }
}
