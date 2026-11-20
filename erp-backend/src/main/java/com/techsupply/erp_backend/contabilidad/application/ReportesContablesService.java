package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.contabilidad.infrastructure.persistence.CuentaContableRepository;
import com.techsupply.erp_backend.contabilidad.infrastructure.persistence.PeriodoContableRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only accounting views over posted journal lines. */
@Service
public class ReportesContablesService {
    private static final Map<String, String> SORT_FIELDS = Map.of("codigoCuenta", "codigo_cuenta", "cuentaId", "cuenta_id");
    private static final String POSTED = """
        SELECT p.cuenta_contable_id, a.fecha_contable, p.debe, p.haber
        FROM partidas_asiento p
        JOIN asientos_contables a ON a.id = p.asiento_id
        WHERE a.estado IN ('CONTABILIZADO', 'REVERSADO')
        """;
    private static final String TRIAL_BALANCE = """
        WITH movimientos AS (
          %s
        ), acumulados AS (
          SELECT c.id AS cuenta_id, c.codigo AS codigo_cuenta, c.nombre AS nombre_cuenta,
            COALESCE(sum(m.debe - m.haber) FILTER (WHERE m.fecha_contable < ?), 0) AS apertura_neta,
            COALESCE(sum(m.debe) FILTER (WHERE m.fecha_contable BETWEEN ? AND ?), 0) AS movimientos_debe,
            COALESCE(sum(m.haber) FILTER (WHERE m.fecha_contable BETWEEN ? AND ?), 0) AS movimientos_haber
          FROM cuentas_contables c
          LEFT JOIN movimientos m ON m.cuenta_contable_id = c.id
          GROUP BY c.id, c.codigo, c.nombre
        ), saldos AS (
          SELECT cuenta_id, codigo_cuenta, nombre_cuenta,
            GREATEST(apertura_neta, 0) AS apertura_deudora,
            GREATEST(-apertura_neta, 0) AS apertura_acreedora,
            movimientos_debe, movimientos_haber,
            GREATEST(apertura_neta + movimientos_debe - movimientos_haber, 0) AS cierre_deudor,
            GREATEST(-(apertura_neta + movimientos_debe - movimientos_haber), 0) AS cierre_acreedor
          FROM acumulados
          WHERE apertura_neta <> 0 OR movimientos_debe <> 0 OR movimientos_haber <> 0
        )
        """.formatted(POSTED);
    private final JdbcTemplate db;
    private final PeriodoContableRepository periodos;
    private final CuentaContableRepository cuentas;

