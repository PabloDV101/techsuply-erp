package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.contabilidad.application.ContabilidadService;
import com.techsupply.erp_backend.contabilidad.application.dto.PeriodoContableRequest;
import com.techsupply.erp_backend.contabilidad.application.dto.PeriodoContableResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/periodos-contables")
public class PeriodoContableController {
    private final ContabilidadService service;
    public PeriodoContableController(ContabilidadService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('CONTABILIDAD_LEER')") public List<PeriodoContableResponse> listar(){return service.listarPeriodos();}
    @PostMapping @PreAuthorize("hasAuthority('CONTABILIDAD_ADMIN')") public ResponseEntity<PeriodoContableResponse> crear(@Valid @RequestBody PeriodoContableRequest r){var v=service.crearPeriodo(r);return ResponseEntity.created(URI.create("/api/v1/periodos-contables/"+v.id())).body(v);}
    @PostMapping("/{id}/cerrar") @PreAuthorize("hasAuthority('CONTABILIDAD_ADMIN')") public PeriodoContableResponse cerrar(@PathVariable Long id){return service.cerrarPeriodo(id);}
}
