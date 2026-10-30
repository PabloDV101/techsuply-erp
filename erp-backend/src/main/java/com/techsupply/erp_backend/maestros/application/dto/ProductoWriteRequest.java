package com.techsupply.erp_backend.maestros.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductoWriteRequest(
    @NotBlank @Size(max = 30) String sku,
    @NotBlank @Size(max = 160) String nombre,
    String descripcion,
    @NotBlank @Size(max = 20) String unidadMedida,
    @NotNull @Positive Long categoriaId,
    @Positive Long proveedorPrincipalId,
    @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal precioLista,
    @NotNull @DecimalMin("0") @Digits(integer = 12, fraction = 2) BigDecimal costoReferencia,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal tasaImpuesto,
    @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal puntoReorden,
    @NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal cantidadReorden
) {}
