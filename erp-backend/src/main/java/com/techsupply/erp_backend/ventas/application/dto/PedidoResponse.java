package com.techsupply.erp_backend.ventas.application.dto;

import com.techsupply.erp_backend.ventas.domain.model.EstadoPedidoVenta;
import com.techsupply.erp_backend.ventas.domain.model.ModalidadVenta;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PedidoResponse(Long id, String folio, Long cotizacionId, Long clienteId, Long almacenId,
        Long creadoPorUsuarioId, Long vendedorUsuarioId, ModalidadVenta modalidad, EstadoPedidoVenta estado,
        Instant fecha, BigDecimal subtotal, BigDecimal impuestos, BigDecimal total, List<Linea> detalles,
        String motivoCancelacion, Long canceladoPorUsuarioId, Instant canceladoEn) {
    public PedidoResponse { detalles = List.copyOf(detalles); }
    public record Linea(Long id, Long productoId, BigDecimal cantidad, BigDecimal precioUnitario,
            BigDecimal descuentoPct, BigDecimal tasaImpuesto, BigDecimal cantidadReservada, BigDecimal cantidadDespachada) {}
}
