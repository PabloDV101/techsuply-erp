package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Creates and posts an invoice source entry atomically with its durable handoff status. */
@Service
public class ProcesadorFacturaVentaContable {
    private final JdbcTemplate db;
    private final AsientoContableService entries;
    private final AuditTrail audit;
    private final AuthenticatedUserProvider users;

    public ProcesadorFacturaVentaContable(JdbcTemplate db, AsientoContableService entries, AuditTrail audit,
            AuthenticatedUserProvider users) {
        this.db = db; this.entries = entries; this.audit = audit; this.users = users;
    }

    @Transactional
    public IntegracionFacturaVentaResponse procesar(Long facturaId, LocalDate requestedDate) {
        if (facturaId == null || facturaId < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador inválido");
        var rows = db.query("""
            select i.estado,i.asiento_contable_id,f.folio,f.fecha_emision,f.subtotal,f.impuestos,f.total
            from integracion_factura_venta_contabilidad i join facturas_venta f on f.id=i.factura_venta_id
            where i.factura_venta_id=? for update of i
            """, (rs, row) -> new Factura(rs.getString("estado"), (Long) rs.getObject("asiento_contable_id"),
                rs.getString("folio"), rs.getObject("fecha_emision", LocalDate.class), rs.getBigDecimal("subtotal"),
                rs.getBigDecimal("impuestos"), rs.getBigDecimal("total")), facturaId);
        if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Integración de factura no encontrada");
        Factura invoice = rows.getFirst();
        if ("CONTABILIZADA".equals(invoice.estado())) return status(facturaId);

        LocalDate date = requestedDate == null ? invoice.fechaEmision() : requestedDate;
        var periods = db.query("select id from periodos_contables where estado='ABIERTO' and fecha_inicio<=? and fecha_fin>=?",
            (rs, row) -> rs.getLong(1), date, date);
        if (periods.isEmpty()) throw new IntegracionFacturaVentaException(HttpStatus.CONFLICT, "PERIODO_NO_DISPONIBLE",
            requestedDate == null ? "La fecha de emisión no está en un periodo abierto; indique una fecha contable abierta" :
                "La fecha contable debe pertenecer a un periodo abierto");

        Map<String, Cuenta> accounts = db.query("""
            select m.concepto,c.id,c.tipo,c.naturaleza,c.activa,c.acepta_movimientos
            from configuracion_cuentas_factura_venta m join cuentas_contables c on c.id=m.cuenta_contable_id
            """, rs -> {
                Map<String, Cuenta> result = new HashMap<>();
                while (rs.next()) result.put(rs.getString("concepto"), new Cuenta(rs.getLong("id"), rs.getString("tipo"),
                    rs.getString("naturaleza"), rs.getBoolean("activa"), rs.getBoolean("acepta_movimientos")));
                return result;
            });
        if (accounts.size() != 3) throw new IntegracionFacturaVentaException(HttpStatus.CONFLICT,
            "CONFIGURACION_INCOMPLETA", "Configure las tres cuentas de integración antes de reintentar");
        valida(accounts, "CLIENTES_POR_COBRAR", "ACTIVO", "DEUDORA");
        valida(accounts, "VENTAS", "INGRESO", "ACREEDORA");
        valida(accounts, "IMPUESTOS_TRASLADADOS", "PASIVO", "ACREEDORA");
        if (invoice.total().signum() <= 0) throw new IntegracionFacturaVentaException(HttpStatus.CONFLICT,
            "FACTURA_SIN_IMPORTE", "La factura no tiene un importe positivo contabilizable");

        var lines = new ArrayList<PartidaAsientoCreateRequest>();
        lines.add(new PartidaAsientoCreateRequest(accounts.get("CLIENTES_POR_COBRAR").id(), invoice.total(), BigDecimal.ZERO,
            "Saldo de factura " + invoice.folio()));
        if (invoice.subtotal().signum() > 0) lines.add(new PartidaAsientoCreateRequest(accounts.get("VENTAS").id(),
            BigDecimal.ZERO, invoice.subtotal(), "Venta neta " + invoice.folio()));
        if (invoice.impuestos().signum() > 0) lines.add(new PartidaAsientoCreateRequest(accounts.get("IMPUESTOS_TRASLADADOS").id(),
            BigDecimal.ZERO, invoice.impuestos(), "Impuestos trasladados " + invoice.folio()));

        var creation = entries.crearDesdeOrigen(new AsientoContableCreateRequest(periods.getFirst(), date,
            "Factura de venta " + invoice.folio(), "VENTAS-FACTURA-" + facturaId, lines),
            "VENTAS", "FACTURA_VENTA", facturaId);
        var posted = entries.contabilizar(creation.asiento().id());
        Instant now = Instant.now();
        long actor = users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "Autenticación requerida")).usuarioId();
        int changed = db.update("""
            update integracion_factura_venta_contabilidad
            set estado='CONTABILIZADA',asiento_contable_id=?,fecha_contable=?,intentos=intentos+1,
                ultima_fecha_intentada=?,ultimo_codigo_error=null,ultimo_intento_por_usuario_id=?,contabilizada_en=?
            where factura_venta_id=? and estado='PENDIENTE'
            """, posted.id(), date, date, actor, Timestamp.from(now), facturaId);
        if (changed != 1) throw new IntegracionFacturaVentaException(HttpStatus.CONFLICT,
            "INTEGRACION_NO_PENDIENTE", "La integración de la factura ya no está pendiente");
        Long receivableId = db.queryForObject("""
            insert into cuentas_por_cobrar(factura_venta_id,cliente_id,monto_original,fecha_emision,fecha_vencimiento)
            select id,cliente_id,total,fecha_emision,fecha_vencimiento from facturas_venta where id=?
            returning id
            """, Long.class, facturaId);
        audit.change(AuditAction.ACCOUNTING_RECEIVABLE_CREATED, receivableId,
            Set.of(AuditField.ESTADO, AuditField.IMPORTE_ORIGINAL, AuditField.FECHA_VENCIMIENTO),
            null, AuditState.ABIERTO);
        audit.change(AuditAction.ACCOUNTING_SALES_INVOICE_POSTED, facturaId,
            Set.of(AuditField.ESTADO, AuditField.FECHA_CONTABLE), AuditState.PENDIENTE, AuditState.CONTABILIZADO);
        return status(facturaId);
    }

    private IntegracionFacturaVentaResponse status(Long id) {
        return db.query("""
            select i.factura_venta_id,f.folio,i.estado,i.asiento_contable_id,i.fecha_contable,i.intentos,
                   i.ultima_fecha_intentada,i.ultimo_codigo_error,i.emitida_en,i.contabilizada_en
            from integracion_factura_venta_contabilidad i join facturas_venta f on f.id=i.factura_venta_id
            where i.factura_venta_id=?
            """, (rs, row) -> new IntegracionFacturaVentaResponse(rs.getLong(1), rs.getString(2), rs.getString(3),
                (Long) rs.getObject(4), rs.getObject(5, LocalDate.class), rs.getInt(6), rs.getObject(7, LocalDate.class),
                rs.getString(8), rs.getTimestamp(9).toInstant(), rs.getTimestamp(10) == null ? null : rs.getTimestamp(10).toInstant()), id)
            .stream().findFirst().orElseThrow();
    }

    private static void valida(Map<String, Cuenta> accounts, String concept, String type, String nature) {
        Cuenta account = accounts.get(concept);
        if (account == null || !account.tipo().equals(type) || !account.naturaleza().equals(nature)
                || !account.activa() || !account.aceptaMovimientos())
            throw new IntegracionFacturaVentaException(HttpStatus.CONFLICT, "CUENTAS_INVALIDAS",
                "Una cuenta configurada ya no cumple el tipo o estado requerido");
    }

    private record Factura(String estado, Long asientoId, String folio, LocalDate fechaEmision,
            BigDecimal subtotal, BigDecimal impuestos, BigDecimal total) {}
    private record Cuenta(Long id, String tipo, String naturaleza, boolean activa, boolean aceptaMovimientos) {}
}
