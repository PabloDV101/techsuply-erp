package com.techsupply.erp_backend.security.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CrearUsuarioRequest(
    @NotBlank @Size(max = 60) String username,
    @NotBlank @Email @Size(max = 150) String correo,
    @NotBlank @Size(min = 12, max = 72) String password,
    @NotEmpty Set<@NotBlank @Size(max = 30) String> roles,
    Long empleadoId,
    Long clienteId
) {}
