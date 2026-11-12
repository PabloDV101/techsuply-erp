package com.techsupply.erp_backend.maestros.application.dto;

public record ProveedorResponse(
    Long id, String codigo, String nombreRazonSocial, String identificadorFiscal,
    String contacto, String correo, String telefono, String direccion,
    short plazoCreditoDias, boolean activo
) {}
