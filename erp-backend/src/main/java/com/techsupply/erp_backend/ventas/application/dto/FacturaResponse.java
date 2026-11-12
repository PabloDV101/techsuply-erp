package com.techsupply.erp_backend.ventas.application.dto;

import com.techsupply.erp_backend.ventas.domain.model.EstadoFacturaVenta;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FacturaResponse(Long id,String folio,Long pedidoId,Long clienteId,Long emitidaPorUsuarioId,
        LocalDate fechaEmision,LocalDate fechaVencimiento,BigDecimal subtotal,BigDecimal impuestos,BigDecimal total,
        EstadoFacturaVenta estado,List<Linea> detalles) {
    public FacturaResponse {detalles=List.copyOf(detalles);}
    public record Linea(Long id,Long detallePedidoId,Long productoId,BigDecimal cantidad,BigDecimal precioUnitario,
            BigDecimal descuentoPct,BigDecimal tasaImpuesto,BigDecimal subtotal,BigDecimal impuestos,BigDecimal total) {}
}
