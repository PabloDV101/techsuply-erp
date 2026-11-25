package com.techsupply.erp_backend.ventas.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Total de subtotales de facturas comerciales emitidas por fecha de emisión. */
public record FacturacionVentaReportResponse(LocalDate from, LocalDate to, BigDecimal subtotal) {}
