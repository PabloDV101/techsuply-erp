package com.techsupply.erp_backend.maestros.application.dto;

import java.math.BigDecimal;

public record ProductoResponse(Long id, String sku, String nombre, String descripcion, String unidadMedida,
    Long categoriaId, Long proveedorPrincipalId, BigDecimal precioLista, BigDecimal costoReferencia,
    BigDecimal tasaImpuesto, BigDecimal puntoReorden, BigDecimal cantidadReorden, boolean activo) {}
