package com.techsupply.erp_backend.ventas.application.dto;

import com.techsupply.erp_backend.ventas.domain.model.EstadoCotizacion;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record CotizacionResponse(Long id, String folio, Long clienteId, Long vendedorUsuarioId,
    Instant fecha, LocalDate vigenciaHasta, EstadoCotizacion estado, BigDecimal subtotal,
    BigDecimal impuestos, BigDecimal total, List<Linea> detalles) {
    public CotizacionResponse { detalles = List.copyOf(detalles); }
    public record Linea(Long id, Long productoId, BigDecimal cantidad, BigDecimal precioUnitario,
        BigDecimal descuentoPct, BigDecimal tasaImpuesto) {}
}
