package com.techsupply.erp_backend.contabilidad.application.dto;

import com.techsupply.erp_backend.contabilidad.domain.model.EstadoPeriodoContable;
import com.techsupply.erp_backend.contabilidad.domain.model.PeriodoContable;
import java.time.Instant;
import java.time.LocalDate;

public record PeriodoContableResponse(Long id, LocalDate fechaInicio, LocalDate fechaFin,
        EstadoPeriodoContable estado, Long cerradoPorUsuarioId, Instant cerradoEn) {
    public static PeriodoContableResponse from(PeriodoContable periodo) {
        return new PeriodoContableResponse(periodo.getId(), periodo.getFechaInicio(), periodo.getFechaFin(),
            periodo.getEstado(), periodo.getCerradoPorUsuarioId(), periodo.getCerradoEn());
    }
}
