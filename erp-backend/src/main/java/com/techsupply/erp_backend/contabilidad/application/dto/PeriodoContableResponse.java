package com.techsupply.erp_backend.contabilidad.application.dto;

import java.time.LocalDate;

public record PeriodoContableResponse(Long id,LocalDate fechaInicio,LocalDate fechaFin,String estado) {}
