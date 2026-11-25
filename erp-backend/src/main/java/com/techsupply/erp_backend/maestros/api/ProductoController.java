package com.techsupply.erp_backend.maestros.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.MaestroOperativoService;
import com.techsupply.erp_backend.maestros.application.dto.EstadoActivoRequest;
import com.techsupply.erp_backend.maestros.application.dto.ProductoRequest;
import com.techsupply.erp_backend.maestros.application.dto.ProductoResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/productos")
public class ProductoController {
    private final MaestroOperativoService service;
    public ProductoController(MaestroOperativoService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('MAESTROS_PRODUCTOS_LEER')")
    public PageResponse<ProductoResponse> listar(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="codigo")String sort,@RequestParam(defaultValue="asc")String direction,@RequestParam(required=false)String search,@RequestParam(required=false)Boolean activo){return service.listarProductos(page,size,sort,direction,search,activo);}
    @PostMapping @PreAuthorize("hasAuthority('PRODUCTO_WRITE')")
    public ResponseEntity<ProductoResponse> crear(@Valid @RequestBody ProductoRequest request){ProductoResponse r=service.crearProducto(request);return ResponseEntity.created(URI.create("/api/v1/productos/"+r.id())).body(r);}
    @PutMapping("/{id}") @PreAuthorize("hasAuthority('PRODUCTO_WRITE')")
    public ProductoResponse actualizar(@PathVariable Long id,@Valid @RequestBody ProductoRequest request){return service.actualizarProducto(id,request);}
    @PatchMapping("/{id}/estado") @PreAuthorize("hasAuthority('PRODUCTO_WRITE')")
    public ProductoResponse estado(@PathVariable Long id,@Valid @RequestBody EstadoActivoRequest request){return service.estadoProducto(id,request.activo());}
}
