package com.techsupply.erp_backend.maestros.application.dto;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ClienteWriteRequest(
    @NotBlank @Size(max = 20) String codigo,
    @NotBlank @Size(max = 160) String nombreRazonSocial,
    @Size(max = 20) String identificadorFiscal,
    @Email @Size(max = 150) String correo,
    @Size(max = 30) String telefono,
    String direccion,
    @NotNull @DecimalMin("0.00") @Digits(integer = 12, fraction = 2) BigDecimal limiteCredito,
    @NotNull @Min(0) Short plazoCreditoDias
) {}
