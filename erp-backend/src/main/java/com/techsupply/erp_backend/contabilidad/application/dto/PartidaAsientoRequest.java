package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record PartidaAsientoRequest(
    @NotNull Long cuentaId,
    @NotNull @DecimalMin("0") BigDecimal debe,
    @NotNull @DecimalMin("0") BigDecimal haber,
    @Size(max=200) String descripcion
) {}
