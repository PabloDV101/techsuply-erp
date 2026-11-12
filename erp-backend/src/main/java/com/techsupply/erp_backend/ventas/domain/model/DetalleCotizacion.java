package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Mapeo del esquema V11. La escritura y las transiciones se incorporan con sus casos de uso. */
@Entity @Table(name = "detalles_cotizacion")
public class DetalleCotizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "cotizacion_id", nullable = false) private Long cotizacionId;
    @Column(name = "producto_id", nullable = false) private Long productoId;
    @Column(name = "cantidad", nullable = false, precision = 14, scale = 3) private BigDecimal cantidad;
    @Column(name = "precio_unitario", nullable = false, precision = 14, scale = 2) private BigDecimal precioUnitario;
    @Column(name = "descuento_pct", nullable = false, precision = 5, scale = 2) private BigDecimal descuentoPct;
    @Column(name = "tasa_impuesto", nullable = false, precision = 5, scale = 2) private BigDecimal tasaImpuesto;
    protected DetalleCotizacion() {}
    public DetalleCotizacion(Long cotizacionId, Long productoId, ValoresVenta.Linea linea) {
        this.cotizacionId = java.util.Objects.requireNonNull(cotizacionId);
        this.productoId = java.util.Objects.requireNonNull(productoId);
        this.cantidad = linea.cantidad(); this.precioUnitario = linea.precioUnitario();
        this.descuentoPct = linea.descuentoPct(); this.tasaImpuesto = linea.tasaImpuesto();
    }
    public Long getId() { return id; }
    public Long getCotizacionId() { return cotizacionId; }
    public Long getProductoId() { return productoId; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getPrecioUnitario() { return precioUnitario; }
    public BigDecimal getDescuentoPct() { return descuentoPct; }
    public BigDecimal getTasaImpuesto() { return tasaImpuesto; }
    public ValoresVenta.Linea lineaComercial() { return new ValoresVenta.Linea(cantidad, precioUnitario, descuentoPct, tasaImpuesto); }
}
