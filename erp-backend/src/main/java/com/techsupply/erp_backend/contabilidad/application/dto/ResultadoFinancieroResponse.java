package com.techsupply.erp_backend.contabilidad.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Resultado del estado financiero contable para un periodo. */
public record ResultadoFinancieroResponse(Long periodoId, LocalDate inicio, LocalDate fin, BigDecimal resultado) {}
