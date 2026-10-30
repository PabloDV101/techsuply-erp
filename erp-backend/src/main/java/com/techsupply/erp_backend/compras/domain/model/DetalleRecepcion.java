package com.techsupply.erp_backend.compras.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity @Table(name = "detalles_recepcion")
public class DetalleRecepcion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "recepcion_id", nullable = false) private RecepcionCompra recepcion;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "detalle_orden_compra_id", nullable = false) private DetalleOrdenCompra detalleOrden;
    @Column(name = "cantidad_recibida", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadRecibida;
    @Column(name = "cantidad_aceptada", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadAceptada;
    @Column(name = "cantidad_rechazada", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadRechazada;
    @Column(name = "precio_unitario_recibido", nullable = false, precision = 14, scale = 2) private BigDecimal precioUnitarioRecibido;
    @Column(name = "motivo_rechazo", columnDefinition = "text") private String motivoRechazo;
    protected DetalleRecepcion() {}
    public DetalleRecepcion(DetalleOrdenCompra detalle, BigDecimal recibida, BigDecimal aceptada, BigDecimal rechazada, BigDecimal precio, String motivo) {
        this.detalleOrden = Objects.requireNonNull(detalle);
        if (recibida.signum() <= 0 || aceptada.signum() < 0 || rechazada.signum() < 0 || precio.signum() < 0
            || aceptada.add(rechazada).compareTo(recibida) > 0) throw new IllegalArgumentException("Cantidades inválidas");
        this.cantidadRecibida = recibida; this.cantidadAceptada = aceptada; this.cantidadRechazada = rechazada;
        this.precioUnitarioRecibido = precio; this.motivoRechazo = motivo;
    }
    void asignarRecepcion(RecepcionCompra value) { recepcion = value; }
    public Long getId() { return id; } public RecepcionCompra getRecepcion() { return recepcion; }
    public DetalleOrdenCompra getDetalleOrden() { return detalleOrden; }
    public BigDecimal getCantidadRecibida() { return cantidadRecibida; } public BigDecimal getCantidadAceptada() { return cantidadAceptada; }
    public BigDecimal getCantidadRechazada() { return cantidadRechazada; } public BigDecimal getPrecioUnitarioRecibido() { return precioUnitarioRecibido; }
    public String getMotivoRechazo() { return motivoRechazo; }
}
