package com.techsupply.erp_backend.contabilidad.application.dto;

import java.math.BigDecimal;

public record BalancePruebaFilaResponse(Long cuentaId, String codigoCuenta, String nombreCuenta,
        BigDecimal aperturaDeudora, BigDecimal aperturaAcreedora,
        BigDecimal movimientosDebe, BigDecimal movimientosHaber,
        BigDecimal cierreDeudor, BigDecimal cierreAcreedor) {}
