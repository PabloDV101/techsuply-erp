package com.techsupply.erp_backend.security.application.dto;

import com.techsupply.erp_backend.security.application.validation.Utf8Password;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RecuperarCuentaLocalRequest(
    @NotBlank @Size(max = 150) String identificador,
    @NotBlank @Size(min = 32, max = 128) String claveRecuperacion,
    @NotBlank @Size(min = 12, max = 72) @Utf8Password String contrasenaNueva
) {}
