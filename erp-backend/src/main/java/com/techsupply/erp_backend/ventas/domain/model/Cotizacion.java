package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Cotización comercial; sus transiciones se coordinan en una transacción de aplicación. */
@Entity @Table(name = "cotizaciones")
public class Cotizacion {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "folio", nullable = false, length = 30) private String folio;
    @Column(name = "cliente_id", nullable = false) private Long clienteId;
    @Column(name = "vendedor_usuario_id", nullable = false) private Long vendedorUsuarioId;
    @Column(name = "fecha", nullable = false) private Instant fecha;
    @Column(name = "vigencia_hasta", nullable = false) private LocalDate vigenciaHasta;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 15) private EstadoCotizacion estado;
    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2) private BigDecimal subtotal;
    @Column(name = "impuestos", nullable = false, precision = 14, scale = 2) private BigDecimal impuestos;
    @Column(name = "total", nullable = false, precision = 14, scale = 2) private BigDecimal total;
    @Column(name = "clave_idempotencia", nullable = false, length = 100) private String claveIdempotencia;
    protected Cotizacion() {}
    public Cotizacion(String folio, Long clienteId, Long vendedorUsuarioId, Instant fecha,
            LocalDate vigenciaHasta, String clave, CalculoCotizacion.Totales totales) {
        this.folio = java.util.Objects.requireNonNull(folio);
        this.clienteId = java.util.Objects.requireNonNull(clienteId);
        this.vendedorUsuarioId = java.util.Objects.requireNonNull(vendedorUsuarioId);
        this.fecha = java.util.Objects.requireNonNull(fecha);
        this.vigenciaHasta = java.util.Objects.requireNonNull(vigenciaHasta);
        this.claveIdempotencia = java.util.Objects.requireNonNull(clave);
        this.subtotal = totales.subtotal(); this.impuestos = totales.impuestos(); this.total = totales.total();
        this.estado = EstadoCotizacion.BORRADOR;
    }
    public void emitir(LocalDate fechaNegocio) {
        if (estado != EstadoCotizacion.BORRADOR || !VigenciaCotizacion.vigente(vigenciaHasta, fechaNegocio))
            throw new IllegalStateException("Cotización no emitible");
        estado = EstadoCotizacion.VIGENTE;
    }
    public void aceptar(LocalDate fechaNegocio) {
        if (estado != EstadoCotizacion.VIGENTE || !VigenciaCotizacion.vigente(vigenciaHasta, fechaNegocio))
            throw new IllegalStateException("Cotización no aceptable");
        estado = EstadoCotizacion.ACEPTADA;
    }
    public void cancelar() {
        if (estado != EstadoCotizacion.BORRADOR && estado != EstadoCotizacion.VIGENTE)
            throw new IllegalStateException("La cotización no se puede cancelar en su estado actual");
        estado = EstadoCotizacion.CANCELADA;
    }
    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Long getClienteId() { return clienteId; }
    public Long getVendedorUsuarioId() { return vendedorUsuarioId; }
    public Instant getFecha() { return fecha; }
    public LocalDate getVigenciaHasta() { return vigenciaHasta; }
    public EstadoCotizacion getEstado() { return estado; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getImpuestos() { return impuestos; }
    public BigDecimal getTotal() { return total; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
}
