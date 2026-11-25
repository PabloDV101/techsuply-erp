package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record PeriodoContableRequest(@NotNull LocalDate fechaInicio,@NotNull LocalDate fechaFin) {}
