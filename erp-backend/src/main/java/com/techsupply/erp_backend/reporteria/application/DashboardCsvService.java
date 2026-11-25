package com.techsupply.erp_backend.reporteria.application;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardCsvService {
    private final DashboardService dashboard;
    private final AuditTrail audit;

    public DashboardCsvService(DashboardService dashboard, AuditTrail audit) {
        this.dashboard = dashboard;
        this.audit = audit;
    }

    @Transactional(isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("isAuthenticated()")
    public byte[] exportar(LocalDate from, LocalDate to, Long periodId) {
        var result = dashboard.consultar(from, to, periodId);
        var csv = new StringBuilder("requestedFrom,requestedTo,periodId,code,source,from,to,asOf,availability,value,unit,unavailabilityReason\r\n");
        for (var i : result.indicators()) {
            Object[] cells = {from, to, periodId, i.code(), i.source(), i.from(), i.to(), i.asOf(),
                i.availability(), i.value() == null ? null : i.value().toPlainString(), i.unit(), i.unavailabilityReason()};
            for (int n = 0; n < cells.length; n++) {
                if (n > 0) csv.append(',');
                csv.append(cell(cells[n]));
            }
            csv.append("\r\n");
        }
        byte[] content = csv.toString().getBytes(StandardCharsets.UTF_8);
        audit.change(AuditAction.DASHBOARD_CSV_GENERATED, null);
        return content;
    }

    // All text originates in server-owned definitions; no free-form user cells.
    private static String cell(Object value) {
        return value == null ? "" : "\"" + value.toString().replace("\"", "\"\"") + "\"";
    }
}
