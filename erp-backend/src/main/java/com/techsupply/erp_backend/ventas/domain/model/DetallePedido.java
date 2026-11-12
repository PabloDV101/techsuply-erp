package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Instantánea comercial de una línea de cotización. */
@Entity @Table(name = "detalles_pedido")
public class DetallePedido {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "pedido_id", nullable = false) private Long pedidoId;
    @Column(name = "producto_id", nullable = false) private Long productoId;
    @Column(name = "cantidad", nullable = false, precision = 14, scale = 3) private BigDecimal cantidad;
    @Column(name = "cantidad_reservada", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadReservada;
    @Column(name = "cantidad_despachada", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadDespachada;
    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 2) private BigDecimal precioUnitario;
    @Column(name = "descuento_pct", nullable = false, precision = 5, scale = 2) private BigDecimal descuentoPct;
    @Column(name = "tasa_impuesto", nullable = false, precision = 5, scale = 2) private BigDecimal tasaImpuesto;
    protected DetallePedido() {}
    public DetallePedido(Long pedidoId, DetalleCotizacion linea) {
        this.pedidoId = java.util.Objects.requireNonNull(pedidoId); this.productoId = linea.getProductoId();
        this.cantidad = linea.getCantidad(); this.cantidadReservada = BigDecimal.ZERO; this.cantidadDespachada = BigDecimal.ZERO;
        this.precioUnitario = linea.getPrecioUnitario(); this.descuentoPct = linea.getDescuentoPct(); this.tasaImpuesto = linea.getTasaImpuesto();
    }
    public Long getId() { return id; }
    public Long getPedidoId() { return pedidoId; }
    public Long getProductoId() { return productoId; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getCantidadReservada() { return cantidadReservada; }
    public BigDecimal getCantidadDespachada() { return cantidadDespachada; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getDescuentoPct() { return descuentoPct; }
    public BigDecimal getTasaImpuesto() { return tasaImpuesto; }
    public BigDecimal pendiente() { return ValoresVenta.pendiente(cantidad, cantidadDespachada); }
    public ValoresVenta.Linea lineaComercial() { return new ValoresVenta.Linea(cantidad, precioUnitario, descuentoPct, tasaImpuesto); }
}
