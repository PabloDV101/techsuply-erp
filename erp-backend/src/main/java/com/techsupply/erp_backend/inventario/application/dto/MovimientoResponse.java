package com.techsupply.erp_backend.inventario.application.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record MovimientoResponse(Long id, Long productoId, Long almacenId, String tipo, String motivo,
                                 BigDecimal cantidad, BigDecimal costoUnitario, Instant fecha, Long usuarioId,
                                 Long detalleRecepcionId, Long detalleAjusteId, Long detalleDespachoId) {}
