package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PeriodoContableCreateRequest(@NotNull LocalDate fechaInicio, @NotNull LocalDate fechaFin) {}
