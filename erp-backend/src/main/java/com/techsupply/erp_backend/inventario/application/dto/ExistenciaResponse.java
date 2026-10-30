package com.techsupply.erp_backend.inventario.application.dto;

import java.math.BigDecimal;

public record ExistenciaResponse(Long productoId, Long almacenId, BigDecimal cantidadFisica,
    BigDecimal cantidadReservada, BigDecimal cantidadDisponible) {}
