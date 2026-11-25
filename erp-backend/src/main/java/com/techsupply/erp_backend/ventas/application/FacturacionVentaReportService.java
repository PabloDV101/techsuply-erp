package com.techsupply.erp_backend.ventas.application;

import com.techsupply.erp_backend.ventas.application.dto.FacturacionVentaReportResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FacturacionVentaReportService {
    private final JdbcTemplate db;

    public FacturacionVentaReportService(JdbcTemplate db) { this.db = db; }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('VENTAS_FACTURAS_LEER')")
    public FacturacionVentaReportResponse consultar(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango de fechas inválido");
        }
        BigDecimal subtotal = db.queryForObject("""
            SELECT COALESCE(SUM(subtotal), 0) FROM facturas_venta
            WHERE estado = 'EMITIDA' AND fecha_emision BETWEEN ? AND ?
            """, BigDecimal.class, from, to);
        return new FacturacionVentaReportResponse(from, to, subtotal);
    }
}
