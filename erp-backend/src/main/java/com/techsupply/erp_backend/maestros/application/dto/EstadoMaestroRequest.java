package com.techsupply.erp_backend.maestros.application.dto;

import jakarta.validation.constraints.NotNull;

public record EstadoMaestroRequest(@NotNull Boolean activo) {}
