package com.techsupply.erp_backend.security.application.dto;

import jakarta.validation.constraints.NotNull;

public record ActualizarEstadoUsuarioRequest(@NotNull Boolean activo) {}
