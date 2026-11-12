package com.techsupply.erp_backend.ventas.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CotizacionCreateRequest(
    @NotNull @Positive Long clienteId,
    @NotNull LocalDate vigenciaHasta,
    @NotBlank @Size(max = 100) String claveIdempotencia,
    @NotEmpty List<@NotNull @Valid Linea> detalles
) {
    public record Linea(
        @NotNull @Positive Long productoId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal cantidad,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal precioUnitario,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal descuentoPct,
        @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal tasaImpuesto
    ) {}
}
