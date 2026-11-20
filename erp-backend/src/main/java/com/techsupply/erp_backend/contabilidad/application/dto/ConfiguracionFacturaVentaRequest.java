package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ConfiguracionFacturaVentaRequest(
    @NotNull @Positive Long clientesPorCobrarCuentaId,
    @NotNull @Positive Long ventasCuentaId,
    @NotNull @Positive Long impuestosTrasladadosCuentaId) {}
