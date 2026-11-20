package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.dto.CuentaPorCobrarResponse;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Read-only customer subledger over invoices that have a posted accounting entry. */
@Service
public class CuentasPorCobrarService {
    private static final String SELECT = """
        select c.id,c.factura_venta_id,f.folio,c.cliente_id,c.monto_original,
               c.monto_original-coalesce(sum(a.monto_aplicado),0) as saldo_pendiente,c.fecha_emision,c.fecha_vencimiento
        from cuentas_por_cobrar c join facturas_venta f on f.id=c.factura_venta_id
        left join aplicaciones_cobro a on a.cuenta_por_cobrar_id=c.id
        """;

    private final JdbcTemplate db;

    public CuentasPorCobrarService(JdbcTemplate db) { this.db = db; }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTABILIDAD_CXC_LEER')")
    public PageResponse<CuentaPorCobrarResponse> listar(Long clienteId, int page, int size) {
        var pageable = PageRequests.of(page, size, "fechaEmision", "desc",
            Map.of("fechaEmision", "c.fecha_emision"), "c.id");
        if (clienteId != null && clienteId < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador de cliente inválido");
        String where = clienteId == null ? "" : " where c.cliente_id=?";
        long total = clienteId == null
            ? db.queryForObject("select count(*) from cuentas_por_cobrar", Long.class)
            : db.queryForObject("select count(*) from cuentas_por_cobrar where cliente_id=?", Long.class, clienteId);
        var mapper = mapper();
        var rows = clienteId == null
            ? db.query(SELECT + " group by c.id,f.id order by c.fecha_emision desc,c.id desc limit ? offset ?", mapper,
                pageable.getPageSize(), pageable.getOffset())
            : db.query(SELECT + where + " group by c.id,f.id order by c.fecha_emision desc,c.id desc limit ? offset ?", mapper,
                clienteId, pageable.getPageSize(), pageable.getOffset());
        return new PageResponse<>(rows, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTABILIDAD_CXC_LEER')")
    public CuentaPorCobrarResponse obtener(Long id) {
        if (id == null || id < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador inválido");
        return db.query(SELECT + " where c.id=? group by c.id,f.id", mapper(), id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cuenta por cobrar no encontrada"));
    }

    private static org.springframework.jdbc.core.RowMapper<CuentaPorCobrarResponse> mapper() {
        return (rs, row) -> new CuentaPorCobrarResponse(rs.getLong("id"), rs.getLong("factura_venta_id"),
            rs.getString("folio"), rs.getLong("cliente_id"), rs.getBigDecimal("monto_original"),
            rs.getBigDecimal("saldo_pendiente"), rs.getObject("fecha_emision", LocalDate.class),
            rs.getObject("fecha_vencimiento", LocalDate.class));
    }
}
