package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Documento de despacho; la confirmación se incorpora en su caso de uso. */
@Entity @Table(name = "despachos")
public class Despacho {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "folio", nullable = false, length = 30) private String folio;
    @Column(name = "pedido_id", nullable = false) private Long pedidoId;
    @Column(name = "almacen_id", nullable = false) private Long almacenId;
    @Column(name = "registrado_por_usuario_id", nullable = false) private Long registradoPorUsuarioId;
    @Column(name = "fecha", nullable = false) private Instant fecha;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15) private EstadoDespacho estado;
    @Column(name = "clave_idempotencia", nullable = false, length = 100) private String claveIdempotencia;
    protected Despacho() {}
    public Despacho(String folio, Long pedidoId, Long almacenId, Long actor, Instant fecha, String clave) {
        this.folio = java.util.Objects.requireNonNull(folio); this.pedidoId = java.util.Objects.requireNonNull(pedidoId);
        this.almacenId = java.util.Objects.requireNonNull(almacenId); this.registradoPorUsuarioId = java.util.Objects.requireNonNull(actor);
        this.fecha = java.util.Objects.requireNonNull(fecha); this.claveIdempotencia = java.util.Objects.requireNonNull(clave);
        this.estado = EstadoDespacho.BORRADOR;
    }
    public void confirmar() {
        if (estado != EstadoDespacho.BORRADOR) throw new IllegalStateException("Despacho no confirmable");
        estado = EstadoDespacho.CONFIRMADO;
    }
    public void cancelar() {
        if (estado != EstadoDespacho.BORRADOR) throw new IllegalStateException("Despacho no cancelable");
        estado = EstadoDespacho.CANCELADO;
    }
    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Long getPedidoId() { return pedidoId; }
    public Long getAlmacenId() { return almacenId; }
    public Long getRegistradoPorUsuarioId() { return registradoPorUsuarioId; }
    public Instant getFecha() { return fecha; }
    public EstadoDespacho getEstado() { return estado; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
}
