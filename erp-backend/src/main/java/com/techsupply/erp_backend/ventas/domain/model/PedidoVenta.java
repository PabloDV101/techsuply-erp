package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/** Mapeo del esquema V11. La escritura y las transiciones se incorporan con sus casos de uso. */
@Entity @Table(name = "pedidos_venta")
public class PedidoVenta {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "folio", nullable = false, length = 30) private String folio;
    @Column(name = "cliente_id", nullable = false) private Long clienteId;
    @Column(name = "vendedor_usuario_id", nullable = false) private Long vendedorUsuarioId;
    @Column(name = "cotizacion_id", nullable = false) private Long cotizacionId;
    @Column(name = "almacen_id", nullable = false) private Long almacenId;
    @Column(name = "creado_por_usuario_id", nullable = false) private Long creadoPorUsuarioId;
    @Enumerated(EnumType.STRING)
    @Column(name = "canal", nullable = false, length = 15) private CanalVenta canal;
    @Enumerated(EnumType.STRING)
    @Column(name = "modalidad", nullable = false, length = 15) private ModalidadVenta modalidad;
    @Column(name = "fecha", nullable = false) private Instant fecha;
    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 25) private EstadoPedidoVenta estado;
    @Column(name = "subtotal", nullable = false, precision = 14, scale = 2) private BigDecimal subtotal;
    @Column(name = "impuestos", nullable = false, precision = 14, scale = 2) private BigDecimal impuestos;
    @Column(name = "total", nullable = false, precision = 14, scale = 2) private BigDecimal total;
    @Column(name = "clave_idempotencia", nullable = false, length = 100) private String claveIdempotencia;
    @Column(name = "observaciones", columnDefinition = "text") private String observaciones;
    protected PedidoVenta() {}
    public Long getId() { return id; }
    public String getFolio() { return folio; }
    public Long getClienteId() { return clienteId; }
    public Long getVendedorUsuarioId() { return vendedorUsuarioId; }
    public Long getCotizacionId() { return cotizacionId; }
    public Long getAlmacenId() { return almacenId; }
    public Long getCreadoPorUsuarioId() { return creadoPorUsuarioId; }
    public CanalVenta getCanal() { return canal; }
    public ModalidadVenta getModalidad() { return modalidad; }
    public Instant getFecha() { return fecha; }
    public EstadoPedidoVenta getEstado() { return estado; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getImpuestos() { return impuestos; }
    public BigDecimal getTotal() { return total; }
    public String getClaveIdempotencia() { return claveIdempotencia; }
    public String getObservaciones() { return observaciones; }
}
