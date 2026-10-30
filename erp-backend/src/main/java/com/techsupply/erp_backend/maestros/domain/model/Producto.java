package com.techsupply.erp_backend.maestros.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "productos")
public class Producto {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 30, unique = true)
    private String sku;

    @Column(nullable = false, length = 160)
    private String nombre;

    @Column(columnDefinition = "text")
    private String descripcion;

    @Column(name = "unidad_medida", nullable = false, length = 20)
    private String unidadMedida = "PIEZA";

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false)
    private CategoriaProducto categoria;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "proveedor_principal_id")
    private Proveedor proveedorPrincipal;

    @Column(name = "precio_lista", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioLista = BigDecimal.ZERO;

    @Column(name = "costo_referencia", nullable = false, precision = 14, scale = 2)
    private BigDecimal costoReferencia = BigDecimal.ZERO;

    @Column(name = "tasa_impuesto", nullable = false, precision = 5, scale = 2)
    private BigDecimal tasaImpuesto = new BigDecimal("16.00");

    @Column(name = "punto_reorden", nullable = false, precision = 14, scale = 3)
    private BigDecimal puntoReorden = BigDecimal.ZERO;

    @Column(name = "cantidad_reorden", nullable = false, precision = 14, scale = 3)
    private BigDecimal cantidadReorden = BigDecimal.ONE;

    @Column(nullable = false)
    private boolean activo = true;

    public Long getId() { return id; }
    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }
    public CategoriaProducto getCategoria() { return categoria; }
    public void setCategoria(CategoriaProducto categoria) { this.categoria = categoria; }
    public Proveedor getProveedorPrincipal() { return proveedorPrincipal; }
    public void setProveedorPrincipal(Proveedor proveedorPrincipal) { this.proveedorPrincipal = proveedorPrincipal; }
    public BigDecimal getPrecioLista() { return precioLista; }
    public void setPrecioLista(BigDecimal precioLista) { this.precioLista = precioLista; }
    public BigDecimal getCostoReferencia() { return costoReferencia; }
    public void setCostoReferencia(BigDecimal costoReferencia) { this.costoReferencia = costoReferencia; }
    public BigDecimal getTasaImpuesto() { return tasaImpuesto; }
    public void setTasaImpuesto(BigDecimal tasaImpuesto) { this.tasaImpuesto = tasaImpuesto; }
    public BigDecimal getPuntoReorden() { return puntoReorden; }
    public void setPuntoReorden(BigDecimal puntoReorden) { this.puntoReorden = puntoReorden; }
    public BigDecimal getCantidadReorden() { return cantidadReorden; }
    public void setCantidadReorden(BigDecimal cantidadReorden) { this.cantidadReorden = cantidadReorden; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
