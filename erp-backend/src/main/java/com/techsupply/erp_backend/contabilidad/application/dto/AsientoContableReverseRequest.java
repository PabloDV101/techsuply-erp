package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public record AsientoContableReverseRequest(@NotNull LocalDate fechaContable,
        @NotBlank @Size(max = 300) String motivo,
        @NotBlank @Size(max = 120) String claveIdempotencia) {}
