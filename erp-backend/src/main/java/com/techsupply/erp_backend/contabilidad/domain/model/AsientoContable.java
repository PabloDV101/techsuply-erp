package com.techsupply.erp_backend.contabilidad.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/** Encabezado contable y transiciones protegidas por el servicio y V15. */
@Entity
@Table(name = "asientos_contables")
public class AsientoContable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 30) private String folio;
    @Column(name = "periodo_id", nullable = false) private Long periodoId;
    @Column(name = "fecha_contable", nullable = false) private LocalDate fechaContable;
    @Column(nullable = false, length = 300) private String descripcion;
    @Column(name = "origen_modulo", nullable = false, length = 30) private String origenModulo;
    @Column(name = "origen_tipo", nullable = false, length = 40) private String origenTipo;
    @Column(name = "origen_id") private Long origenId;
    @Column(name = "clave_idempotencia", nullable = false, unique = true, length = 120) private String claveIdempotencia;
    @Column(name = "contraasiento_de_id", unique = true) private Long contraasientoDeId;
    @Column(name = "creado_por_usuario_id", nullable = false) private Long creadoPorUsuarioId;
    @Column(name = "contabilizado_por_usuario_id") private Long contabilizadoPorUsuarioId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private EstadoAsientoContable estado;
    @Column(name = "contabilizado_en") private Instant contabilizadoEn;
    @Column(name = "motivo_cancelacion") private String motivoCancelacion;
    @Column(name = "cancelado_por_usuario_id") private Long canceladoPorUsuarioId;
    @Column(name = "cancelado_en") private Instant canceladoEn;
    @Column(name = "motivo_reversion") private String motivoReversion;
    @Column(name = "ultimo_motivo_edicion") private String ultimoMotivoEdicion;
    @Column(name = "editado_por_usuario_id") private Long editadoPorUsuarioId;
    @Column(name = "editado_en") private Instant editadoEn;

    protected AsientoContable() {}

    public AsientoContable(String folio, Long periodoId, LocalDate fechaContable, String descripcion,
                           String claveIdempotencia, Long creadoPorUsuarioId) {
        this(folio, periodoId, fechaContable, descripcion, claveIdempotencia, creadoPorUsuarioId,
            "MANUAL", "MANUAL", null);
    }
    public AsientoContable(String folio, Long periodoId, LocalDate fechaContable, String descripcion,
                           String claveIdempotencia, Long creadoPorUsuarioId,
                           String origenModulo, String origenTipo, Long origenId) {
        this.folio = ValidacionesContables.texto(folio, 30);
        this.periodoId = ValidacionesContables.id(periodoId);
        this.fechaContable = Objects.requireNonNull(fechaContable, "Fecha requerida");
        this.descripcion = ValidacionesContables.texto(descripcion, 300);
        this.claveIdempotencia = ValidacionesContables.texto(claveIdempotencia, 120);
        this.creadoPorUsuarioId = ValidacionesContables.id(creadoPorUsuarioId);
        this.origenModulo = ValidacionesContables.texto(origenModulo, 30);
        this.origenTipo = ValidacionesContables.texto(origenTipo, 40);
        this.origenId = origenId == null ? null : ValidacionesContables.id(origenId);
        this.estado = EstadoAsientoContable.BORRADOR;
    }
    public static AsientoContable contraasiento(String folio, Long periodoId, LocalDate fechaContable,
            String descripcion, String claveIdempotencia, Long actor, Long originalId, String motivo, Instant instante) {
        var asiento = new AsientoContable(folio, periodoId, fechaContable, descripcion, claveIdempotencia, actor);
        asiento.contraasientoDeId = ValidacionesContables.id(originalId);
        asiento.motivoReversion = ValidacionesContables.texto(motivo, 300);
        asiento.contabilizar(actor, instante);
        return asiento;
    }
    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Long getPeriodoId() { return periodoId; }
    public LocalDate getFechaContable() { return fechaContable; }
    public String getDescripcion() { return descripcion; }
    public String getOrigenModulo() { return origenModulo; }
    public String getOrigenTipo() { return origenTipo; }
    public Long getOrigenId() { return origenId; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public Long getContraasientoDeId() { return contraasientoDeId; }
    public Long getCreadoPorUsuarioId() { return creadoPorUsuarioId; }
    public Long getContabilizadoPorUsuarioId() { return contabilizadoPorUsuarioId; }
    public EstadoAsientoContable getEstado() { return estado; }
    public Instant getContabilizadoEn() { return contabilizadoEn; }
    public String getMotivoReversion() { return motivoReversion; }
    public void registrarEdicion(String motivo, Long actor, Instant instante) {
        this.ultimoMotivoEdicion = ValidacionesContables.texto(motivo, 300);
        this.editadoPorUsuarioId = ValidacionesContables.id(actor);
        this.editadoEn = Objects.requireNonNull(instante);
    }
    public void editar(LocalDate fecha, String descripcion) {
        this.fechaContable = Objects.requireNonNull(fecha);
        this.descripcion = ValidacionesContables.texto(descripcion, 300);
    }
    public void contabilizar(Long actor, Instant instante) {
        this.estado = EstadoAsientoContable.CONTABILIZADO;
        this.contabilizadoPorUsuarioId = ValidacionesContables.id(actor);
        this.contabilizadoEn = Objects.requireNonNull(instante);
    }
    public void cancelar(Long actor, Instant instante, String motivo) {
        this.estado = EstadoAsientoContable.CANCELADO;
        this.canceladoPorUsuarioId = ValidacionesContables.id(actor);
        this.canceladoEn = Objects.requireNonNull(instante);
        this.motivoCancelacion = ValidacionesContables.texto(motivo, 300);
    }
    public void revertir(String motivo) {
        this.estado = EstadoAsientoContable.REVERSADO;
        this.motivoReversion = ValidacionesContables.texto(motivo, 300);
    }
}
