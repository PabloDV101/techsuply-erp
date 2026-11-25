package com.techsupply.erp_backend.maestros.application.dto;

import java.math.BigDecimal;

public record ClienteResponse(
    Long id, String codigo, String ruc, String razonSocial, String contacto,
    String telefono, String email, String direccion, BigDecimal limiteCredito,
    short plazoPagoDias, boolean activo
) {}
