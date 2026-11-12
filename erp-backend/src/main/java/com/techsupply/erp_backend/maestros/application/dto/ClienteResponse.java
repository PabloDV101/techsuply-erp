package com.techsupply.erp_backend.maestros.application.dto;

import java.math.BigDecimal;

public record ClienteResponse(Long id, String codigo, String nombreRazonSocial, String identificadorFiscal,
    String correo, String telefono, String direccion, BigDecimal limiteCredito, short plazoCreditoDias, boolean activo) {}
