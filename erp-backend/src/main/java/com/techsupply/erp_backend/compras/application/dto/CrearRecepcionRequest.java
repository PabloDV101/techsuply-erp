package com.techsupply.erp_backend.compras.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CrearRecepcionRequest(@NotNull @Positive Long ordenCompraId, @NotNull @Positive Long almacenId,
    @NotBlank @Size(max = 120) String claveIdempotencia, @Size(max = 80) String referenciaExterna,
    @NotEmpty List<@NotNull @Valid Linea> detalles) {
    public record Linea(@NotNull @Positive Long detalleOrdenCompraId,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal cantidadRecibida,
        @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal cantidadAceptada,
        @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal cantidadRechazada,
        @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal precioUnitarioRecibido,
        String motivoRechazo) {}
}
