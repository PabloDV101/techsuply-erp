package com.techsupply.erp_backend.inventario.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity @Table(name = "detalles_conteo")
public class DetalleConteo {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "conteo_id", nullable = false) private ConteoInventario conteo;
    @Column(name = "producto_id", nullable = false) private Long productoId;
    @Column(name = "cantidad_sistema", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadSistema;
    @Column(name = "cantidad_contada", nullable = false, precision = 14, scale = 3) private BigDecimal cantidadContada;
    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal diferencia;
    @Column(name = "existencia_previa", nullable = false) private boolean existenciaPrevia;
    @Column(name = "version_existencia") private Integer versionExistencia;

    protected DetalleConteo() {}
    DetalleConteo(ConteoInventario conteo, Long productoId, BigDecimal sistema, BigDecimal contada, boolean previa, Integer version) {
        this.conteo = conteo; this.productoId = ConteoInventario.identificador(productoId);
        recapturar(sistema, contada, previa, version);
    }
    void recapturar(BigDecimal sistema, BigDecimal contada, boolean previa, Integer version) {
        BigDecimal sistemaValido = CalculoConteo.cantidad(sistema), contadaValida = CalculoConteo.cantidad(contada);
        if (previa ? version == null || version < 0 : version != null || sistemaValido.signum() != 0)
            throw new IllegalArgumentException("Referencia de existencia inválida");
        cantidadSistema = sistemaValido; cantidadContada = contadaValida;
        diferencia = CalculoConteo.diferencia(sistemaValido, contadaValida);
        existenciaPrevia = previa; versionExistencia = version;
    }
    public Long getId() { return id; }
    public Long getProductoId() { return productoId; }
    public BigDecimal getCantidadSistema() { return cantidadSistema; }
    public BigDecimal getCantidadContada() { return cantidadContada; }
    public BigDecimal getDiferencia() { return diferencia; }
    public boolean isExistenciaPrevia() { return existenciaPrevia; }
    public Integer getVersionExistencia() { return versionExistencia; }
}
