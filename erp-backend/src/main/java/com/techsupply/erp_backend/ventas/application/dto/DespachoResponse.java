package com.techsupply.erp_backend.ventas.application.dto;

import com.techsupply.erp_backend.ventas.domain.model.EstadoDespacho;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record DespachoResponse(Long id, String folio, Long pedidoId, Long almacenId, Long registradoPorUsuarioId,
        Instant fecha, EstadoDespacho estado, List<Linea> detalles) {
    public DespachoResponse { detalles = List.copyOf(detalles); }
    public record Linea(Long id, Long detallePedidoId, BigDecimal cantidad, BigDecimal costoUnitario) {}
}
