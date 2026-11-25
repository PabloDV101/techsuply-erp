package com.techsupply.erp_backend.contabilidad.domain.model;

import com.techsupply.erp_backend.security.domain.model.Usuario;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity @Table(name="periodos_contables")
public class PeriodoContable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false) private LocalDate fechaInicio;
    @Column(nullable=false) private LocalDate fechaFin;
    @Column(nullable=false,length=10) private String estado="ABIERTO";
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cerrado_por_usuario_id") private Usuario cerradoPor;
    private Instant cerradoEn;
    public Long getId(){return id;} public LocalDate getFechaInicio(){return fechaInicio;} public void setFechaInicio(LocalDate v){fechaInicio=v;}
    public LocalDate getFechaFin(){return fechaFin;} public void setFechaFin(LocalDate v){fechaFin=v;}
    public String getEstado(){return estado;} public void setEstado(String v){estado=v;}
    public Usuario getCerradoPor(){return cerradoPor;} public void setCerradoPor(Usuario v){cerradoPor=v;}
    public Instant getCerradoEn(){return cerradoEn;} public void setCerradoEn(Instant v){cerradoEn=v;}
}
