package com.techsupply.erp_backend.contabilidad.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record BalancePruebaResponse(Long periodoId, List<BalancePruebaFilaResponse> content,
        int page, int size, long totalElements, int totalPages,
        BigDecimal totalAperturaDeudora, BigDecimal totalAperturaAcreedora,
        BigDecimal totalMovimientosDebe, BigDecimal totalMovimientosHaber,
        BigDecimal totalCierreDeudor, BigDecimal totalCierreAcreedor) {}
