package com.techsupply.erp_backend.inventario.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity @Table(name = "ajustes_inventario")
public class AjusteInventario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 30, unique = true) private String folio;
    @Column(name = "almacen_id", nullable = false) private Long almacenId;
    @Column(nullable = false, columnDefinition = "text") private String motivo;
    @Column(name = "solicitado_por_usuario_id", nullable = false) private Long solicitadoPorUsuarioId;
    @Column(name = "autorizado_por_usuario_id") private Long autorizadoPorUsuarioId;
    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 120) private String claveIdempotencia;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private EstadoAjuste estado;
    @Column(nullable = false) private Instant fecha;
    @Column(name = "autorizado_en") private Instant autorizadoEn;
    @Column(name = "motivo_rechazo", columnDefinition = "text") private String motivoRechazo;
    @Column(name = "aplicado_en") private Instant aplicadoEn;
    @OneToMany(mappedBy = "ajuste", cascade = {CascadeType.PERSIST, CascadeType.MERGE})
    private List<DetalleAjusteInventario> detalles = new ArrayList<>();

    protected AjusteInventario() {}
    public AjusteInventario(String folio, Long almacenId, String motivo, Long solicitanteId, String clave, Instant fecha) {
        this.folio = ConteoInventario.texto(folio, 30); this.almacenId = ConteoInventario.identificador(almacenId);
        this.motivo = ConteoInventario.texto(motivo, 1000); this.solicitadoPorUsuarioId = ConteoInventario.identificador(solicitanteId);
        this.claveIdempotencia = ConteoInventario.texto(clave, 120); this.fecha = Objects.requireNonNull(fecha);
        estado = EstadoAjuste.PENDIENTE;
    }
    public void agregarDetalle(Long productoId, TipoAjuste tipo, BigDecimal cantidad, BigDecimal costo, Long detalleConteoId, String motivoLinea) {
        exigirPendiente();
        ConteoInventario.identificador(productoId);
        if (detalles.stream().anyMatch(d -> d.getProductoId().equals(productoId))) throw new IllegalArgumentException("Producto duplicado");
        detalles.add(new DetalleAjusteInventario(this, productoId, tipo, cantidad, costo, detalleConteoId, motivoLinea));
    }
    public void aprobar(Long actorId, Instant instante) {
        validarAutorizacion(actorId, instante);
        if (detalles.isEmpty()) throw new IllegalStateException("Ajuste sin detalles");
        autorizadoPorUsuarioId = actorId; autorizadoEn = instante; estado = EstadoAjuste.APROBADO;
    }
    public void rechazar(Long actorId, Instant instante, String motivo) {
        validarAutorizacion(actorId, instante);
        String motivoValido = ConteoInventario.texto(motivo, 1000);
        autorizadoPorUsuarioId = actorId; autorizadoEn = instante; motivoRechazo = motivoValido; estado = EstadoAjuste.RECHAZADO;
    }
    public void fijarCostoSalida(Long detalleId, BigDecimal costo) {
        if (estado != EstadoAjuste.APROBADO) throw new IllegalStateException("Ajuste no aprobado");
        detalles.stream().filter(d -> Objects.equals(d.getId(), detalleId)).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("Detalle no encontrado")).fijarCostoSalida(costo);
    }
    public void marcarAplicado(Instant instante) {
        if (estado != EstadoAjuste.APROBADO || detalles.isEmpty()) throw new IllegalStateException("Ajuste no aplicable");
        if (detalles.stream().anyMatch(d -> d.getCostoUnitario() == null)) throw new IllegalStateException("Costo de salida pendiente");
        aplicadoEn = Objects.requireNonNull(instante); estado = EstadoAjuste.APLICADO;
    }
    private void validarAutorizacion(Long actorId, Instant instante) {
        exigirPendiente(); ConteoInventario.identificador(actorId); Objects.requireNonNull(instante);
        if (solicitadoPorUsuarioId.equals(actorId)) throw new IllegalArgumentException("El autorizador debe ser distinto del solicitante");
    }
    private void exigirPendiente() { if (estado != EstadoAjuste.PENDIENTE) throw new IllegalStateException("Ajuste no pendiente"); }
    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Long getAlmacenId() { return almacenId; }
    public String getMotivo() { return motivo; }
    public Long getSolicitadoPorUsuarioId() { return solicitadoPorUsuarioId; }
    public Long getAutorizadoPorUsuarioId() { return autorizadoPorUsuarioId; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public EstadoAjuste getEstado() { return estado; }
    public Instant getFecha() { return fecha; }
    public Instant getAutorizadoEn() { return autorizadoEn; }
    public String getMotivoRechazo() { return motivoRechazo; }
    public Instant getAplicadoEn() { return aplicadoEn; }
    public List<DetalleAjusteInventario> getDetalles() { return List.copyOf(detalles); }
}
