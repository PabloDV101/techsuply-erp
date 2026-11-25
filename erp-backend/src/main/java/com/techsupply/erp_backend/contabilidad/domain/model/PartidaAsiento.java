package com.techsupply.erp_backend.contabilidad.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name="partidas_asiento")
public class PartidaAsiento {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="asiento_id",nullable=false) private AsientoContable asiento;
    @Column(nullable=false) private short numeroLinea;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cuenta_contable_id",nullable=false) private CuentaContable cuenta;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal debe=BigDecimal.ZERO;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal haber=BigDecimal.ZERO;
    @Column(length=200) private String descripcion;
    public Long getId(){return id;} public AsientoContable getAsiento(){return asiento;} public void setAsiento(AsientoContable v){asiento=v;}
    public short getNumeroLinea(){return numeroLinea;} public void setNumeroLinea(short v){numeroLinea=v;}
    public CuentaContable getCuenta(){return cuenta;} public void setCuenta(CuentaContable v){cuenta=v;}
    public BigDecimal getDebe(){return debe;} public void setDebe(BigDecimal v){debe=v;} public BigDecimal getHaber(){return haber;} public void setHaber(BigDecimal v){haber=v;}
    public String getDescripcion(){return descripcion;} public void setDescripcion(String v){descripcion=v;}
}
