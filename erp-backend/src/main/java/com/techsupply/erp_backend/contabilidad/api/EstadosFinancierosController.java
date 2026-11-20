package com.techsupply.erp_backend.contabilidad.api;
import com.techsupply.erp_backend.contabilidad.application.EstadosFinancierosService;
import com.techsupply.erp_backend.contabilidad.application.dto.EstadoFinancieroResponse;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/reports/finance")
public class EstadosFinancierosController {
    private final EstadosFinancierosService service;
    public EstadosFinancierosController(EstadosFinancierosService service){this.service=service;}
    @GetMapping("/income-statement") public EstadoFinancieroResponse resultados(@RequestParam Long periodId){return service.resultados(periodId);}
    @GetMapping("/balance-sheet") public EstadoFinancieroResponse balance(@RequestParam Long periodId){return service.balance(periodId);}
    @GetMapping("/cash-flow") public EstadoFinancieroResponse flujo(@RequestParam Long periodId){return service.flujo(periodId);}
}
