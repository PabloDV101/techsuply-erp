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
    @Column(nullable=false, length=30, unique=true) private String codigo;
    @Column(nullable=false, length=160) private String descripcion;
    @Column(nullable=false, length=20) private String unidadMedida;
    @ManyToOne(fetch=FetchType.LAZY)
    @JoinColumn(name="categoria_id", nullable=false) private CategoriaProducto categoria;
    @Column(nullable=false, precision=14, scale=2) private BigDecimal precioReferencia=BigDecimal.ZERO;
    @Column(nullable=false) private boolean activo=true;

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public String getUnidadMedida() { return unidadMedida; }
    public void setUnidadMedida(String unidadMedida) { this.unidadMedida = unidadMedida; }
    public CategoriaProducto getCategoria() { return categoria; }
    public void setCategoria(CategoriaProducto categoria) { this.categoria = categoria; }
    public BigDecimal getPrecioReferencia() { return precioReferencia; }
    public void setPrecioReferencia(BigDecimal precioReferencia) { this.precioReferencia = precioReferencia; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
