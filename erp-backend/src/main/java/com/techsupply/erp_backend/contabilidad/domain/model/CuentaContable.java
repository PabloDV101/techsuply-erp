package com.techsupply.erp_backend.contabilidad.domain.model;

import jakarta.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "cuentas_contables")
public class CuentaContable {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 30) private String codigo;
    @Column(nullable = false, length = 150) private String nombre;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 15) private TipoCuenta tipo;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private NaturalezaCuenta naturaleza;
    @Column(nullable = false) private short nivel;
    @Column(name = "cuenta_padre_id") private Long cuentaPadreId;
    @Column(name = "acepta_movimientos", nullable = false) private boolean aceptaMovimientos;
    @Column(nullable = false) private boolean activa;

    protected CuentaContable() {}

    public CuentaContable(String codigo, String nombre, TipoCuenta tipo, NaturalezaCuenta naturaleza,
                           short nivel, Long cuentaPadreId, boolean aceptaMovimientos) {
        this.codigo = ValidacionesContables.texto(codigo, 30);
        this.nombre = ValidacionesContables.texto(nombre, 150);
        this.tipo = Objects.requireNonNull(tipo, "Tipo requerido");
        this.naturaleza = Objects.requireNonNull(naturaleza, "Naturaleza requerida");
        if (nivel < 1) throw new IllegalArgumentException("Nivel inválido");
        if (cuentaPadreId != null) ValidacionesContables.id(cuentaPadreId);
        this.nivel = nivel;
        this.cuentaPadreId = cuentaPadreId;
        this.aceptaMovimientos = aceptaMovimientos;
        this.activa = true;
    }
    public Long getId() { return id; }
    public String getCodigo() { return codigo; }
    public String getNombre() { return nombre; }
    public TipoCuenta getTipo() { return tipo; }
    public NaturalezaCuenta getNaturaleza() { return naturaleza; }
    public short getNivel() { return nivel; }
    public Long getCuentaPadreId() { return cuentaPadreId; }
    public boolean isAceptaMovimientos() { return aceptaMovimientos; }
    public boolean isActiva() { return activa; }

    /** El servicio debe comprobar jerarquía, historia y concurrencia antes de llamar. */
    public void actualizar(String codigo, String nombre, TipoCuenta tipo, NaturalezaCuenta naturaleza,
                           short nivel, Long cuentaPadreId, boolean aceptaMovimientos) {
        CuentaContable datos = new CuentaContable(codigo, nombre, tipo, naturaleza, nivel, cuentaPadreId, aceptaMovimientos);
        if (id != null && id.equals(datos.cuentaPadreId)) throw new IllegalArgumentException("Cuenta padre inválida");
        this.codigo = datos.codigo;
        this.nombre = datos.nombre;
        this.tipo = datos.tipo;
        this.naturaleza = datos.naturaleza;
        this.nivel = datos.nivel;
        this.cuentaPadreId = datos.cuentaPadreId;
        this.aceptaMovimientos = datos.aceptaMovimientos;
    }

    public void cambiarEstado(boolean activa) { this.activa = activa; }
}
