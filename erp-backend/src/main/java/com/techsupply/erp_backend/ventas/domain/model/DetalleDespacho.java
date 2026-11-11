package com.techsupply.erp_backend.ventas.domain.model;

import jakarta.persistence.*;
import java.math.BigDecimal;

/** Mapeo del esquema V11. La escritura y las transiciones se incorporan con sus casos de uso. */
@Entity @Table(name = "detalles_despacho")
public class DetalleDespacho {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "despacho_id", nullable = false) private Long despachoId;
    @Column(name = "detalle_pedido_id", nullable = false) private Long detallePedidoId;
    @Column(name = "cantidad", nullable = false, precision = 14, scale = 3) private BigDecimal cantidad;
    @Column(name = "costo_unitario", precision = 16, scale = 4) private BigDecimal costoUnitario;
    protected DetalleDespacho() {}
    public Long getId() { return id; }
    public Long getDespachoId() { return despachoId; }
    public Long getDetallePedidoId() { return detallePedidoId; }
    public BigDecimal getCantidad() { return cantidad; }
    public BigDecimal getCostoUnitario() { return costoUnitario; }
}
