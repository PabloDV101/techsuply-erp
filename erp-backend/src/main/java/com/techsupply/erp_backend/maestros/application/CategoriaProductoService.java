package com.techsupply.erp_backend.maestros.application;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditField;
import com.techsupply.erp_backend.audit.application.AuditState;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.dto.ActualizarCategoriaRequest;
import com.techsupply.erp_backend.maestros.application.dto.CategoriaResponse;
import com.techsupply.erp_backend.maestros.application.dto.CategoriaSearchCriteria;
import com.techsupply.erp_backend.maestros.application.dto.CrearCategoriaRequest;
import com.techsupply.erp_backend.maestros.domain.model.CategoriaProducto;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.CategoriaProductoRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.CategoriaProductoSpecifications;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CategoriaProductoService {
    private static final Map<String, String> SORT_FIELDS = Map.of(
        "id", "id", "codigo", "codigo", "nombre", "nombre");
    private final CategoriaProductoRepository repository;
    private final AuditTrail auditTrail;

    public CategoriaProductoService(CategoriaProductoRepository repository, AuditTrail auditTrail) {
        this.repository = repository;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public CategoriaResponse crear(CrearCategoriaRequest request) {
        repository.bloquearJerarquia();
        String codigo = normalizarCodigo(request.codigo());
        if (repository.existsByCodigo(codigo)) throw conflictoCodigo();
        CategoriaProducto categoria = new CategoriaProducto();
        categoria.setCodigo(codigo);
        categoria.setNombre(request.nombre().trim());
        categoria.setDescripcion(normalizarOpcional(request.descripcion()));
        categoria.setCategoriaPadre(buscarPadre(request.categoriaPadreId()));
        CategoriaProducto guardada = repository.saveAndFlush(categoria);
        auditTrail.change(AuditAction.CATEGORY_CREATED, guardada.getId());
        return response(guardada);
    }

    @Transactional(readOnly = true)
    public CategoriaResponse obtener(Long id) {
        return response(buscar(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoriaResponse> listar(int page, int size, String sort, String direction,
            CategoriaSearchCriteria criteria) {
        return PageResponse.from(repository.findAll(CategoriaProductoSpecifications.matching(criteria),
                PageRequests.of(page, size, sort, direction, SORT_FIELDS, "id")),
            CategoriaProductoService::response);
    }

    @Transactional
    public CategoriaResponse actualizar(Long id, ActualizarCategoriaRequest request) {
        repository.bloquearJerarquia();
        CategoriaProducto categoria = buscar(id);
        String codigo = normalizarCodigo(request.codigo());
        if (repository.existsByCodigoAndIdNot(codigo, id)) throw conflictoCodigo();
        CategoriaProducto padre = buscarPadre(request.categoriaPadreId());
        verificarSinCiclo(categoria, padre);
        String nombre = request.nombre().trim();
        String descripcion = normalizarOpcional(request.descripcion());
        Set<AuditField> cambiados = new HashSet<>();
        if (!Objects.equals(categoria.getCodigo(), codigo)) cambiados.add(AuditField.CODIGO);
        if (!Objects.equals(categoria.getNombre(), nombre)) cambiados.add(AuditField.NOMBRE);
        if (!Objects.equals(categoria.getDescripcion(), descripcion)) cambiados.add(AuditField.DESCRIPCION);
        if (!Objects.equals(categoria.getCategoriaPadre() == null ? null : categoria.getCategoriaPadre().getId(),
                padre == null ? null : padre.getId())) cambiados.add(AuditField.CATEGORIA_PADRE);
        categoria.setCodigo(codigo);
        categoria.setNombre(nombre);
        categoria.setDescripcion(descripcion);
        categoria.setCategoriaPadre(padre);
        CategoriaProducto guardada = repository.saveAndFlush(categoria);
        if (!cambiados.isEmpty()) auditTrail.change(AuditAction.CATEGORY_UPDATED, id, cambiados, null, null);
        return response(guardada);
    }

    @Transactional
    public CategoriaResponse cambiarEstado(Long id, boolean activa) {
        repository.bloquearJerarquia();
        CategoriaProducto categoria = buscar(id);
        boolean anterior = categoria.isActiva();
        categoria.setActiva(activa);
        CategoriaProducto guardada = repository.saveAndFlush(categoria);
        if (anterior != activa) auditTrail.change(AuditAction.CATEGORY_STATUS_CHANGED, id, Set.of(AuditField.ACTIVA),
            anterior ? AuditState.ACTIVO : AuditState.INACTIVO, activa ? AuditState.ACTIVO : AuditState.INACTIVO);
        return response(guardada);
    }

    private CategoriaProducto buscar(Long id) {
        return repository.findById(id).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Categoría no encontrada"));
    }

    private CategoriaProducto buscarPadre(Long id) {
        return id == null ? null : buscar(id);
    }

    private static void verificarSinCiclo(CategoriaProducto categoria, CategoriaProducto padre) {
        Set<Long> visitados = new HashSet<>();
        while (padre != null) {
            if (padre.getId().equals(categoria.getId()) || !visitados.add(padre.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "La jerarquía de categorías no puede contener ciclos");
            }
            padre = padre.getCategoriaPadre();
        }
    }

    private static String normalizarCodigo(String codigo) {
        return codigo.trim().toUpperCase(Locale.ROOT);
    }

    private static String normalizarOpcional(String value) {
        return value == null ? null : value.trim();
    }

    private static ResponseStatusException conflictoCodigo() {
        return new ResponseStatusException(HttpStatus.CONFLICT, "El código de categoría ya existe");
    }

    private static CategoriaResponse response(CategoriaProducto categoria) {
        CategoriaProducto padre = categoria.getCategoriaPadre();
        return new CategoriaResponse(categoria.getId(), categoria.getCodigo(), categoria.getNombre(),
            categoria.getDescripcion(), padre == null ? null : padre.getId(), categoria.isActiva());
    }
}
