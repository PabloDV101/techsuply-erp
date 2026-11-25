package com.techsupply.erp_backend.maestros.application.dto;

import java.math.BigDecimal;

public record ProductoResponse(
    Long id, String codigo, Long categoriaId, String categoria,
    String descripcion, String unidadMedida, BigDecimal precioReferencia, boolean activo
) {}
