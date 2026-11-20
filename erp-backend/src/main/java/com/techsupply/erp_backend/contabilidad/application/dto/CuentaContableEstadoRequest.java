package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.NotNull;

public record CuentaContableEstadoRequest(@NotNull Boolean activa) {}
