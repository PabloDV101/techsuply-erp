package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ConfirmarCobroRequest(@NotNull LocalDate fechaContable) {}
