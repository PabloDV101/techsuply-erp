package com.techsupply.erp_backend.inventario.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "existencias")
public class Existencia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "producto_id", nullable = false) private Long productoId;
    @Column(name = "almacen_id", nullable = false) private Long almacenId;
    @Column(name = "cantidad_fisica", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadFisica;
    @Column(name = "cantidad_reservada", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadReservada;
    @Column(name = "costo_promedio", nullable = false, precision = 14, scale = 4) private BigDecimal costoPromedio;
    @Version @Column(nullable = false) private int version;
    protected Existencia() {}
    public Long getId() { return id; }
    public Long getProductoId() { return productoId; }
    public Long getAlmacenId() { return almacenId; }
    public BigDecimal getCantidadFisica() { return cantidadFisica; }
    public BigDecimal getCantidadReservada() { return cantidadReservada; }
    public BigDecimal getCostoPromedio() { return costoPromedio; }
    public int getVersion() { return version; }
}
