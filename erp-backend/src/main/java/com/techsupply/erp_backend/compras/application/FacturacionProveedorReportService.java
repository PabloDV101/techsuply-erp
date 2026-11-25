package com.techsupply.erp_backend.compras.application;

import com.techsupply.erp_backend.compras.application.dto.FacturacionProveedorReportResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FacturacionProveedorReportService {
    private final JdbcTemplate db;

    public FacturacionProveedorReportService(JdbcTemplate db) { this.db = db; }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_LEER')")
    public FacturacionProveedorReportResponse consultar(LocalDate from, LocalDate to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rango de fechas inválido");
        }
        BigDecimal subtotal = db.queryForObject("""
            SELECT COALESCE(SUM(subtotal), 0) FROM facturas_proveedor
            WHERE estado = 'VALIDADA' AND fecha_emision BETWEEN ? AND ?
            """, BigDecimal.class, from, to);
        return new FacturacionProveedorReportResponse(from, to, subtotal);
    }
}
