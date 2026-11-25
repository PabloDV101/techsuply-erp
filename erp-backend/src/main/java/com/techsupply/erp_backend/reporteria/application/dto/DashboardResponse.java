package com.techsupply.erp_backend.reporteria.application.dto;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(LocalDate from, LocalDate to, List<IndicadorDashboardResponse> indicators) {
    public DashboardResponse { indicators = List.copyOf(indicators); }
}
