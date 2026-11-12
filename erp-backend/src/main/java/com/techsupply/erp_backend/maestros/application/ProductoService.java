package com.techsupply.erp_backend.maestros.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.*;
import com.techsupply.erp_backend.maestros.application.dto.*;
import com.techsupply.erp_backend.maestros.domain.model.*;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.*;
import java.util.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ProductoService {
    private final ProductoRepository products;
    private final CategoriaProductoRepository categories;
    private final ProveedorRepository suppliers;
    private final AuditTrail audit;
    public ProductoService(ProductoRepository products, CategoriaProductoRepository categories, ProveedorRepository suppliers, AuditTrail audit) {
        this.products = products; this.categories = categories; this.suppliers = suppliers; this.audit = audit;
    }
    @Transactional
    public ProductoResponse crear(ProductoWriteRequest request) {
        String sku = MasterCatalogText.code(request.sku());
        if (products.existsBySku(sku)) throw conflict();
        Producto product = new Producto();
        assign(product, request, sku);
        product = products.saveAndFlush(product);
        audit.change(AuditAction.PRODUCT_CREATED, product.getId());
        return response(product);
    }
    @Transactional(readOnly = true)
    public ProductoResponse obtener(Long id) { return response(find(id)); }
    @Transactional(readOnly = true)
    public PageResponse<ProductoResponse> listar(int page, int size, String sort, String direction, String sku, Long categoriaId, String activo) {
        String code = sku == null ? null : MasterCatalogText.code(sku);
        if (code != null && (code.isEmpty() || code.length() > 30)) throw bad();
        if (categoriaId != null && categoriaId < 1) throw bad();
        if (activo != null && !activo.equals("true") && !activo.equals("false")) throw bad();
        Specification<Producto> spec = (root, query, cb) -> {
            var conditions = new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (code != null) conditions.add(cb.equal(root.get("sku"), code));
            if (categoriaId != null) conditions.add(cb.equal(root.get("categoria").get("id"), categoriaId));
            if (activo != null) conditions.add(cb.equal(root.get("activo"), Boolean.valueOf(activo)));
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        return PageResponse.from(products.findAll(spec, PageRequests.of(page, size, sort, direction,
            Map.of("id", "id", "sku", "sku", "nombre", "nombre"), "id")), ProductoService::response);
    }
    @Transactional
    public ProductoResponse actualizar(Long id, ProductoWriteRequest request) {
        Producto product = find(id);
        String sku = MasterCatalogText.code(request.sku());
        if (products.existsBySkuAndIdNot(sku, id)) throw conflict();
        ProductoResponse before = response(product);
        assign(product, request, sku);
        ProductoResponse after = response(product);
        Set<AuditField> fields = differences(before, after);
        product = products.saveAndFlush(product);
        if (!fields.isEmpty()) audit.change(AuditAction.PRODUCT_UPDATED, id, fields, null, null);
        return response(product);
    }
    @Transactional
    public ProductoResponse cambiarEstado(Long id, boolean activo) {
        Producto product = find(id);
        boolean before = product.isActivo();
        product.setActivo(activo);
        product = products.saveAndFlush(product);
        if (before != activo) audit.change(AuditAction.PRODUCT_STATUS_CHANGED, id, Set.of(AuditField.ACTIVO),
            before ? AuditState.ACTIVO : AuditState.INACTIVO, activo ? AuditState.ACTIVO : AuditState.INACTIVO);
        return response(product);
    }
    private void assign(Producto p, ProductoWriteRequest r, String sku) {
        CategoriaProducto category = categories.findById(r.categoriaId()).orElseThrow(() -> missing("Categoría"));
        Proveedor supplier = r.proveedorPrincipalId() == null ? null : suppliers.findById(r.proveedorPrincipalId()).orElseThrow(() -> missing("Proveedor"));
        p.setSku(sku); p.setNombre(MasterCatalogText.required(r.nombre())); p.setDescripcion(MasterCatalogText.optional(r.descripcion()));
        p.setUnidadMedida(MasterCatalogText.required(r.unidadMedida())); p.setCategoria(category); p.setProveedorPrincipal(supplier);
        p.setPrecioLista(r.precioLista()); p.setCostoReferencia(r.costoReferencia()); p.setTasaImpuesto(r.tasaImpuesto());
        p.setPuntoReorden(r.puntoReorden()); p.setCantidadReorden(r.cantidadReorden());
    }
    private static Set<AuditField> differences(ProductoResponse a, ProductoResponse b) {
        Set<AuditField> fields = new HashSet<>();
        if (!Objects.equals(a.sku(), b.sku())) fields.add(AuditField.SKU);
        if (!Objects.equals(a.nombre(), b.nombre())) fields.add(AuditField.NOMBRE);
        if (!Objects.equals(a.descripcion(), b.descripcion())) fields.add(AuditField.DESCRIPCION);
        if (!Objects.equals(a.unidadMedida(), b.unidadMedida())) fields.add(AuditField.UNIDAD_MEDIDA);
        if (!Objects.equals(a.categoriaId(), b.categoriaId())) fields.add(AuditField.CATEGORIA);
        if (!Objects.equals(a.proveedorPrincipalId(), b.proveedorPrincipalId())) fields.add(AuditField.PROVEEDOR_PRINCIPAL);
        if (a.precioLista().compareTo(b.precioLista()) != 0) fields.add(AuditField.PRECIO_LISTA);
        if (a.costoReferencia().compareTo(b.costoReferencia()) != 0) fields.add(AuditField.COSTO_REFERENCIA);
        if (a.tasaImpuesto().compareTo(b.tasaImpuesto()) != 0) fields.add(AuditField.TASA_IMPUESTO);
        if (a.puntoReorden().compareTo(b.puntoReorden()) != 0) fields.add(AuditField.PUNTO_REORDEN);
        if (a.cantidadReorden().compareTo(b.cantidadReorden()) != 0) fields.add(AuditField.CANTIDAD_REORDEN);
        return fields;
    }
    private Producto find(Long id) { return products.findById(id).orElseThrow(() -> missing("Producto")); }
    private static ResponseStatusException missing(String name) { return new ResponseStatusException(HttpStatus.NOT_FOUND, name + " no encontrado"); }
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT, "El SKU ya existe"); }
    private static ResponseStatusException bad() { return new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de producto no válido"); }
    private static ProductoResponse response(Producto p) {
        return new ProductoResponse(p.getId(), p.getSku(), p.getNombre(), p.getDescripcion(), p.getUnidadMedida(),
            p.getCategoria().getId(), p.getProveedorPrincipal() == null ? null : p.getProveedorPrincipal().getId(),
            p.getPrecioLista(), p.getCostoReferencia(), p.getTasaImpuesto(), p.getPuntoReorden(), p.getCantidadReorden(), p.isActivo());
    }
}
