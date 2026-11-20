package com.techsupply.erp_backend.compras.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record FacturaProveedorWriteRequest(
    @NotNull @Positive Long ordenCompraId,
    @NotBlank @Size(max=60) String numeroFacturaProveedor,
    @NotNull LocalDate fechaEmision,
    @NotNull LocalDate fechaVencimiento,
    @NotEmpty List<@NotNull @Valid Linea> detalles) {
    public record Linea(
        @NotNull @Positive Long detalleOrdenCompraId,
        @NotNull @DecimalMin(value="0",inclusive=false) @Digits(integer=11,fraction=3) BigDecimal cantidadFacturada,
        @NotNull @DecimalMin("0") @Digits(integer=12,fraction=2) BigDecimal precioUnitario,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal descuentoPct,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer=3,fraction=2) BigDecimal tasaImpuesto) {}
}
