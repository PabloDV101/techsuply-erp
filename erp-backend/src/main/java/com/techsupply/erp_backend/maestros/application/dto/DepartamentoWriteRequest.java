package com.techsupply.erp_backend.maestros.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DepartamentoWriteRequest(
    @NotBlank @Size(max = 20) String codigo,
    @NotBlank @Size(max = 100) String nombre
) {}
