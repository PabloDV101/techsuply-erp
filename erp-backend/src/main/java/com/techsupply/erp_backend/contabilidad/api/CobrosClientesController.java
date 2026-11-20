package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.contabilidad.application.CobrosClientesService;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/accounting/customer-receipts")
public class CobrosClientesController {
    private final CobrosClientesService service;
    public CobrosClientesController(CobrosClientesService service){this.service=service;}

    @PostMapping public CobroClienteResponse registrar(@Valid @RequestBody CobroClienteCreateRequest request){return service.registrar(request);}
    @GetMapping("/{id}") public CobroClienteResponse obtener(@PathVariable Long id){return service.obtener(id);}
    @PostMapping("/{id}/confirm") public CobroClienteResponse confirmar(@PathVariable Long id,@Valid @RequestBody ConfirmarCobroRequest request){return service.confirmar(id,request);}
    @PostMapping("/{id}/applications") public CobroClienteResponse aplicar(@PathVariable Long id,@Valid @RequestBody AplicarCobroRequest request){return service.aplicar(id,request);}
    @GetMapping("/accounts") public ConfiguracionCobroResponse configuracion(){return service.configuracion();}
    @PutMapping("/accounts") public ConfiguracionCobroResponse configurar(@Valid @RequestBody ConfiguracionCobroRequest request){return service.configurar(request);}
}
