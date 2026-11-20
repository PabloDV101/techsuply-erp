package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;

public record CobroClienteCreateRequest(@NotNull @Positive Long clienteId, @NotNull Instant fecha,
        @NotNull @DecimalMin(value="0.01") @Digits(integer=12, fraction=2) BigDecimal monto,
        @NotBlank @Pattern(regexp="EFECTIVO|TRANSFERENCIA|CHEQUE|TARJETA|OTRO") String medio,
        @Size(max=100) String referencia, @NotBlank @Size(max=100) String claveIdempotencia) {}
