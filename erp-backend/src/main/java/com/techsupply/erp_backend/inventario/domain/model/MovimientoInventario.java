package com.techsupply.erp_backend.inventario.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity @Table(name = "movimientos_inventario")
public class MovimientoInventario {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "producto_id", nullable = false) private Long productoId;
    @Column(name = "almacen_id", nullable = false) private Long almacenId;
    @Column(nullable = false, length = 12) private String tipo;
    @Column(nullable = false, length = 35) private String motivo;
    @Column(nullable = false, precision = 14, scale = 3) private BigDecimal cantidad;
    @Column(name = "costo_unitario", nullable = false, precision = 14, scale = 4) private BigDecimal costoUnitario;
    @Column(nullable = false) private Instant fecha;
    @Column(name = "usuario_id", nullable = false) private Long usuarioId;
    @Column(name = "detalle_recepcion_id", nullable = false, unique = true) private Long detalleRecepcionId;
    @Column(name = "clave_operacion", nullable = false, unique = true, length = 120) private String claveOperacion;
    @Column(columnDefinition = "text") private String referencia;
    protected MovimientoInventario() {}
    public Long getId() { return id; }
    public Long getDetalleRecepcionId() { return detalleRecepcionId; }
    public BigDecimal getCantidad() { return cantidad; }
}
