package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.time.Instant;

/** Mapeo del esquema V11. La escritura y las transiciones se incorporan con sus casos de uso. */
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
    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Long getPedidoId() { return pedidoId; }
    public Long getAlmacenId() { return almacenId; }
    public Long getRegistradoPorUsuarioId() { return registradoPorUsuarioId; }
    public Instant getFecha() { return fecha; }
    public EstadoDespacho getEstado() { return estado; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
}
