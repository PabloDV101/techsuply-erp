package com.techsupply.erp_backend.ventas.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.ventas.application.PedidoService;
import com.techsupply.erp_backend.ventas.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/sales/orders")
public class PedidoController {
    private final PedidoService service;
    public PedidoController(PedidoService service) { this.service = service; }
    @PostMapping @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_REGISTRAR')")
    public ResponseEntity<PedidoResponse> crear(@Valid @RequestBody PedidoCreateRequest request) {
        var result = service.crear(request);
        return result.creado() ? ResponseEntity.created(URI.create("/api/v1/sales/orders/" + result.pedido().id())).body(result.pedido())
            : ResponseEntity.ok(result.pedido());
    }
    @GetMapping @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_LEER')")
    public PageResponse<PedidoResponse> listar(@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) Long clienteId) { return service.listar(page, size, clienteId); }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_LEER')")
    public PedidoResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @PostMapping("/{id}/confirm") @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_CONFIRMAR')")
    public PedidoResponse confirmar(@PathVariable Long id) { return service.confirmar(id); }
    @PostMapping("/{id}/cancel") @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_CANCELAR')")
    public PedidoResponse cancelar(@PathVariable Long id, @Valid @RequestBody CancelarPedidoRequest request) { return service.cancelar(id,request); }
}
