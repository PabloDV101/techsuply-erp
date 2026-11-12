package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Documento interno; el borrador solo existe dentro de la transacción de emisión. */
@Entity @Table(name="facturas_venta")
public class FacturaVenta {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=30) private String folio;
    @Column(name="pedido_id",nullable=false,unique=true) private Long pedidoId;
    @Column(name="cliente_id",nullable=false) private Long clienteId;
    @Column(name="emitida_por_usuario_id",nullable=false) private Long emitidaPorUsuarioId;
    @Column(name="fecha_emision",nullable=false) private LocalDate fechaEmision;
    @Column(name="fecha_vencimiento",nullable=false) private LocalDate fechaVencimiento;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal subtotal;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal impuestos;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal total;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private EstadoFacturaVenta estado;
    @Column(name="clave_idempotencia",nullable=false,unique=true,length=100) private String claveIdempotencia;
    protected FacturaVenta() {}
    public FacturaVenta(String folio,PedidoVenta pedido,Long actor,LocalDate fecha,String clave) {
        this.folio=java.util.Objects.requireNonNull(folio);this.pedidoId=pedido.getId();this.clienteId=pedido.getClienteId();
        this.emitidaPorUsuarioId=java.util.Objects.requireNonNull(actor);this.fechaEmision=java.util.Objects.requireNonNull(fecha);
        this.fechaVencimiento=fecha;this.subtotal=pedido.getSubtotal();this.impuestos=pedido.getImpuestos();this.total=pedido.getTotal();
        this.claveIdempotencia=java.util.Objects.requireNonNull(clave);this.estado=EstadoFacturaVenta.BORRADOR;
    }
    public void emitir() {if(estado!=EstadoFacturaVenta.BORRADOR) throw new IllegalStateException("Factura no emitible");estado=EstadoFacturaVenta.EMITIDA;}
    public Long getId(){return id;} public String getFolio(){return folio;} public Long getPedidoId(){return pedidoId;}
    public Long getClienteId(){return clienteId;} public Long getEmitidaPorUsuarioId(){return emitidaPorUsuarioId;}
    public LocalDate getFechaEmision(){return fechaEmision;} public LocalDate getFechaVencimiento(){return fechaVencimiento;}
    public BigDecimal getSubtotal(){return subtotal;} public BigDecimal getImpuestos(){return impuestos;} public BigDecimal getTotal(){return total;}
    public EstadoFacturaVenta getEstado(){return estado;} public String getClaveIdempotencia(){return claveIdempotencia;}
}
