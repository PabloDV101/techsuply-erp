package com.techsupply.erp_backend.contabilidad.api;

import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.ContabilidadService;
import com.techsupply.erp_backend.contabilidad.application.dto.AsientoContableRequest;
import com.techsupply.erp_backend.contabilidad.application.dto.AsientoContableResponse;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/asientos-contables")
public class AsientoContableController {
    private final ContabilidadService service;
    public AsientoContableController(ContabilidadService service){this.service=service;}
    @GetMapping @PreAuthorize("hasAuthority('CONTABILIDAD_LEER')")
    public PageResponse<AsientoContableResponse> listar(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(defaultValue="folio")String sort,@RequestParam(defaultValue="asc")String direction,@RequestParam(required=false)Long periodoId,@RequestParam(required=false)String estado,@RequestParam(required=false)String search){return service.listarAsientos(page,size,sort,direction,periodoId,estado,search);}
    @GetMapping("/{id}") @PreAuthorize("hasAuthority('CONTABILIDAD_LEER')") public AsientoContableResponse obtener(@PathVariable Long id){return service.obtenerAsiento(id);}
    @PostMapping @PreAuthorize("hasAuthority('CONTABILIDAD_ADMIN')") public ResponseEntity<AsientoContableResponse> crear(@Valid @RequestBody AsientoContableRequest r,@RequestHeader(value="Idempotency-Key",required=false)String idempotencyKey){if((r.claveIdempotencia()==null||r.claveIdempotencia().isBlank())&&idempotencyKey!=null){r=new AsientoContableRequest(r.fechaContable(),r.descripcion(),r.origenModulo(),r.origenTipo(),r.origenId(),idempotencyKey,r.partidas());}var v=service.crearAsiento(r);return ResponseEntity.created(URI.create("/api/v1/asientos-contables/"+v.id())).body(v);}
    @PostMapping("/{id}/contabilizar") @PreAuthorize("hasAuthority('CONTABILIDAD_ADMIN')") public AsientoContableResponse contabilizar(@PathVariable Long id){return service.contabilizar(id);}
    @PostMapping("/{id}/revertir") @PreAuthorize("hasAuthority('CONTABILIDAD_ADMIN')") public AsientoContableResponse revertir(@PathVariable Long id){return service.revertir(id);}
}
