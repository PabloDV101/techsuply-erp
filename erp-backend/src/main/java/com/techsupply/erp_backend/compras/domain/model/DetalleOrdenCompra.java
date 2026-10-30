package com.techsupply.erp_backend.compras.domain.model;

import com.techsupply.erp_backend.maestros.domain.model.Producto;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "detalles_orden_compra")
public class DetalleOrdenCompra {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "orden_compra_id", nullable = false)
    private OrdenCompra orden;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "producto_id", nullable = false)
    private Producto producto;
    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal cantidad;
    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 2) private BigDecimal precioUnitario;
    @Column(name = "descuento_pct", nullable = false, precision = 5, scale = 2) private BigDecimal descuentoPct;
    @Column(name = "tasa_impuesto", nullable = false, precision = 5, scale = 2) private BigDecimal tasaImpuesto;

    protected DetalleOrdenCompra() {}
    public DetalleOrdenCompra(Producto producto, BigDecimal cantidad, BigDecimal precioUnitario, BigDecimal descuentoPct, BigDecimal tasaImpuesto) {
        this.producto = Objects.requireNonNull(producto);
        if (producto.getId() == null) throw new IllegalArgumentException("Producto debe estar persistido");
        CalculoOrden.Linea values = new CalculoOrden.Linea(cantidad, precioUnitario, descuentoPct, tasaImpuesto);
        this.cantidad = values.cantidad(); this.precioUnitario = values.precioUnitario();
        this.descuentoPct = values.descuentoPct(); this.tasaImpuesto = values.tasaImpuesto();
    }
    void asignarOrden(OrdenCompra orden) { this.orden = orden; }
    CalculoOrden.Linea linea() { return new CalculoOrden.Linea(cantidad, precioUnitario, descuentoPct, tasaImpuesto); }
    public Long getId() { return id; }
    public OrdenCompra getOrden() { return orden; }
    public Producto getProducto() { return producto; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getDescuentoPct() { return descuentoPct; }
    public BigDecimal getTasaImpuesto() { return tasaImpuesto; }
}
