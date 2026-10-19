package com.techsupply.erp_backend.security.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CrearPermisoRequest(
    @NotBlank @Size(max = 60) String codigo,
    @NotBlank @Size(max = 40) String modulo,
    @NotBlank @Size(max = 30) String accion,
    @Size(max = 250) String descripcion
) {}
