package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record AsientoContableCreateRequest(
        @NotNull @Positive Long periodoId,
        @NotNull LocalDate fechaContable,
        @NotBlank @Size(max = 300) String descripcion,
        @NotBlank @Size(max = 120) String claveIdempotencia,
        @NotNull @Size(min = 2, max = 32767) List<@NotNull @Valid PartidaAsientoCreateRequest> partidas) {}
