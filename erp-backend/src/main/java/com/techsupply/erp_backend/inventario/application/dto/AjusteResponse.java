package com.techsupply.erp_backend.inventario.application.dto;

import com.techsupply.erp_backend.inventario.domain.model.EstadoAjuste;
import com.techsupply.erp_backend.inventario.domain.model.TipoAjuste;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AjusteResponse(Long id, String folio, Long almacenId, String motivo, Long solicitadoPorUsuarioId,
    Long autorizadoPorUsuarioId, String claveIdempotencia, EstadoAjuste estado, String motivoRechazo,
    Instant fecha, Instant autorizadoEn, Instant aplicadoEn, List<Linea> detalles) {
    public record Linea(Long id, Long productoId, TipoAjuste tipo, BigDecimal cantidad, BigDecimal costoUnitario,
                        Long detalleConteoId, String motivoLinea) {}
}
