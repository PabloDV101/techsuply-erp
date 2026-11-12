package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="detalles_factura_venta")
public class DetalleFacturaVenta {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(name="factura_venta_id",nullable=false) private Long facturaVentaId;
    @Column(name="detalle_pedido_id",nullable=false) private Long detallePedidoId;
    @Column(name="producto_id",nullable=false) private Long productoId;
    @Column(nullable=false,precision=14,scale=3) private BigDecimal cantidad;
    @Column(name="precio_unitario",nullable=false,precision=14,scale=2) private BigDecimal precioUnitario;
    @Column(name="descuento_pct",nullable=false,precision=5,scale=2) private BigDecimal descuentoPct;
    @Column(name="tasa_impuesto",nullable=false,precision=5,scale=2) private BigDecimal tasaImpuesto;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal subtotal;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal impuestos;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal total;
    protected DetalleFacturaVenta() {}
    public DetalleFacturaVenta(Long facturaId,DetallePedido linea,CalculoCotizacion.Totales importes) {
        facturaVentaId=java.util.Objects.requireNonNull(facturaId);detallePedidoId=linea.getId();productoId=linea.getProductoId();
        cantidad=linea.getCantidad();precioUnitario=linea.getPrecioUnitario();descuentoPct=linea.getDescuentoPct();tasaImpuesto=linea.getTasaImpuesto();
        subtotal=importes.subtotal();impuestos=importes.impuestos();total=importes.total();
    }
    public Long getId(){return id;} public Long getFacturaVentaId(){return facturaVentaId;} public Long getDetallePedidoId(){return detallePedidoId;}
    public Long getProductoId(){return productoId;} public BigDecimal getCantidad(){return cantidad;} public BigDecimal getPrecioUnitario(){return precioUnitario;}
    public BigDecimal getDescuentoPct(){return descuentoPct;} public BigDecimal getTasaImpuesto(){return tasaImpuesto;}
    public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getImpuestos(){return impuestos;} public BigDecimal getTotal(){return total;}
}
