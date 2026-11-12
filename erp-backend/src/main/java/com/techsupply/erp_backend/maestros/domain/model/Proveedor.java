package com.techsupply.erp_backend.maestros.domain.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "proveedores")
public class Proveedor {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20, unique = true)
    private String codigo;

    @Column(name = "nombre_razon_social", nullable = false, length = 160)
    private String nombreRazonSocial;

    @Column(name = "identificador_fiscal", length = 20, unique = true)
    private String identificadorFiscal;

    @Column(length = 100)
    private String contacto;

    @Column(length = 150)
    private String correo;

    @Column(length = 30)
    private String telefono;

    @Column(columnDefinition = "text")
    private String direccion;

    @Column(name = "plazo_credito_dias", nullable = false)
    private short plazoCreditoDias;

    @Column(nullable = false)
    private boolean activo = true;

    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }
    public String getNombreRazonSocial() { return nombreRazonSocial; }
    public void setNombreRazonSocial(String nombreRazonSocial) { this.nombreRazonSocial = nombreRazonSocial; }
    public String getIdentificadorFiscal() { return identificadorFiscal; }
    public void setIdentificadorFiscal(String identificadorFiscal) { this.identificadorFiscal = identificadorFiscal; }
    public String getContacto() { return contacto; }
    public void setContacto(String contacto) { this.contacto = contacto; }
    public String getCorreo() { return correo; }
    public void setCorreo(String correo) { this.correo = correo; }
    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }
    public String getDireccion() { return direccion; }
    public void setDireccion(String direccion) { this.direccion = direccion; }
    public short getPlazoCreditoDias() { return plazoCreditoDias; }
    public void setPlazoCreditoDias(short plazoCreditoDias) { this.plazoCreditoDias = plazoCreditoDias; }
    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }
}
