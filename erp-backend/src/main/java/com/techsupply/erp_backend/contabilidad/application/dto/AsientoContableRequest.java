package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record AsientoContableRequest(
    @NotNull LocalDate fechaContable,
    @NotBlank @Size(max=300) String descripcion,
    @NotBlank @Size(max=30) String origenModulo,
    @NotBlank @Size(max=40) String origenTipo,
    Long origenId,
    @Size(max=100) String claveIdempotencia,
    @NotNull @Size(min=2) List<@Valid PartidaAsientoRequest> partidas
) {}
