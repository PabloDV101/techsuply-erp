package com.techsupply.erp_backend.compras.application.dto;

import com.techsupply.erp_backend.compras.domain.model.EstadoRecepcionCompra;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record RecepcionCompraResponse(Long id, String folio, Long ordenCompraId, Long almacenId,
    Long registradaPorUsuarioId, String claveIdempotencia, Instant fecha, EstadoRecepcionCompra estado,
    String referenciaExterna, Instant confirmadaEn, List<Linea> detalles) {
    public record Linea(Long id, Long detalleOrdenCompraId, BigDecimal cantidadRecibida, BigDecimal cantidadAceptada,
        BigDecimal cantidadRechazada, BigDecimal precioUnitarioRecibido, String motivoRechazo) {}
}
