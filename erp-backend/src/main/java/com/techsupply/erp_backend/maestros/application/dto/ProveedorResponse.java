package com.techsupply.erp_backend.maestros.application.dto;

public record ProveedorResponse(
    Long id, String codigo, String ruc, String razonSocial, String contacto,
    String telefono, String email, String direccion, boolean activo
) {}
