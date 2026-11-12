package com.techsupply.erp_backend.inventario.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.MovimientoConsultaService;
import com.techsupply.erp_backend.inventario.application.dto.MovimientoResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory/movements")
public class MovimientoController {
    private final MovimientoConsultaService service;
    public MovimientoController(MovimientoConsultaService service) { this.service = service; }

    @GetMapping @PreAuthorize("hasAuthority('INVENTARIO_MOVIMIENTOS_LEER')")
    public PageResponse<MovimientoResponse> listar(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @RequestParam(required = false) Long productoId,
        @RequestParam(required = false) Long almacenId) {
        return service.listar(page, size, productoId, almacenId);
    }
}
