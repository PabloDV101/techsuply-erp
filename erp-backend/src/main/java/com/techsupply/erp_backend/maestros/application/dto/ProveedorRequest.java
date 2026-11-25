package com.techsupply.erp_backend.maestros.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProveedorRequest(
    @NotBlank @Size(max=20) String codigo,
    @NotBlank @Size(max=30) String ruc,
    @NotBlank @Size(max=160) String razonSocial,
    @Size(max=100) String contacto,
    @Size(max=30) String telefono,
    @Email @Size(max=150) String email,
    @Size(max=2000) String direccion
) {}
