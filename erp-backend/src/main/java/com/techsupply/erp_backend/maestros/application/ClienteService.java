package com.techsupply.erp_backend.maestros.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.dto.*;
import com.techsupply.erp_backend.maestros.domain.model.Cliente;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.ClienteRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.MasterCatalogSpecifications;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
public class ClienteService {
    private final ClienteRepository clientes;
    private final AuditTrail audit;
    public ClienteService(ClienteRepository clientes, AuditTrail audit) { this.clientes = clientes; this.audit = audit; }

    @Transactional
    @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_ADMIN')")
    public ClienteResponse crear(@NotNull @Valid ClienteWriteRequest request) {
        requireCreditPermission(request.limiteCredito().signum() != 0);
        String code = normalizeCode(request.codigo());
        String fiscal = MasterCatalogText.optional(request.identificadorFiscal());
        if (clientes.existsByCodigo(code) || fiscal != null && clientes.existsByIdentificadorFiscal(fiscal)) throw duplicate();
        Cliente cliente = new Cliente(code, request.nombreRazonSocial());
        assign(cliente, request, code, fiscal);
        cliente = clientes.save(cliente); clientes.flush();
        audit.change(AuditAction.CUSTOMER_CREATED, cliente.getId());
        return response(cliente);
    }
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_LEER')")
    public ClienteResponse obtener(Long id) { return response(find(id)); }
    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_LEER')")
    public PageResponse<ClienteResponse> listar(int page, int size, MasterCatalogSearchCriteria criteria) {
        var pageable = PageRequests.of(page, size, "id", "asc", Map.of("id", "id"));
        return PageResponse.from(clientes.findAll(MasterCatalogSpecifications.matching(criteria), pageable), ClienteService::response);
    }
    @Transactional
    @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_ADMIN')")
    public ClienteResponse actualizar(Long id, @NotNull @Valid ClienteWriteRequest request) {
        Cliente cliente = lock(id);
        requireCreditPermission(cliente.getLimiteCredito().compareTo(request.limiteCredito()) != 0);
        String code = normalizeCode(request.codigo());
        String fiscal = MasterCatalogText.optional(request.identificadorFiscal());
        if (clientes.existsByCodigoAndIdNot(code, id) || fiscal != null && clientes.existsByIdentificadorFiscalAndIdNot(fiscal, id)) throw duplicate();
        Set<AuditField> changes = differences(cliente, request, code, fiscal);
        assign(cliente, request, code, fiscal);
        clientes.flush();
        if (!changes.isEmpty()) audit.change(AuditAction.CUSTOMER_UPDATED, id, changes, null, null);
        return response(cliente);
    }
    @Transactional
    @PreAuthorize("hasAuthority('MAESTROS_CLIENTES_ADMIN')")
    public ClienteResponse cambiarEstado(Long id, boolean active) {
        Cliente cliente = lock(id); boolean before = cliente.isActivo();
        if (before != active) {
            cliente.cambiarEstado(active); clientes.flush();
            audit.change(AuditAction.CUSTOMER_STATUS_CHANGED, id, Set.of(AuditField.ACTIVO),
                before ? AuditState.ACTIVO : AuditState.INACTIVO, active ? AuditState.ACTIVO : AuditState.INACTIVO);
        }
        return response(cliente);
    }
    private Cliente find(Long id) {
        validId(id);
        return clientes.findById(id).orElseThrow(ClienteService::missing);
    }
    private Cliente lock(Long id) {
        validId(id);
        return clientes.bloquearPorId(id).orElseThrow(ClienteService::missing);
    }
    private static String normalizeCode(String value) {
        String code = MasterCatalogText.code(value);
        if (code.isEmpty() || code.length() > 20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Código de cliente inválido");
        return code;
    }
    private static void validId(Long id) {
        if (id == null || id < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador inválido");
    }
    private static void requireCreditPermission(boolean changed) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (changed && (authentication == null || authentication.getAuthorities().stream()
                .noneMatch(a -> a.getAuthority().equals("MAESTROS_CLIENTES_CREDITO_ADMIN"))))
            throw new AccessDeniedException("Permiso insuficiente");
    }
    private static void assign(Cliente c, ClienteWriteRequest r, String code, String fiscal) {
        c.actualizar(code, MasterCatalogText.required(r.nombreRazonSocial()), fiscal, MasterCatalogText.optional(r.correo()),
            MasterCatalogText.optional(r.telefono()), MasterCatalogText.optional(r.direccion()), r.limiteCredito(), r.plazoCreditoDias());
    }
    private static Set<AuditField> differences(Cliente c, ClienteWriteRequest r, String code, String fiscal) {
        Set<AuditField> changes = new HashSet<>();
        if (!Objects.equals(c.getCodigo(), code)) changes.add(AuditField.CODIGO);
        if (!Objects.equals(c.getNombreRazonSocial(), MasterCatalogText.required(r.nombreRazonSocial()))) changes.add(AuditField.NOMBRE);
        if (!Objects.equals(c.getIdentificadorFiscal(), fiscal)) changes.add(AuditField.IDENTIFICADOR_FISCAL);
        if (!Objects.equals(c.getCorreo(), MasterCatalogText.optional(r.correo()))) changes.add(AuditField.CORREO);
        if (!Objects.equals(c.getTelefono(), MasterCatalogText.optional(r.telefono()))) changes.add(AuditField.TELEFONO);
        if (!Objects.equals(c.getDireccion(), MasterCatalogText.optional(r.direccion()))) changes.add(AuditField.DIRECCION);
        if (c.getLimiteCredito().compareTo(r.limiteCredito()) != 0) changes.add(AuditField.LIMITE_CREDITO);
        if (c.getPlazoCreditoDias() != r.plazoCreditoDias()) changes.add(AuditField.PLAZO_CREDITO_DIAS);
        return changes;
    }
    private static ClienteResponse response(Cliente c) {
        return new ClienteResponse(c.getId(), c.getCodigo(), c.getNombreRazonSocial(), c.getIdentificadorFiscal(),
            c.getCorreo(), c.getTelefono(), c.getDireccion(), c.getLimiteCredito(), c.getPlazoCreditoDias(), c.isActivo());
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"); }
    private static ResponseStatusException duplicate() { return new ResponseStatusException(HttpStatus.CONFLICT, "Código o identificador fiscal duplicado"); }
}
