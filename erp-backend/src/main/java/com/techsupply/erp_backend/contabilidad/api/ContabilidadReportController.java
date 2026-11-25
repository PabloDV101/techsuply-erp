package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.contabilidad.application.ContabilidadService;
import com.techsupply.erp_backend.contabilidad.application.dto.ReportResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/reportes/contabilidad")
public class ContabilidadReportController {
    private final ContabilidadService service;
    public ContabilidadReportController(ContabilidadService service){this.service=service;}
    @GetMapping("/balance-prueba") @PreAuthorize("hasAuthority('CONTABILIDAD_LEER')") public ReportResponse balancePrueba(@RequestParam Long periodoId){return service.reporteBalancePrueba(periodoId);}
    @GetMapping("/mayor") @PreAuthorize("hasAuthority('CONTABILIDAD_LEER')") public ReportResponse mayor(@RequestParam Long periodoId,@RequestParam(required=false)Long cuentaId){return service.reporteMayor(periodoId,cuentaId);}
    @GetMapping("/estado-resultados") @PreAuthorize("hasAuthority('CONTABILIDAD_LEER')") public ReportResponse resultados(@RequestParam Long periodoId){return service.reporteTipo(periodoId,"INCOME");}
    @GetMapping("/balance-general") @PreAuthorize("hasAuthority('CONTABILIDAD_LEER')") public ReportResponse balance(@RequestParam Long periodoId){return service.reporteTipo(periodoId,"BALANCE");}
    @GetMapping("/flujo-efectivo") @PreAuthorize("hasAuthority('CONTABILIDAD_LEER')") public ReportResponse flujo(@RequestParam Long periodoId){return new ReportResponse("CASH_FLOW",periodoId,null,null,java.util.List.of(),null,"NO_DISPONIBLE");}
}
