package com.techsupply.erp_backend.inventario.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.AjusteInventarioService;
import com.techsupply.erp_backend.inventario.application.dto.AjusteResponse;
import com.techsupply.erp_backend.inventario.application.dto.CrearAjusteRequest;
import com.techsupply.erp_backend.inventario.application.dto.RechazarAjusteRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/inventory/adjustments")
public class AjusteInventarioController {
    private final AjusteInventarioService service;
    public AjusteInventarioController(AjusteInventarioService service) { this.service=service; }
    @PostMapping @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_REGISTRAR')")
    public ResponseEntity<AjusteResponse> crear(@Valid @RequestBody CrearAjusteRequest request) {
        var result=service.crear(request);
        return result.creado() ? ResponseEntity.created(URI.create("/api/v1/inventory/adjustments/"+result.ajuste().id())).body(result.ajuste()) : ResponseEntity.ok(result.ajuste());
    }
    @GetMapping @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_LEER')")
    public PageResponse<AjusteResponse> listar(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
        @RequestParam(required=false) Long almacenId,@RequestParam(required=false) String estado) { return service.listar(page,size,almacenId,estado); }
    @PostMapping("/{id}/apply") @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_APLICAR')")
    public AjusteResponse aplicar(@PathVariable Long id) { return service.aplicar(id); }
    @PostMapping("/{id}/approve") @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_APROBAR')")
    public AjusteResponse aprobar(@PathVariable Long id) { return service.aprobar(id); }
    @PostMapping("/{id}/reject") @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_APROBAR')")
    public AjusteResponse rechazar(@PathVariable Long id, @Valid @RequestBody RechazarAjusteRequest request) { return service.rechazar(id,request); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_LEER')")
    public AjusteResponse obtener(@PathVariable Long id) { return service.obtener(id); }
}
