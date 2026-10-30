package com.techsupply.erp_backend.maestros.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.dto.*;
import com.techsupply.erp_backend.maestros.domain.model.Proveedor;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.MasterCatalogSpecifications;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.ProveedorRepository;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProveedorService {
    private static final Map<String, String> SORT_FIELDS = Map.of("id", "id", "codigo", "codigo", "nombreRazonSocial", "nombreRazonSocial");
    private final ProveedorRepository repository;
    private final AuditTrail auditTrail;

    public ProveedorService(ProveedorRepository repository, AuditTrail auditTrail) {
        this.repository = repository;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public ProveedorResponse crear(ProveedorWriteRequest request) {
        String codigo = MasterCatalogText.code(request.codigo());
        if (repository.existsByCodigo(codigo)) throw duplicate();
        String fiscal = MasterCatalogText.optional(request.identificadorFiscal());
        if (fiscal != null && repository.existsByIdentificadorFiscal(fiscal)) throw duplicateFiscal();
        Proveedor entity = new Proveedor();
        assign(entity, request, codigo);
        entity = repository.saveAndFlush(entity);
        auditTrail.change(AuditAction.SUPPLIER_CREATED, entity.getId());
        return response(entity);
    }

    @Transactional(readOnly = true)
    public ProveedorResponse obtener(Long id) { return response(find(id)); }

    @Transactional(readOnly = true)
    public PageResponse<ProveedorResponse> listar(int page, int size, String sort, String direction,
            MasterCatalogSearchCriteria criteria) {
        return PageResponse.from(repository.findAll(MasterCatalogSpecifications.matching(criteria),
            PageRequests.of(page, size, sort, direction, SORT_FIELDS, "id")), ProveedorService::response);
    }

    @Transactional
    public ProveedorResponse actualizar(Long id, ProveedorWriteRequest request) {
        Proveedor entity = find(id);
        String codigo = MasterCatalogText.code(request.codigo());
        if (repository.existsByCodigoAndIdNot(codigo, id)) throw duplicate();
        String fiscal = MasterCatalogText.optional(request.identificadorFiscal());
        if (fiscal != null && repository.existsByIdentificadorFiscalAndIdNot(fiscal, id)) throw duplicateFiscal();
        Set<AuditField> changed = differences(entity, request, codigo);
        assign(entity, request, codigo);
        entity = repository.saveAndFlush(entity);
        if (!changed.isEmpty()) auditTrail.change(AuditAction.SUPPLIER_UPDATED, id, changed, null, null);
        return response(entity);
    }

    @Transactional
    public ProveedorResponse cambiarEstado(Long id, boolean activo) {
        Proveedor entity = find(id);
        boolean before = entity.isActivo();
        entity.setActivo(activo);
        entity = repository.saveAndFlush(entity);
        if (before != activo) auditTrail.change(AuditAction.SUPPLIER_STATUS_CHANGED, id, Set.of(AuditField.ACTIVO),
            before ? AuditState.ACTIVO : AuditState.INACTIVO, activo ? AuditState.ACTIVO : AuditState.INACTIVO);
        return response(entity);
    }

    private static void assign(Proveedor entity, ProveedorWriteRequest request, String codigo) {
        entity.setCodigo(codigo);
        entity.setNombreRazonSocial(MasterCatalogText.required(request.nombreRazonSocial()));
        entity.setIdentificadorFiscal(MasterCatalogText.optional(request.identificadorFiscal()));
        entity.setContacto(MasterCatalogText.optional(request.contacto()));
        entity.setCorreo(MasterCatalogText.optional(request.correo()));
        entity.setTelefono(MasterCatalogText.optional(request.telefono()));
        entity.setDireccion(MasterCatalogText.optional(request.direccion()));
        entity.setPlazoCreditoDias(request.plazoCreditoDias() == null ? (short) 0 : request.plazoCreditoDias());
    }

    private static Set<AuditField> differences(Proveedor entity, ProveedorWriteRequest request, String codigo) {
        Set<AuditField> changed = new HashSet<>();
        if (!Objects.equals(entity.getCodigo(), codigo)) changed.add(AuditField.CODIGO);
        if (!Objects.equals(entity.getNombreRazonSocial(), MasterCatalogText.required(request.nombreRazonSocial()))) changed.add(AuditField.NOMBRE);
        if (!Objects.equals(entity.getIdentificadorFiscal(), MasterCatalogText.optional(request.identificadorFiscal()))) changed.add(AuditField.IDENTIFICADOR_FISCAL);
        if (!Objects.equals(entity.getContacto(), MasterCatalogText.optional(request.contacto()))) changed.add(AuditField.CONTACTO);
        if (!Objects.equals(entity.getCorreo(), MasterCatalogText.optional(request.correo()))) changed.add(AuditField.CORREO);
        if (!Objects.equals(entity.getTelefono(), MasterCatalogText.optional(request.telefono()))) changed.add(AuditField.TELEFONO);
        if (!Objects.equals(entity.getDireccion(), MasterCatalogText.optional(request.direccion()))) changed.add(AuditField.DIRECCION);
        short plazo = request.plazoCreditoDias() == null ? 0 : request.plazoCreditoDias();
        if (entity.getPlazoCreditoDias() != plazo) changed.add(AuditField.PLAZO_CREDITO_DIAS);
        return changed;
    }

    private Proveedor find(Long id) {
        return repository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Proveedor no encontrado"));
    }

    private static ResponseStatusException duplicate() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "El código de proveedor ya existe");
    }

    private static ResponseStatusException duplicateFiscal() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "El identificador fiscal de proveedor ya existe");
    }

    private static ProveedorResponse response(Proveedor entity) {
        return new ProveedorResponse(entity.getId(), entity.getCodigo(), entity.getNombreRazonSocial(),
            entity.getIdentificadorFiscal(), entity.getContacto(), entity.getCorreo(), entity.getTelefono(),
            entity.getDireccion(), entity.getPlazoCreditoDias(), entity.isActivo());
    }
}