    public ReportesContablesService(JdbcTemplate db, PeriodoContableRepository periodos,
            CuentaContableRepository cuentas) {
        this.db = db;
        this.periodos = periodos;
        this.cuentas = cuentas;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public BalancePruebaResponse balancePrueba(Long periodoId, int page, int size, String sort, String direction) {
        var periodo = periodos.findById(periodoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Periodo no encontrado"));
        var pageable = PageRequests.of(page, size, sort, direction, SORT_FIELDS, "cuenta_id");
        var args = new Object[] {periodo.getFechaInicio(), periodo.getFechaInicio(), periodo.getFechaFin(),
            periodo.getFechaInicio(), periodo.getFechaFin()};
        var totals = db.queryForMap(TRIAL_BALANCE + """
            SELECT count(*) AS total,
              COALESCE(sum(apertura_deudora),0) AS apertura_deudora,
              COALESCE(sum(apertura_acreedora),0) AS apertura_acreedora,
              COALESCE(sum(movimientos_debe),0) AS movimientos_debe,
              COALESCE(sum(movimientos_haber),0) AS movimientos_haber,
              COALESCE(sum(cierre_deudor),0) AS cierre_deudor,
              COALESCE(sum(cierre_acreedor),0) AS cierre_acreedor
            FROM saldos
            """, args);
        String order = SORT_FIELDS.get(sort) + ("DESC".equalsIgnoreCase(direction) ? " DESC" : " ASC")
            + ("cuenta_id".equals(SORT_FIELDS.get(sort)) ? "" : ", cuenta_id ASC");
        List<BalancePruebaFilaResponse> filas = db.query(TRIAL_BALANCE + """
            SELECT cuenta_id, codigo_cuenta, nombre_cuenta, apertura_deudora, apertura_acreedora,
              movimientos_debe, movimientos_haber, cierre_deudor, cierre_acreedor
            FROM saldos ORDER BY %s LIMIT ? OFFSET ?
            """.formatted(order), (rs, row) -> new BalancePruebaFilaResponse(
                rs.getLong("cuenta_id"), rs.getString("codigo_cuenta"), rs.getString("nombre_cuenta"),
                rs.getBigDecimal("apertura_deudora"), rs.getBigDecimal("apertura_acreedora"),
                rs.getBigDecimal("movimientos_debe"), rs.getBigDecimal("movimientos_haber"),
                rs.getBigDecimal("cierre_deudor"), rs.getBigDecimal("cierre_acreedor")),
            periodo.getFechaInicio(), periodo.getFechaInicio(), periodo.getFechaFin(), periodo.getFechaInicio(),
            periodo.getFechaFin(), pageable.getPageSize(), pageable.getOffset());
        long total = ((Number) totals.get("total")).longValue();
        return new BalancePruebaResponse(periodoId, filas, page, size, total, pages(total, size),
            decimal(totals, "apertura_deudora"), decimal(totals, "apertura_acreedora"),
            decimal(totals, "movimientos_debe"), decimal(totals, "movimientos_haber"),
            decimal(totals, "cierre_deudor"), decimal(totals, "cierre_acreedor"));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public LibroMayorResponse libroMayor(Long periodoId, Long cuentaId, int page, int size) {
        var periodo = periodos.findById(periodoId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Periodo no encontrado"));
        var cuenta = cuentas.findById(cuentaId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta no encontrada"));
        var pageable = PageRequests.of(page, size, "fecha", "asc", Map.of("fecha", "fecha"));
        var resumen = db.queryForMap("""
            SELECT COALESCE(sum(p.debe - p.haber) FILTER (WHERE a.fecha_contable < ?),0) AS saldo_inicial,
              COALESCE(sum(p.debe) FILTER (WHERE a.fecha_contable BETWEEN ? AND ?),0) AS movimiento_debe,
              COALESCE(sum(p.haber) FILTER (WHERE a.fecha_contable BETWEEN ? AND ?),0) AS movimiento_haber
            FROM partidas_asiento p JOIN asientos_contables a ON a.id=p.asiento_id
            WHERE p.cuenta_contable_id=? AND a.estado IN ('CONTABILIZADO','REVERSADO')
              AND a.fecha_contable <= ?
            """, periodo.getFechaInicio(), periodo.getFechaInicio(), periodo.getFechaFin(),
            periodo.getFechaInicio(), periodo.getFechaFin(), cuentaId, periodo.getFechaFin());
        BigDecimal opening = decimal(resumen, "saldo_inicial");
        BigDecimal debit = decimal(resumen, "movimiento_debe");
        BigDecimal credit = decimal(resumen, "movimiento_haber");
        long total = db.queryForObject("""
            SELECT count(*) FROM partidas_asiento p JOIN asientos_contables a ON a.id=p.asiento_id
            WHERE p.cuenta_contable_id=? AND a.estado IN ('CONTABILIZADO','REVERSADO')
              AND a.fecha_contable BETWEEN ? AND ?
            """, Long.class, cuentaId, periodo.getFechaInicio(), periodo.getFechaFin());
        List<LibroMayorLineaResponse> lineas = db.query("""
            WITH movimientos AS (
              SELECT a.id AS asiento_id, a.folio, a.fecha_contable, a.descripcion AS descripcion_asiento,
                p.numero_linea, p.descripcion AS descripcion_partida, p.debe, p.haber,
                ? + sum(p.debe-p.haber) OVER (ORDER BY a.fecha_contable,a.id,p.numero_linea
                  ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW) AS saldo
              FROM partidas_asiento p JOIN asientos_contables a ON a.id=p.asiento_id
              WHERE p.cuenta_contable_id=? AND a.estado IN ('CONTABILIZADO','REVERSADO')
                AND a.fecha_contable BETWEEN ? AND ?
            )
            SELECT * FROM movimientos ORDER BY fecha_contable,asiento_id,numero_linea LIMIT ? OFFSET ?
            """, (rs, row) -> new LibroMayorLineaResponse(rs.getLong("asiento_id"), rs.getString("folio"),
                rs.getDate("fecha_contable").toLocalDate(), rs.getString("descripcion_asiento"),
                rs.getShort("numero_linea"), rs.getString("descripcion_partida"), rs.getBigDecimal("debe"),
                rs.getBigDecimal("haber"), rs.getBigDecimal("saldo")), opening, cuentaId,
            periodo.getFechaInicio(), periodo.getFechaFin(), pageable.getPageSize(), pageable.getOffset());
        return new LibroMayorResponse(periodoId, cuentaId, cuenta.getCodigo(), cuenta.getNombre(), opening,
            debit, credit, opening.add(debit).subtract(credit), lineas, page, size, total, pages(total, size));
    }

    private static BigDecimal decimal(Map<String, Object> row, String key) {
        BigDecimal value = (BigDecimal) row.get(key);
        return value == null ? BigDecimal.ZERO : value;
    }

    private static int pages(long total, int size) {
        return total == 0 ? 0 : (int) ((total + size - 1) / size);
    }
}
