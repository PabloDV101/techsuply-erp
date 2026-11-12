package com.techsupply.erp_backend.ventas.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.ventas.application.FacturaService;
import com.techsupply.erp_backend.ventas.application.dto.*;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/sales/invoices")
public class FacturaController {
    private final FacturaService service;
    public FacturaController(FacturaService service){this.service=service;}
    @PostMapping @PreAuthorize("hasAuthority('VENTAS_FACTURAS_EMITIR')")
    public ResponseEntity<FacturaResponse> emitir(@Valid @RequestBody FacturaCreateRequest request){
        var result=service.emitir(request);
        return result.creada()?ResponseEntity.created(URI.create("/api/v1/sales/invoices/"+result.factura().id())).body(result.factura())
            :ResponseEntity.ok(result.factura());
    }
    @GetMapping @PreAuthorize("hasAuthority('VENTAS_FACTURAS_LEER')")
    public PageResponse<FacturaResponse> listar(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size,
            @RequestParam(required=false) Long clienteId){return service.listar(page,size,clienteId);}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('VENTAS_FACTURAS_LEER')")
    public FacturaResponse obtener(@PathVariable Long id){return service.obtener(id);}
}
