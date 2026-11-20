package com.techsupply.erp_backend.contabilidad.domain.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

@Entity
@Table(name = "periodos_contables")
public class PeriodoContable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "fecha_inicio", nullable = false) private LocalDate fechaInicio;
    @Column(name = "fecha_fin", nullable = false) private LocalDate fechaFin;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 10) private EstadoPeriodoContable estado;
    @Column(name = "cerrado_por_usuario_id") private Long cerradoPorUsuarioId;
    @Column(name = "cerrado_en") private Instant cerradoEn;

    protected PeriodoContable() {}

    public PeriodoContable(LocalDate fechaInicio, LocalDate fechaFin) {
        this.fechaInicio = Objects.requireNonNull(fechaInicio, "Inicio requerido");
        this.fechaFin = Objects.requireNonNull(fechaFin, "Fin requerido");
        if (fechaInicio.isAfter(fechaFin)) throw new IllegalArgumentException("Fechas inválidas");
        estado = EstadoPeriodoContable.ABIERTO;
    }
    public boolean contiene(LocalDate fecha) {
        Objects.requireNonNull(fecha, "Fecha requerida");
        return !fecha.isBefore(fechaInicio) && !fecha.isAfter(fechaFin);
    }
    /** La coordinación y el control de borradores corresponden al servicio transaccional. */
    public void cerrar(Long usuarioId, Instant instante) {
        if (estado != EstadoPeriodoContable.ABIERTO) throw new IllegalStateException("Periodo no está abierto");
        long actor = ValidacionesContables.id(usuarioId);
        Instant cierre = Objects.requireNonNull(instante, "Fecha de cierre requerida");
        this.cerradoPorUsuarioId = actor;
        this.cerradoEn = cierre;
        this.estado = EstadoPeriodoContable.CERRADO;
    }
    public Long getId() { return id; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public LocalDate getFechaFin() { return fechaFin; }
    public EstadoPeriodoContable getEstado() { return estado; }
    public Long getCerradoPorUsuarioId() { return cerradoPorUsuarioId; }
    public Instant getCerradoEn() { return cerradoEn; }
}
