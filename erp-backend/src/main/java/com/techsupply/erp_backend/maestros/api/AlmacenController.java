package com.techsupply.erp_backend.maestros.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.MaestroOperativoService;
import com.techsupply.erp_backend.maestros.application.dto.AlmacenRequest;
import com.techsupply.erp_backend.maestros.application.dto.AlmacenResponse;
import com.techsupply.erp_backend.maestros.application.dto.EstadoActivoRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/almacenes")
public class AlmacenController {
    private final MaestroOperativoService service;
    public AlmacenController(MaestroOperativoService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('MAESTROS_ALMACENES_LEER')")
    public PageResponse<AlmacenResponse> listar(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="codigo")String sort,@RequestParam(defaultValue="asc")String direction,@RequestParam(required=false)String search,@RequestParam(required=false)Boolean activo){return service.listarAlmacenes(page,size,sort,direction,search,activo);}
    @PostMapping @PreAuthorize("hasAuthority('ALMACEN_WRITE')")
    public ResponseEntity<AlmacenResponse> crear(@Valid @RequestBody AlmacenRequest request){AlmacenResponse r=service.crearAlmacen(request);return ResponseEntity.created(URI.create("/api/v1/almacenes/"+r.id())).body(r);}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('ALMACEN_WRITE')")
    public AlmacenResponse actualizar(@PathVariable Long id,@Valid @RequestBody AlmacenRequest request){return service.actualizarAlmacen(id,request);}
    @PatchMapping("/{id}/estado") @PreAuthorize("hasAuthority('ALMACEN_WRITE')")
    public AlmacenResponse estado(@PathVariable Long id,@Valid @RequestBody EstadoActivoRequest request){return service.estadoAlmacen(id,request.activo());}
}
