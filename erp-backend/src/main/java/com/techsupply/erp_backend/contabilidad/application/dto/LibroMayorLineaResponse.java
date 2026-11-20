package com.techsupply.erp_backend.contabilidad.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record LibroMayorLineaResponse(Long asientoId, String folio, LocalDate fechaContable,
        String descripcionAsiento, short numeroLinea, String descripcionPartida,
        BigDecimal debe, BigDecimal haber, BigDecimal saldo) {}
