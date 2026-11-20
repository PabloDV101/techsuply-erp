package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AsientoContableCancelRequest(@NotBlank @Size(max = 300) String motivo) {}
