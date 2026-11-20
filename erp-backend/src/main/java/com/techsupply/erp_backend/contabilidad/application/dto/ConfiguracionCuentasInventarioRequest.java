package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfiguracionCuentasInventarioRequest(
    @NotNull @Positive Long inventarioCuentaId,
    @NotNull @Positive Long mercanciaRecibidaNoFacturadaCuentaId,
    @NotNull @Positive Long costoDeVentasCuentaId,
    @NotNull @Positive Long ajustePositivoCuentaId,
    @NotNull @Positive Long ajusteNegativoCuentaId) {}
