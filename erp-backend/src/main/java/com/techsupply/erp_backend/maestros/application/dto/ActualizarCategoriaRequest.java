package com.techsupply.erp_backend.maestros.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record ActualizarCategoriaRequest(
    @NotBlank @Size(max = 20) String codigo,
    @NotBlank @Size(max = 100) String nombre,
    @Size(max = 250) String descripcion,
    @Positive Long categoriaPadreId
) {}
