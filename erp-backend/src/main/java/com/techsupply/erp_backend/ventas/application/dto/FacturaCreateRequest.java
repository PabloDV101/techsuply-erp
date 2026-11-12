package com.techsupply.erp_backend.ventas.application.dto;

import jakarta.validation.constraints.*;

public record FacturaCreateRequest(@NotNull @Positive Long pedidoId,@NotBlank @Size(max=100) String claveIdempotencia) {}
