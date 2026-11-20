package com.techsupply.erp_backend.contabilidad.application.dto;

import java.math.BigDecimal;
import java.util.List;

public record LibroMayorResponse(Long periodoId, Long cuentaId, String codigoCuenta, String nombreCuenta,
        BigDecimal saldoInicial, BigDecimal movimientoDebe, BigDecimal movimientoHaber,
        BigDecimal saldoFinal, List<LibroMayorLineaResponse> content, int page, int size,
        long totalElements, int totalPages) {}
