package com.techsupply.erp_backend.contabilidad.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CuentaPorCobrarResponse(Long id, Long facturaVentaId, String folio,
        Long clienteId, BigDecimal montoOriginal, BigDecimal saldoPendiente,
        LocalDate fechaEmision, LocalDate fechaVencimiento) {}
