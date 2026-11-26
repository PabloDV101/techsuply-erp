package com.techsupply.erp_backend.compras.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FacturaProveedorResumenResponse(Long id, String folioInterno, Long proveedorId, Long ordenCompraId,
        String numeroFacturaProveedor, LocalDate fechaEmision, LocalDate fechaVencimiento,
        BigDecimal subtotal, BigDecimal impuestos, BigDecimal total, String estado, Long asientoContableId) {}
