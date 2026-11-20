package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;

public record AplicarCobroRequest(@NotNull @Positive Long cuentaPorCobrarId,
        @NotNull @DecimalMin(value="0.01") @Digits(integer=12, fraction=2) BigDecimal monto,
        @NotBlank @Size(max=100) String claveIdempotencia, @NotNull LocalDate fechaContable) {}
