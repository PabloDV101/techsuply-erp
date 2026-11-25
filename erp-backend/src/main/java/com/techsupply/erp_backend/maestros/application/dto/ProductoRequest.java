package com.techsupply.erp_backend.maestros.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record ProductoRequest(
    @NotBlank @Size(max=30) String codigo,
    @NotNull Long categoriaId,
    @NotBlank @Size(max=160) String descripcion,
    @NotBlank @Size(max=20) String unidadMedida,
    @NotNull @DecimalMin("0") BigDecimal precioReferencia
) {}
