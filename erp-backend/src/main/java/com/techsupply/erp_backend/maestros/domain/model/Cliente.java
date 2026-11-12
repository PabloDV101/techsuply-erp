package com.techsupply.erp_backend.maestros.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

@Entity @Table(name = "clientes")
public class Cliente {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, length = 20, unique = true) private String codigo;
    @Column(name = "nombre_razon_social", nullable = false, length = 160) private String nombreRazonSocial;
    @Column(name = "identificador_fiscal", length = 20, unique = true) private String identificadorFiscal;
    @Column(length = 150) private String correo;
    @Column(length = 30) private String telefono;
    @Column(columnDefinition = "text") private String direccion;
    @Column(name = "limite_credito", nullable = false, precision = 14, scale = 2) private BigDecimal limiteCredito;
    @Column(name = "plazo_credito_dias", nullable = false) private short plazoCreditoDias;
    @Column(nullable = false) private boolean activo;

    protected Cliente() {}
    /** Initializes a customer without granting or implying credit. */
    public Cliente(String codigo, String nombreRazonSocial) {
        String normalized = Objects.requireNonNull(codigo, "código").trim().toUpperCase(Locale.ROOT);
        String name = Objects.requireNonNull(nombreRazonSocial, "nombre").trim();
        if (normalized.isEmpty() || normalized.length() > 20 || name.isEmpty() || name.length() > 160)
            throw new IllegalArgumentException("Código o nombre de cliente inválido");
        this.codigo = normalized; this.nombreRazonSocial = name;
        this.limiteCredito = BigDecimal.ZERO.setScale(2); this.plazoCreditoDias = 30; this.activo = true;
    }
    public void actualizar(String codigo, String nombre, String fiscal, String correo, String telefono,
            String direccion, BigDecimal limite, short plazo) {
        String normalized = Objects.requireNonNull(codigo, "código").trim().toUpperCase(Locale.ROOT);
        String normalizedName = Objects.requireNonNull(nombre, "nombre").trim();
        Objects.requireNonNull(limite, "límite de crédito");
        if (normalized.isEmpty() || normalized.length() > 20 || normalizedName.isEmpty() || normalizedName.length() > 160
                || limite.signum() < 0 || limite.compareTo(new BigDecimal("999999999999.99")) > 0 || plazo < 0)
            throw new IllegalArgumentException("Datos de cliente inválidos");
        BigDecimal exacto;
        try { exacto = limite.setScale(2, java.math.RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw new IllegalArgumentException("Precisión de límite inválida", ex); }
        this.codigo = normalized; this.nombreRazonSocial = normalizedName; this.identificadorFiscal = fiscal;
        this.correo = correo; this.telefono = telefono; this.direccion = direccion;
        this.limiteCredito = exacto; this.plazoCreditoDias = plazo;
    }
    public void cambiarEstado(boolean activo) { this.activo = activo; }
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombreRazonSocial() { return nombreRazonSocial; }
    public String getIdentificadorFiscal() { return identificadorFiscal; }
    public String getCorreo() { return correo; }
    public String getTelefono() { return telefono; }
    public String getDireccion() { return direccion; }
    public BigDecimal getLimiteCredito() { return limiteCredito; }
    public short getPlazoCreditoDias() { return plazoCreditoDias; }
    public boolean isActivo() { return activo; }
}
