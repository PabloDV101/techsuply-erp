package com.techsupply.erp_backend.security.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CrearRolRequest(
    @NotBlank @Size(max = 30) String codigo,
    @NotBlank @Size(max = 80) String nombre,
    @Size(max = 250) String descripcion,
    @NotNull Set<@NotBlank @Size(max = 60) String> permisos
) {}
