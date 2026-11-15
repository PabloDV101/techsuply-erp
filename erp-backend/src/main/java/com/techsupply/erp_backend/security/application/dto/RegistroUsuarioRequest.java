package com.techsupply.erp_backend.security.application.dto;

import com.techsupply.erp_backend.security.application.validation.Utf8Password;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegistroUsuarioRequest(
    @NotBlank @Size(max = 60) String username,
    @NotBlank @Email @Size(max = 150) String correo,
    @NotBlank @Size(min = 12, max = 72) @Utf8Password String password
) {}
