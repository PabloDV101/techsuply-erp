package com.techsupply.erp_backend.compras.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FacturaProveedorResponse(Long id, String folioInterno, Long proveedorId, Long ordenCompraId,
    String numeroFacturaProveedor, LocalDate fechaEmision, LocalDate fechaVencimiento,
    BigDecimal subtotal, BigDecimal impuestos, BigDecimal total, String estado,
    Long asientoContableId, List<Linea> detalles) {
    public record Linea(Long detalleOrdenCompraId, BigDecimal cantidadFacturada, BigDecimal precioUnitario,
        BigDecimal descuentoPct, BigDecimal tasaImpuesto, BigDecimal subtotal, BigDecimal impuestos, BigDecimal total) {}
}
