package com.techsupply.erp_backend.compras.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Total de subtotales de facturas de proveedor validadas, por fecha de emisión. */
public record FacturacionProveedorReportResponse(LocalDate from, LocalDate to, BigDecimal subtotal) {}
