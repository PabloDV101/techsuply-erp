package com.techsupply.erp_backend.contabilidad.domain.model;

import jakarta.persistence.*;

@Entity @Table(name="cuentas_contables")
public class CuentaContable {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=30,unique=true) private String codigo;
    @Column(nullable=false,length=150) private String nombre;
    @Column(nullable=false,length=15) private String tipo;
    @Column(nullable=false,length=12) private String naturaleza;
    @Column(nullable=false) private short nivel=1;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="cuenta_padre_id") private CuentaContable cuentaPadre;
    @Column(nullable=false) private boolean aceptaMovimientos=true;
    @Column(nullable=false) private boolean activa=true;
    public Long getId(){return id;} public String getCodigo(){return codigo;} public void setCodigo(String v){codigo=v;}
    public String getNombre(){return nombre;} public void setNombre(String v){nombre=v;}
    public String getTipo(){return tipo;} public void setTipo(String v){tipo=v;}
    public String getNaturaleza(){return naturaleza;} public void setNaturaleza(String v){naturaleza=v;}
    public short getNivel(){return nivel;} public void setNivel(short v){nivel=v;}
    public CuentaContable getCuentaPadre(){return cuentaPadre;} public void setCuentaPadre(CuentaContable v){cuentaPadre=v;}
    public boolean isAceptaMovimientos(){return aceptaMovimientos;} public void setAceptaMovimientos(boolean v){aceptaMovimientos=v;}
    public boolean isActiva(){return activa;} public void setActiva(boolean v){activa=v;}
}
