package com.techsupply.erp_backend.maestros.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name="clientes")
public class Cliente {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @Column(nullable=false,length=20,unique=true) private String codigo;
    @Column(nullable=false,length=30,unique=true) private String ruc;
    @Column(nullable=false,length=160) private String razonSocial;
    @Column(length=100) private String contacto;
    @Column(length=30) private String telefono;
    @Column(length=150) private String email;
    @Column(columnDefinition="text") private String direccion;
    @Column(nullable=false,precision=14,scale=2) private BigDecimal limiteCredito=BigDecimal.ZERO;
    @Column(nullable=false) private short plazoPagoDias=30;
    @Column(nullable=false) private boolean activo=true;
    public Long getId(){return id;} public String getCodigo(){return codigo;} public void setCodigo(String v){codigo=v;}
    public String getRuc(){return ruc;} public void setRuc(String v){ruc=v;}
    public String getRazonSocial(){return razonSocial;} public void setRazonSocial(String v){razonSocial=v;}
    public String getContacto(){return contacto;} public void setContacto(String v){contacto=v;}
    public String getTelefono(){return telefono;} public void setTelefono(String v){telefono=v;}
    public String getEmail(){return email;} public void setEmail(String v){email=v;}
    public String getDireccion(){return direccion;} public void setDireccion(String v){direccion=v;}
    public BigDecimal getLimiteCredito(){return limiteCredito;} public void setLimiteCredito(BigDecimal v){limiteCredito=v;}
    public short getPlazoPagoDias(){return plazoPagoDias;} public void setPlazoPagoDias(short v){plazoPagoDias=v;}
    public boolean isActivo(){return activo;} public void setActivo(boolean v){activo=v;}
}
