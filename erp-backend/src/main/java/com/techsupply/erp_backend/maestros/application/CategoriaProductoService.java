package com.techsupply.erp_backend.maestros.application;

import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.maestros.application.dto.ActualizarCategoriaRequest;
import com.techsupply.erp_backend.maestros.application.dto.CategoriaResponse;
import com.techsupply.erp_backend.maestros.application.dto.CrearCategoriaRequest;
import com.techsupply.erp_backend.maestros.domain.model.CategoriaProducto;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.CategoriaProductoRepository;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
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

    public CategoriaProductoService(CategoriaProductoRepository repository) {
        this.repository = repository;
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
        return response(repository.saveAndFlush(categoria));
    }

    @Transactional(readOnly = true)
    public CategoriaResponse obtener(Long id) {
        return response(buscar(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<CategoriaResponse> listar(int page, int size, String sort, String direction) {
        return PageResponse.from(repository.findAll(PageRequests.of(page, size, sort, direction, SORT_FIELDS)),
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
        categoria.setCodigo(codigo);
        categoria.setNombre(request.nombre().trim());
        categoria.setDescripcion(normalizarOpcional(request.descripcion()));
        categoria.setCategoriaPadre(padre);
        return response(repository.saveAndFlush(categoria));
    }

    @Transactional
    public CategoriaResponse cambiarEstado(Long id, boolean activa) {
        repository.bloquearJerarquia();
        CategoriaProducto categoria = buscar(id);
        categoria.setActiva(activa);
        return response(repository.saveAndFlush(categoria));
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
