package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/** Mapeo del esquema V11. La escritura y las transiciones se incorporan con sus casos de uso. */
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
