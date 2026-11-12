package com.techsupply.erp_backend.inventario.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.Objects;

@Entity @Table(name = "detalles_ajuste_inventario")
public class DetalleAjusteInventario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "ajuste_inventario_id", nullable = false) private AjusteInventario ajuste;
    @Column(name = "producto_id", nullable = false) private Long productoId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 12) private TipoAjuste tipo;
    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal cantidad;
    @Column(name = "costo_unitario", precision = 16, scale = 4) private BigDecimal costoUnitario;
    @Column(name = "motivo_linea", length = 250) private String motivoLinea;
    @Column(name = "detalle_conteo_id") private Long detalleConteoId;

    protected DetalleAjusteInventario() {}
    DetalleAjusteInventario(AjusteInventario ajuste, Long productoId, TipoAjuste tipo, BigDecimal cantidad,
                            BigDecimal costo, Long detalleConteoId, String motivoLinea) {
        this.ajuste = ajuste; this.productoId = ConteoInventario.identificador(productoId);
        this.tipo = Objects.requireNonNull(tipo); this.cantidad = CalculoConteo.cantidadPositiva(cantidad);
        if (tipo == TipoAjuste.POSITIVO && costo == null || tipo == TipoAjuste.NEGATIVO && costo != null)
            throw new IllegalArgumentException("Costo incompatible con el tipo de ajuste");
        this.costoUnitario = costo == null ? null : CalculoConteo.costo(costo);
        this.detalleConteoId = detalleConteoId == null ? null : ConteoInventario.identificador(detalleConteoId);
        this.motivoLinea = motivoLinea == null ? null : ConteoInventario.texto(motivoLinea, 250);
    }
    void fijarCostoSalida(BigDecimal costo) {
        if (tipo != TipoAjuste.NEGATIVO) throw new IllegalStateException("Solo una salida captura costo vigente");
        costoUnitario = CalculoConteo.costo(costo);
    }
    public Long getId() { return id; }
    public Long getProductoId() { return productoId; }
    public TipoAjuste getTipo() { return tipo; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
    public String getMotivoLinea() { return motivoLinea; }
    public Long getDetalleConteoId() { return detalleConteoId; }
}
