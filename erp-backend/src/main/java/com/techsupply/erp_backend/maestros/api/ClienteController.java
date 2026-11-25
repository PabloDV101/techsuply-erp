package com.techsupply.erp_backend.maestros.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.MaestroOperativoService;
import com.techsupply.erp_backend.maestros.application.dto.ClienteRequest;
import com.techsupply.erp_backend.maestros.application.dto.ClienteResponse;
import com.techsupply.erp_backend.maestros.application.dto.EstadoActivoRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/clientes")
public class ClienteController {
    private final MaestroOperativoService service;
    public ClienteController(MaestroOperativoService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_LEER')")
    public PageResponse<ClienteResponse> listar(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="codigo")String sort,@RequestParam(defaultValue="asc")String direction,@RequestParam(required=false)String search,@RequestParam(required=false)Boolean activo){return service.listarClientes(page,size,sort,direction,search,activo);}
    @PostMapping @PreAuthorize("hasAuthority('CLIENTE_WRITE')")
    public ResponseEntity<ClienteResponse> crear(@Valid @RequestBody ClienteRequest request){ClienteResponse r=service.crearCliente(request);return ResponseEntity.created(URI.create("/api/v1/clientes/"+r.id())).body(r);}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('CLIENTE_WRITE')")
    public ClienteResponse actualizar(@PathVariable Long id,@Valid @RequestBody ClienteRequest request){return service.actualizarCliente(id,request);}
    @PatchMapping("/{id}/estado") @PreAuthorize("hasAuthority('CLIENTE_WRITE')")
    public ClienteResponse estado(@PathVariable Long id,@Valid @RequestBody EstadoActivoRequest request){return service.estadoCliente(id,request.activo());}
}
