package com.techsupply.erp_backend.reporteria.application.dto;

import com.techsupply.erp_backend.reporteria.application.IndicadorDashboard;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record IndicadorDashboardResponse(IndicadorDashboard code, String source, LocalDate from, LocalDate to,
        Instant asOf, String availability, BigDecimal value, String unit, String unavailabilityReason) {}
