package com.techsupply.erp_backend.contabilidad.domain.model;

import com.techsupply.erp_backend.security.domain.model.Usuario;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity @Table(name="asientos_contables")
public class AsientoContable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=30,unique=true) private String folio;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="periodo_id",nullable=false) private PeriodoContable periodo;
    @Column(nullable=false) private LocalDate fechaContable;
    @Column(nullable=false,length=300) private String descripcion;
    @Column(nullable=false,length=30) private String origenModulo;
    @Column(nullable=false,length=40) private String origenTipo;
    private Long origenId;
    @Column(length=100) private String claveIdempotencia;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="contraasiento_de_id") private AsientoContable contraasientoDe;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="creado_por_usuario_id",nullable=false) private Usuario creadoPor;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="contabilizado_por_usuario_id") private Usuario contabilizadoPor;
    @Column(nullable=false,length=13) private String estado="BORRADOR";
    private Instant contabilizadoEn;
    @OneToMany(mappedBy="asiento",cascade=CascadeType.ALL,orphanRemoval=true) @OrderBy("numeroLinea ASC") private List<PartidaAsiento> partidas=new ArrayList<>();
    public Long getId(){return id;} public String getFolio(){return folio;} public void setFolio(String v){folio=v;}
    public PeriodoContable getPeriodo(){return periodo;} public void setPeriodo(PeriodoContable v){periodo=v;}
    public LocalDate getFechaContable(){return fechaContable;} public void setFechaContable(LocalDate v){fechaContable=v;}
    public String getDescripcion(){return descripcion;} public void setDescripcion(String v){descripcion=v;}
    public String getOrigenModulo(){return origenModulo;} public void setOrigenModulo(String v){origenModulo=v;}
    public String getOrigenTipo(){return origenTipo;} public void setOrigenTipo(String v){origenTipo=v;}
    public Long getOrigenId(){return origenId;} public void setOrigenId(Long v){origenId=v;}
    public String getClaveIdempotencia(){return claveIdempotencia;} public void setClaveIdempotencia(String v){claveIdempotencia=v;}
    public AsientoContable getContraasientoDe(){return contraasientoDe;} public void setContraasientoDe(AsientoContable v){contraasientoDe=v;}
    public Usuario getCreadoPor(){return creadoPor;} public void setCreadoPor(Usuario v){creadoPor=v;}
    public Usuario getContabilizadoPor(){return contabilizadoPor;} public void setContabilizadoPor(Usuario v){contabilizadoPor=v;}
    public String getEstado(){return estado;} public void setEstado(String v){estado=v;}
    public Instant getContabilizadoEn(){return contabilizadoEn;} public void setContabilizadoEn(Instant v){contabilizadoEn=v;}
    public List<PartidaAsiento> getPartidas(){return partidas;}
}
