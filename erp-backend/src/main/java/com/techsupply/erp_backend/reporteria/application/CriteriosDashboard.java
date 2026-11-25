package com.techsupply.erp_backend.reporteria.application;

import java.time.LocalDate;
import java.util.Set;

/** Criterios autorizados para una consulta; no contiene datos de las fuentes. */
public record CriteriosDashboard(LocalDate from, LocalDate to, Long periodId,
        Set<IndicadorDashboard> indicadores) {
    public CriteriosDashboard {
        indicadores = Set.copyOf(indicadores);
    }
}
