package com.techsupply.erp_backend.security.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
    @NotBlank @Size(max = 150) String identificador,
    @NotBlank @Size(max = 72) String password
) {}
