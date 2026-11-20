package com.techsupply.erp_backend.contabilidad.api;
import com.techsupply.erp_backend.contabilidad.application.ClasificacionFlujoService;
import com.techsupply.erp_backend.contabilidad.application.dto.ClasificacionFlujoRequests.*;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/accounting/cash-flow-classifications")
public class ClasificacionFlujoController {
    private final ClasificacionFlujoService service;
    public ClasificacionFlujoController(ClasificacionFlujoService service) { this.service=service; }
    @PostMapping("/accounts") public Map<String,Long> cuenta(@Valid @RequestBody Cuenta req) { return service.cuenta(req); }
    @PostMapping("/lines") public Map<String,Long> partida(@Valid @RequestBody Partida req) { return service.partida(req); }
    @PostMapping("/accounts/{id}/void") public void anularCuenta(@PathVariable Long id,@Valid @RequestBody Anulacion req) { service.anularCuenta(id,req); }
    @PostMapping("/lines/{id}/void") public void anularPartida(@PathVariable Long id,@Valid @RequestBody Anulacion req) { service.anularPartida(id,req); }
}
