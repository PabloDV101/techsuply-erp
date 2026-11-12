package com.techsupply.erp_backend.maestros.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProveedorWriteRequest(
    @NotBlank @Size(max = 20) String codigo,
    @NotBlank @Size(max = 160) String nombreRazonSocial,
    @Size(max = 20) String identificadorFiscal,
    @Size(max = 100) String contacto,
    @Email @Size(max = 150) String correo,
    @Size(max = 30) String telefono,
    String direccion,
    @Min(0) Short plazoCreditoDias
) {}
