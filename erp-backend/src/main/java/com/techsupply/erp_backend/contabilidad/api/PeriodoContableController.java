package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.PeriodoContableService;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/periods")
public class PeriodoContableController {
    private final PeriodoContableService service;
    public PeriodoContableController(PeriodoContableService service) { this.service = service; }

    @PostMapping @PreAuthorize("hasAuthority('CONTABILIDAD_PERIODOS_ADMIN')")
    public ResponseEntity<PeriodoContableResponse> crear(@Valid @RequestBody PeriodoContableCreateRequest request) {
        var periodo = service.crear(request);
        return ResponseEntity.created(URI.create("/api/v1/accounting/periods/" + periodo.id())).body(periodo);
    }
    @GetMapping @PreAuthorize("hasAuthority('CONTABILIDAD_PERIODOS_LEER')")
    public PageResponse<PeriodoContableResponse> listar(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "fechaInicio") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return service.listar(page, size, sort, direction);
    }
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('CONTABILIDAD_PERIODOS_LEER')")
    public PeriodoContableResponse obtener(@PathVariable Long id) { return service.obtener(id); }
    @PostMapping("/{id}/close") @PreAuthorize("hasAuthority('CONTABILIDAD_PERIODOS_CERRAR')")
    public PeriodoContableResponse cerrar(@PathVariable Long id) { return service.cerrar(id); }
}
