package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record PartidaAsientoCreateRequest(
        @NotNull @Positive Long cuentaId,
        @NotNull BigDecimal debe,
        @NotNull BigDecimal haber,
        String descripcion) {}
