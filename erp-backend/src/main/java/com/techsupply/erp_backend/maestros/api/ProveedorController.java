package com.techsupply.erp_backend.maestros.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.MaestroOperativoService;
import com.techsupply.erp_backend.maestros.application.dto.EstadoActivoRequest;
import com.techsupply.erp_backend.maestros.application.dto.ProveedorRequest;
import com.techsupply.erp_backend.maestros.application.dto.ProveedorResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/proveedores")
public class ProveedorController {
    private final MaestroOperativoService service;
    public ProveedorController(MaestroOperativoService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('MAESTROS_PROVEEDORES_LEER')")
    public PageResponse<ProveedorResponse> listar(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="codigo")String sort,@RequestParam(defaultValue="asc")String direction,@RequestParam(required=false)String search,@RequestParam(required=false)Boolean activo){return service.listarProveedores(page,size,sort,direction,search,activo);}
    @PostMapping @PreAuthorize("hasAuthority('PROVEEDOR_WRITE')")
    public ResponseEntity<ProveedorResponse> crear(@Valid @RequestBody ProveedorRequest request){ProveedorResponse r=service.crearProveedor(request);return ResponseEntity.created(URI.create("/api/v1/proveedores/"+r.id())).body(r);}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('PROVEEDOR_WRITE')")
    public ProveedorResponse actualizar(@PathVariable Long id,@Valid @RequestBody ProveedorRequest request){return service.actualizarProveedor(id,request);}
    @PatchMapping("/{id}/estado") @PreAuthorize("hasAuthority('PROVEEDOR_WRITE')")
    public ProveedorResponse estado(@PathVariable Long id,@Valid @RequestBody EstadoActivoRequest request){return service.estadoProveedor(id,request.activo());}
}
