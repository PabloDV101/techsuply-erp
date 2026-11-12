package com.techsupply.erp_backend.ventas.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.ventas.application.CotizacionService;
import com.techsupply.erp_backend.ventas.application.dto.CotizacionResponse;
import com.techsupply.erp_backend.ventas.application.dto.CotizacionCreateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/sales/quotes")
public class CotizacionController {
    private final CotizacionService service;
    public CotizacionController(CotizacionService service) { this.service = service; }

    @PostMapping
    @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_REGISTRAR')")
    public ResponseEntity<CotizacionResponse> crear(@Valid @RequestBody CotizacionCreateRequest request) {
        var result = service.crear(request);
        URI uri = URI.create("/api/v1/sales/quotes/" + result.cotizacion().id());
        return result.creada() ? ResponseEntity.created(uri).body(result.cotizacion()) : ResponseEntity.ok(result.cotizacion());
    }
    @PostMapping("/{id}/issue")
    @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_ADMIN')")
    public CotizacionResponse emitir(@PathVariable Long id) { return service.emitir(id); }
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_ADMIN')")
    public CotizacionResponse aceptar(@PathVariable Long id) { return service.aceptar(id); }
    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_ADMIN')")
    public CotizacionResponse cancelar(@PathVariable Long id) { return service.cancelar(id); }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_LEER')")
    public CotizacionResponse obtener(@PathVariable Long id) { return service.obtener(id); }

    @GetMapping
    @PreAuthorize("hasAuthority('VENTAS_COTIZACIONES_LEER')")
    public PageResponse<CotizacionResponse> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Long clienteId) {
        return service.listar(page, size, clienteId);
    }
}
