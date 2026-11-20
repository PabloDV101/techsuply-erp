package com.techsupply.erp_backend.contabilidad.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "partidas_asiento")
public class PartidaAsiento {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "asiento_id", nullable = false) private Long asientoId;
    @Column(name = "numero_linea", nullable = false) private short numeroLinea;
    @Column(name = "cuenta_contable_id", nullable = false) private Long cuentaContableId;
    @Column(nullable = false, columnDefinition = "numeric") private BigDecimal debe;
    @Column(nullable = false, columnDefinition = "numeric") private BigDecimal haber;
    @Column(length = 200) private String descripcion;

    protected PartidaAsiento() {}

    public PartidaAsiento(Long asientoId, short numeroLinea, Long cuentaContableId,
                          BigDecimal debe, BigDecimal haber, String descripcion) {
        this.asientoId = ValidacionesContables.id(asientoId);
        if (numeroLinea < 1) throw new IllegalArgumentException("Número de línea inválido");
        this.numeroLinea = numeroLinea;
        this.cuentaContableId = ValidacionesContables.id(cuentaContableId);
        var importe = new ImporteContable.Partida(debe, haber);
        this.debe = importe.debe();
        this.haber = importe.haber();
        if (descripcion != null && descripcion.length() > 200) throw new IllegalArgumentException("Descripción demasiado larga");
        this.descripcion = descripcion;
    }
    public Long getId() { return id; }
    public Long getAsientoId() { return asientoId; }
    public short getNumeroLinea() { return numeroLinea; }
    public Long getCuentaContableId() { return cuentaContableId; }
    public BigDecimal getDebe() { return debe; }
    public BigDecimal getHaber() { return haber; }
    public String getDescripcion() { return descripcion; }
    public ImporteContable.Partida importe() { return new ImporteContable.Partida(debe, haber); }
}
