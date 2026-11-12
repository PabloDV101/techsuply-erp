package com.techsupply.erp_backend.compras.domain.model;

import com.techsupply.erp_backend.maestros.domain.model.Almacen;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity @Table(name = "recepciones_compra")
public class RecepcionCompra {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30, unique = true) private String folio;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "orden_compra_id", nullable = false) private OrdenCompra orden;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "almacen_id", nullable = false) private Almacen almacen;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "registrada_por_usuario_id", nullable = false) private Usuario registradaPor;
    @Column(name = "clave_idempotencia", nullable = false, length = 120, unique = true) private String claveIdempotencia;
    @Column(nullable = false) private Instant fecha;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private EstadoRecepcionCompra estado;
    @Column(name = "referencia_externa", length = 80) private String referenciaExterna;
    @Column(name = "confirmada_en") private Instant confirmadaEn;
    @OneToMany(mappedBy = "recepcion", cascade = CascadeType.PERSIST) private List<DetalleRecepcion> detalles = new ArrayList<>();
    protected RecepcionCompra() {}
    public RecepcionCompra(String folio, OrdenCompra orden, Almacen almacen, Usuario actor, String clave, Instant fecha, String referencia) {
        this.folio = Objects.requireNonNull(folio); this.orden = Objects.requireNonNull(orden); this.almacen = Objects.requireNonNull(almacen);
        this.registradaPor = Objects.requireNonNull(actor); this.claveIdempotencia = Objects.requireNonNull(clave);
        this.fecha = Objects.requireNonNull(fecha); this.referenciaExterna = referencia; this.estado = EstadoRecepcionCompra.PENDIENTE;
    }
    public void agregarDetalle(DetalleRecepcion detalle) {
        Objects.requireNonNull(detalle);
        if (detalle.getRecepcion() != null || !Objects.equals(detalle.getDetalleOrden().getOrden().getId(), orden.getId())
            || detalles.stream().anyMatch(d -> Objects.equals(d.getDetalleOrden().getId(), detalle.getDetalleOrden().getId())))
            throw new IllegalArgumentException("Detalle de orden inválido o duplicado");
        detalle.asignarRecepcion(this); detalles.add(detalle);
    }
    public void confirmar(Instant instante) {
        if (estado != EstadoRecepcionCompra.PENDIENTE || detalles.isEmpty()) throw new IllegalStateException("Recepción no confirmable");
        this.confirmadaEn = Objects.requireNonNull(instante);
        this.estado = EstadoRecepcionCompra.CONFIRMADA;
    }
    public Long getId() { return id; } public String getFolio() { return folio; }
    public OrdenCompra getOrden() { return orden; } public Almacen getAlmacen() { return almacen; }
    public Usuario getRegistradaPor() { return registradaPor; } public String getClaveIdempotencia() { return claveIdempotencia; }
    public Instant getFecha() { return fecha; } public EstadoRecepcionCompra getEstado() { return estado; }
    public String getReferenciaExterna() { return referenciaExterna; } public Instant getConfirmadaEn() { return confirmadaEn; }
    public List<DetalleRecepcion> getDetalles() { return List.copyOf(detalles); }
}
