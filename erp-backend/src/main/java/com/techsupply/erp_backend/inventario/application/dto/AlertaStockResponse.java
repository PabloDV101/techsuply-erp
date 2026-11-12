package com.techsupply.erp_backend.inventario.application.dto;

import java.math.BigDecimal;

public record AlertaStockResponse(Long productoId, String sku, String nombre, BigDecimal disponibleGlobal,
    BigDecimal puntoReorden, BigDecimal cantidadReorden) {}
