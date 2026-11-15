package com.techsupply.erp_backend.security.application.dto;

import com.techsupply.erp_backend.security.application.validation.Utf8Password;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambiarContrasenaRequest(
    @NotBlank @Size(max = 72) @Utf8Password String contrasenaActual,
    @NotBlank @Size(min = 12, max = 72) @Utf8Password String contrasenaNueva
) {}
