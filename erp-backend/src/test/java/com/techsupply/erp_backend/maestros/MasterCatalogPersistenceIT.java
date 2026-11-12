package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.maestros.domain.model.Almacen;
import com.techsupply.erp_backend.maestros.domain.model.CategoriaProducto;
import com.techsupply.erp_backend.maestros.domain.model.Departamento;
import com.techsupply.erp_backend.maestros.domain.model.Producto;
import com.techsupply.erp_backend.maestros.domain.model.Proveedor;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.AlmacenRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.CategoriaProductoRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.DepartamentoRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.ProductoRepository;
import com.techsupply.erp_backend.maestros.infrastructure.persistence.ProveedorRepository;
import com.techsupply.erp_backend.security.PostgresIntegrationSupport;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class MasterCatalogPersistenceIT extends PostgresIntegrationSupport {
    @Autowired EntityManager entityManager;
    @Autowired DepartamentoRepository departamentos;
    @Autowired ProveedorRepository proveedores;
    @Autowired AlmacenRepository almacenes;
    @Autowired CategoriaProductoRepository categorias;
    @Autowired ProductoRepository productos;

    private static String code() {
        return "M" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(Locale.ROOT);
    }

    private CategoriaProducto category() {
        CategoriaProducto category = new CategoriaProducto();
        category.setCodigo(code());
        category.setNombre("Componentes");
        return categorias.saveAndFlush(category);
    }

    @Test
    @Transactional
    void persistsAndReloadsAllFourCatalogsWithProductReferencesAndPreciseNumbers() {
        Departamento department = new Departamento();
        department.setCodigo(code());
        department.setNombre("Compras");
        long departmentId = departamentos.saveAndFlush(department).getId();

        Almacen warehouse = new Almacen();
        warehouse.setCodigo(code());
        warehouse.setNombre("Central");
        warehouse.setSede("Oficina principal");
        warehouse.setUbicacion("Pasillo A");
        long warehouseId = almacenes.saveAndFlush(warehouse).getId();

        Proveedor supplier = new Proveedor();
        supplier.setCodigo(code());
        supplier.setNombreRazonSocial("Proveedor de prueba");
        supplier.setIdentificadorFiscal(code());
        supplier.setContacto("Contacto de prueba");
        supplier.setCorreo("contacto@example.test");
        supplier.setTelefono("5550000000");
        supplier.setDireccion("Dirección de prueba");
        supplier.setPlazoCreditoDias((short) 30);
        long supplierId = proveedores.saveAndFlush(supplier).getId();

        CategoriaProducto category = category();
        Producto product = new Producto();
        product.setSku(code());
        product.setNombre("Componente de prueba");
        product.setDescripcion("Descripción de prueba");
        product.setUnidadMedida("PIEZA");
        product.setCategoria(category);
        product.setProveedorPrincipal(supplier);
        product.setPrecioLista(new BigDecimal("123.45"));
        product.setCostoReferencia(new BigDecimal("98.76"));
        product.setTasaImpuesto(new BigDecimal("16.00"));
        product.setPuntoReorden(new BigDecimal("7.500"));
        product.setCantidadReorden(new BigDecimal("12.250"));
        long productId = productos.saveAndFlush(product).getId();

        entityManager.clear();

        assertThat(departamentos.findById(departmentId).orElseThrow().getNombre()).isEqualTo("Compras");
        assertThat(almacenes.findById(warehouseId).orElseThrow().getSede()).isEqualTo("Oficina principal");
        Proveedor reloadedSupplier = proveedores.findById(supplierId).orElseThrow();
        assertThat(reloadedSupplier.getDireccion()).isEqualTo("Dirección de prueba");
        assertThat(reloadedSupplier.getPlazoCreditoDias()).isEqualTo((short) 30);
        Producto reloadedProduct = productos.findById(productId).orElseThrow();
        assertThat(reloadedProduct.getCategoria().getId()).isEqualTo(category.getId());
        assertThat(reloadedProduct.getProveedorPrincipal().getId()).isEqualTo(supplierId);
        assertThat(reloadedProduct.getPrecioLista()).isEqualByComparingTo("123.45");
        assertThat(reloadedProduct.getCostoReferencia()).isEqualByComparingTo("98.76");
        assertThat(reloadedProduct.getPuntoReorden()).isEqualByComparingTo("7.500");
        assertThat(reloadedProduct.getCantidadReorden()).isEqualByComparingTo("12.250");
    }

    @Test
    @Transactional
    void productMayLackPrincipalSupplierAndKeepsReferenceWhenSupplierIsDeactivated() {
        CategoriaProducto category = category();
        Producto withoutSupplier = new Producto();
        withoutSupplier.setSku(code());
        withoutSupplier.setNombre("Sin proveedor principal");
        withoutSupplier.setCategoria(category);
        long withoutSupplierId = productos.saveAndFlush(withoutSupplier).getId();

        Proveedor supplier = new Proveedor();
        supplier.setCodigo(code());
        supplier.setNombreRazonSocial("Proveedor histórico");
        supplier = proveedores.saveAndFlush(supplier);
        Producto withSupplier = new Producto();
        withSupplier.setSku(code());
        withSupplier.setNombre("Con proveedor principal");
        withSupplier.setCategoria(category);
        withSupplier.setProveedorPrincipal(supplier);
        long withSupplierId = productos.saveAndFlush(withSupplier).getId();

        supplier.setActivo(false);
        proveedores.saveAndFlush(supplier);
        entityManager.clear();

        assertThat(productos.findById(withoutSupplierId).orElseThrow().getProveedorPrincipal()).isNull();
        Producto historical = productos.findById(withSupplierId).orElseThrow();
        assertThat(historical.getProveedorPrincipal().isActivo()).isFalse();
        assertThat(historical.getCategoria().getId()).isEqualTo(category.getId());
    }
}
