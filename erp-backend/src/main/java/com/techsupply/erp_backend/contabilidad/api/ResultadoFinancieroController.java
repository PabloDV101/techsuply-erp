package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.contabilidad.application.ResultadoFinancieroService;
import com.techsupply.erp_backend.contabilidad.application.dto.ResultadoFinancieroResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports/finance/result-summary")
public class ResultadoFinancieroController {
    private final ResultadoFinancieroService service;

    public ResultadoFinancieroController(ResultadoFinancieroService service) { this.service = service; }

    @GetMapping
    @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public ResultadoFinancieroResponse consultar(@RequestParam Long periodId) { return service.consultar(periodId); }
}
