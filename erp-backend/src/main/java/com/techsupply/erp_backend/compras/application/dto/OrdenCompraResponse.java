package com.techsupply.erp_backend.compras.application.dto;

import com.techsupply.erp_backend.compras.domain.model.EstadoOrdenCompra;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrdenCompraResponse(Long id, String folio, Long proveedorId, Long departamentoId,
    Long creadaPorUsuarioId, Long aprobadaPorUsuarioId, Instant aprobadaEn, String motivoRechazo,
    Instant fecha, EstadoOrdenCompra estado, BigDecimal subtotal, BigDecimal impuestos, BigDecimal total,
    String observaciones, List<Linea> detalles) {
    public record Linea(Long id, Long productoId, BigDecimal cantidad, BigDecimal precioUnitario,
        BigDecimal descuentoPct, BigDecimal tasaImpuesto, BigDecimal cantidadAceptada, BigDecimal cantidadPendiente) {}
}
