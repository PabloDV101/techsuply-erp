package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.contabilidad.application.ReportesContablesService;
import com.techsupply.erp_backend.contabilidad.application.dto.BalancePruebaResponse;
import com.techsupply.erp_backend.contabilidad.application.dto.LibroMayorResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ReportesContablesController {
    private final ReportesContablesService service;

    public ReportesContablesController(ReportesContablesService service) { this.service = service; }

    @GetMapping("/reports/finance/trial-balance")
    @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public BalancePruebaResponse balancePrueba(@RequestParam Long periodId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "codigoCuenta") String sort,
            @RequestParam(defaultValue = "asc") String direction) {
        return service.balancePrueba(periodId, page, size, sort, direction);
    }

    @GetMapping("/accounting/periods/{periodId}/accounts/{accountId}/ledger")
    @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public LibroMayorResponse libroMayor(@PathVariable Long periodId, @PathVariable Long accountId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return service.libroMayor(periodId, accountId, page, size);
    }
}
