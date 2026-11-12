package com.techsupply.erp_backend.maestros.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.dto.*;
import com.techsupply.erp_backend.maestros.domain.model.Almacen;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.AlmacenRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.MasterCatalogSpecifications;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlmacenService {
    private static final Map<String, String> SORT_FIELDS = Map.of("id", "id", "codigo", "codigo", "nombre", "nombre");
    private final AlmacenRepository repository;
    private final AuditTrail auditTrail;

    public AlmacenService(AlmacenRepository repository, AuditTrail auditTrail) {
        this.repository = repository;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public AlmacenResponse crear(AlmacenWriteRequest request) {
        String codigo = MasterCatalogText.code(request.codigo());
        if (repository.existsByCodigo(codigo)) throw duplicate();
        Almacen entity = new Almacen();
        entity.setCodigo(codigo);
        entity.setNombre(MasterCatalogText.required(request.nombre()));
        entity.setSede(MasterCatalogText.optional(request.sede()));
        entity.setUbicacion(MasterCatalogText.optional(request.ubicacion()));
        entity = repository.saveAndFlush(entity);
        auditTrail.change(AuditAction.WAREHOUSE_CREATED, entity.getId());
        return response(entity);
    }

    @Transactional(readOnly = true)
    public AlmacenResponse obtener(Long id) { return response(find(id)); }

    @Transactional(readOnly = true)
    public PageResponse<AlmacenResponse> listar(int page, int size, String sort, String direction,
            MasterCatalogSearchCriteria criteria) {
        return PageResponse.from(repository.findAll(MasterCatalogSpecifications.matching(criteria),
            PageRequests.of(page, size, sort, direction, SORT_FIELDS, "id")), AlmacenService::response);
    }

    @Transactional
    public AlmacenResponse actualizar(Long id, AlmacenWriteRequest request) {
        Almacen entity = find(id);
        String codigo = MasterCatalogText.code(request.codigo());
        if (repository.existsByCodigoAndIdNot(codigo, id)) throw duplicate();
        String nombre = MasterCatalogText.required(request.nombre());
        String sede = MasterCatalogText.optional(request.sede());
        String ubicacion = MasterCatalogText.optional(request.ubicacion());
        Set<AuditField> changed = new HashSet<>();
        if (!Objects.equals(entity.getCodigo(), codigo)) changed.add(AuditField.CODIGO);
        if (!Objects.equals(entity.getNombre(), nombre)) changed.add(AuditField.NOMBRE);
        if (!Objects.equals(entity.getSede(), sede)) changed.add(AuditField.SEDE);
        if (!Objects.equals(entity.getUbicacion(), ubicacion)) changed.add(AuditField.UBICACION);
        entity.setCodigo(codigo);
        entity.setNombre(nombre);
        entity.setSede(sede);
        entity.setUbicacion(ubicacion);
        entity = repository.saveAndFlush(entity);
        if (!changed.isEmpty()) auditTrail.change(AuditAction.WAREHOUSE_UPDATED, id, changed, null, null);
        return response(entity);
    }

    @Transactional
    public AlmacenResponse cambiarEstado(Long id, boolean activo) {
        Almacen entity = find(id);
        boolean before = entity.isActivo();
        entity.setActivo(activo);
        entity = repository.saveAndFlush(entity);
        if (before != activo) auditTrail.change(AuditAction.WAREHOUSE_STATUS_CHANGED, id, Set.of(AuditField.ACTIVO),
            before ? AuditState.ACTIVO : AuditState.INACTIVO, activo ? AuditState.ACTIVO : AuditState.INACTIVO);
        return response(entity);
    }

    private Almacen find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Almacén no encontrado"));
    }

    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "El código de almacén ya existe");
    }

    private static AlmacenResponse response(Almacen entity) {
        return new AlmacenResponse(entity.getId(), entity.getCodigo(), entity.getNombre(), entity.getSede(), entity.getUbicacion(), entity.isActivo());
    }
}
