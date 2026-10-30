package com.techsupply.erp_backend.inventario.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.InventarioService;
import com.techsupply.erp_backend.inventario.application.dto.ExistenciaResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/inventory/stock")
public class ExistenciaController {
    private final InventarioService service;
    public ExistenciaController(InventarioService service) { this.service = service; }
    @GetMapping @PreAuthorize("hasAuthority('INVENTARIO_EXISTENCIAS_LEER')")
    public PageResponse<ExistenciaResponse> listar(@RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "id") String sort,
        @RequestParam(defaultValue = "asc") String direction, @RequestParam(required = false) Long productoId,
        @RequestParam(required = false) Long almacenId) {
        return service.listar(page, size, sort, direction, productoId, almacenId);
    }
}
