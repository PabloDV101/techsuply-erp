package com.techsupply.erp_backend.contabilidad.application.dto;

import com.techsupply.erp_backend.contabilidad.domain.model.NaturalezaCuenta;
import com.techsupply.erp_backend.contabilidad.domain.model.TipoCuenta;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CuentaContableWriteRequest(
    @NotBlank @Size(max = 30) String codigo,
    @NotBlank @Size(max = 150) String nombre,
    @NotNull TipoCuenta tipo,
    @NotNull NaturalezaCuenta naturaleza,
    @Positive Long cuentaPadreId,
    @NotNull Boolean aceptaMovimientos
) {}
