package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Pedido originado en una cotización; las transiciones posteriores se incorporan con sus casos de uso. */
@Entity @Table(name = "pedidos_venta")
public class PedidoVenta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "folio", nullable = false, length = 30) private String folio;
    @Column(name = "cliente_id", nullable = false) private Long clienteId;
    @Column(name = "vendedor_usuario_id", nullable = false) private Long vendedorUsuarioId;
    @Column(name = "cotizacion_id", nullable = false) private Long cotizacionId;
    @Column(name = "almacen_id", nullable = false) private Long almacenId;
    @Column(name = "creado_por_usuario_id", nullable = false) private Long creadoPorUsuarioId;
    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 15) private CanalVenta canal;
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", nullable = false, length = 15) private ModalidadVenta modalidad;
    @Column(name = "fecha", nullable = false) private Instant fecha;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 25) private EstadoPedidoVenta estado;
    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2) private BigDecimal subtotal;
    @Column(name = "impuestos", nullable = false, precision = 14, scale = 2) private BigDecimal impuestos;
    @Column(name = "total", nullable = false, precision = 14, scale = 2) private BigDecimal total;
    @Column(name = "clave_idempotencia", nullable = false, length = 100) private String claveIdempotencia;
    @Column(name = "observaciones", columnDefinition = "text") private String observaciones;
    @Column(name = "motivo_cancelacion", columnDefinition = "text") private String motivoCancelacion;
    @Column(name = "cancelado_por_usuario_id") private Long canceladoPorUsuarioId;
    @Column(name = "cancelado_en") private Instant canceladoEn;
    protected PedidoVenta() {}
    public PedidoVenta(String folio, Cotizacion cotizacion, Long almacenId, Long actor, Instant fecha, String clave) {
        this.folio = java.util.Objects.requireNonNull(folio);
        this.clienteId = cotizacion.getClienteId(); this.vendedorUsuarioId = cotizacion.getVendedorUsuarioId();
        this.cotizacionId = cotizacion.getId(); this.almacenId = java.util.Objects.requireNonNull(almacenId);
        this.creadoPorUsuarioId = java.util.Objects.requireNonNull(actor); this.fecha = java.util.Objects.requireNonNull(fecha);
        this.canal = CanalVenta.INTERNO; this.modalidad = ModalidadVenta.CONTADO; this.estado = EstadoPedidoVenta.BORRADOR;
        this.subtotal = cotizacion.getSubtotal(); this.impuestos = cotizacion.getImpuestos(); this.total = cotizacion.getTotal();
        this.claveIdempotencia = java.util.Objects.requireNonNull(clave);
    }
    public void confirmar() {
        if (estado != EstadoPedidoVenta.BORRADOR) throw new IllegalStateException("Pedido no confirmable");
        estado = EstadoPedidoVenta.CONFIRMADO;
    }
    public void registrarDespacho(boolean completo) {
        if (estado != EstadoPedidoVenta.CONFIRMADO && estado != EstadoPedidoVenta.PARCIALMENTE_DESPACHADO)
            throw new IllegalStateException("Pedido no despachable");
        estado = completo ? EstadoPedidoVenta.DESPACHADO : EstadoPedidoVenta.PARCIALMENTE_DESPACHADO;
    }
    public void cancelar(String motivo, Long actor, Instant instante) {
        if (estado != EstadoPedidoVenta.BORRADOR && estado != EstadoPedidoVenta.CONFIRMADO && estado != EstadoPedidoVenta.PARCIALMENTE_DESPACHADO)
            throw new IllegalStateException("Pedido no cancelable");
        if (motivo == null || motivo.isBlank() || motivo.length()>1000 || !motivo.equals(motivo.trim()))
            throw new IllegalArgumentException("Motivo inválido");
        motivoCancelacion=motivo; canceladoPorUsuarioId=java.util.Objects.requireNonNull(actor);
        canceladoEn=java.util.Objects.requireNonNull(instante); estado=EstadoPedidoVenta.CANCELADO;
    }
    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Long getClienteId() { return clienteId; }
    public Long getVendedorUsuarioId() { return vendedorUsuarioId; }
    public Long getCotizacionId() { return cotizacionId; }
    public Long getAlmacenId() { return almacenId; }
    public Long getCreadoPorUsuarioId() { return creadoPorUsuarioId; }
    public CanalVenta getCanal() { return canal; }
    public ModalidadVenta getModalidad() { return modalidad; }
    public Instant getFecha() { return fecha; }
    public EstadoPedidoVenta getEstado() { return estado; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getImpuestos() { return impuestos; }
    public BigDecimal getTotal() { return total; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public String getObservaciones() { return observaciones; }
    public String getMotivoCancelacion() { return motivoCancelacion; }
    public Long getCanceladoPorUsuarioId() { return canceladoPorUsuarioId; }
    public Instant getCanceladoEn() { return canceladoEn; }
}
