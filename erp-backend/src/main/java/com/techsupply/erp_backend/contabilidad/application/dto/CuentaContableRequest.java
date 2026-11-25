package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CuentaContableRequest(
    @NotBlank @Size(max=30) String codigo,
    @NotBlank @Size(max=150) String nombre,
    @NotBlank @Pattern(regexp="ACTIVO|PASIVO|CAPITAL|INGRESO|COSTO|GASTO") String tipo,
    @NotBlank @Pattern(regexp="DEUDORA|ACREEDORA") String naturaleza,
    @Min(1) @Max(10) short nivel,
    Long cuentaPadreId,
    boolean aceptaMovimientos
) {}
