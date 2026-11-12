package com.techsupply.erp_backend.maestros.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.dto.*;
import com.techsupply.erp_backend.maestros.domain.model.Departamento;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.DepartamentoRepository;
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
public class DepartamentoService {
    private static final Map<String, String> SORT_FIELDS = Map.of("id", "id", "codigo", "codigo", "nombre", "nombre");
    private final DepartamentoRepository repository;
    private final AuditTrail auditTrail;

    public DepartamentoService(DepartamentoRepository repository, AuditTrail auditTrail) {
        this.repository = repository;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public DepartamentoResponse crear(DepartamentoWriteRequest request) {
        String codigo = MasterCatalogText.code(request.codigo());
        if (repository.existsByCodigo(codigo)) throw duplicate();
        Departamento entity = new Departamento();
        entity.setCodigo(codigo);
        entity.setNombre(MasterCatalogText.required(request.nombre()));
        entity = repository.saveAndFlush(entity);
        auditTrail.change(AuditAction.DEPARTMENT_CREATED, entity.getId());
        return response(entity);
    }

    @Transactional(readOnly = true)
    public DepartamentoResponse obtener(Long id) { return response(find(id)); }

    @Transactional(readOnly = true)
    public PageResponse<DepartamentoResponse> listar(int page, int size, String sort, String direction,
            MasterCatalogSearchCriteria criteria) {
        return PageResponse.from(repository.findAll(MasterCatalogSpecifications.matching(criteria),
            PageRequests.of(page, size, sort, direction, SORT_FIELDS, "id")), DepartamentoService::response);
    }

    @Transactional
    public DepartamentoResponse actualizar(Long id, DepartamentoWriteRequest request) {
        Departamento entity = find(id);
        String codigo = MasterCatalogText.code(request.codigo());
        if (repository.existsByCodigoAndIdNot(codigo, id)) throw duplicate();
        String nombre = MasterCatalogText.required(request.nombre());
        Set<AuditField> changed = new HashSet<>();
        if (!Objects.equals(entity.getCodigo(), codigo)) changed.add(AuditField.CODIGO);
        if (!Objects.equals(entity.getNombre(), nombre)) changed.add(AuditField.NOMBRE);
        entity.setCodigo(codigo);
        entity.setNombre(nombre);
        entity = repository.saveAndFlush(entity);
        if (!changed.isEmpty()) auditTrail.change(AuditAction.DEPARTMENT_UPDATED, id, changed, null, null);
        return response(entity);
    }

    @Transactional
    public DepartamentoResponse cambiarEstado(Long id, boolean activo) {
        Departamento entity = find(id);
        boolean before = entity.isActivo();
        entity.setActivo(activo);
        entity = repository.saveAndFlush(entity);
        if (before != activo) auditTrail.change(AuditAction.DEPARTMENT_STATUS_CHANGED, id, Set.of(AuditField.ACTIVO),
            before ? AuditState.ACTIVO : AuditState.INACTIVO, activo ? AuditState.ACTIVO : AuditState.INACTIVO);
        return response(entity);
    }

    private Departamento find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Departamento no encontrado"));
    }

    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "El código de departamento ya existe");
    }

    private static DepartamentoResponse response(Departamento entity) {
        return new DepartamentoResponse(entity.getId(), entity.getCodigo(), entity.getNombre(), entity.isActivo());
    }
}
