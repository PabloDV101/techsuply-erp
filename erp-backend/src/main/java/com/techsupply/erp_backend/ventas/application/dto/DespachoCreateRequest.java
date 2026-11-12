package com.techsupply.erp_backend.ventas.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record DespachoCreateRequest(@NotNull @Positive Long pedidoId, @NotBlank @Size(max = 100) String claveIdempotencia,
        @NotEmpty List<@NotNull @Valid Linea> detalles) {
    public record Linea(@NotNull @Positive Long detallePedidoId,
            @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal cantidad) {}
}
