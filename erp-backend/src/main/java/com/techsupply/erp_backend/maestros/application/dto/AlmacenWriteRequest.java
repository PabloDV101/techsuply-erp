package com.techsupply.erp_backend.maestros.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AlmacenWriteRequest(
    @NotBlank @Size(max = 20) String codigo,
    @NotBlank @Size(max = 100) String nombre,
    @Size(max = 120) String sede,
    @Size(max = 200) String ubicacion
) {}
