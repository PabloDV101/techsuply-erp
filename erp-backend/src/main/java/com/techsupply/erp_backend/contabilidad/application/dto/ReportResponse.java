package com.techsupply.erp_backend.contabilidad.application.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public record ReportResponse(
    String type,
    Long periodId,
    java.time.LocalDate fromDate,
    java.time.LocalDate toDate,
    List<ReportLine> lines,
    // Null means the report has no approved single aggregate; subtotals stay grouped by account type.
    BigDecimal total,
    String status,
    Map<String, BigDecimal> totalsByType
) {
    public ReportResponse(
        String type,
        Long periodId,
        java.time.LocalDate fromDate,
        java.time.LocalDate toDate,
        List<ReportLine> lines,
        BigDecimal total,
        String status
    ) {
        this(type, periodId, fromDate, toDate, lines, total, status, Map.of());
    }

    public record ReportLine(Long accountId,String accountCode,String accountName,String type,BigDecimal debit,BigDecimal credit,BigDecimal balance) {}
}
