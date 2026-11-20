package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.audit.application.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records only safe business codes after the accounting transaction has rolled back. */
@Service
public class RegistrarFalloIntegracionFacturaVenta {
    private final JdbcTemplate db;
    private final AuditTrail audit;

    public RegistrarFalloIntegracionFacturaVenta(JdbcTemplate db, AuditTrail audit) { this.db = db; this.audit = audit; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registrar(Long facturaId, LocalDate attemptedDate, Long actorId, String code) {
        int changed = db.update("""
            update integracion_factura_venta_contabilidad i
            set intentos=intentos+1,
                ultima_fecha_intentada=coalesce(?,(select f.fecha_emision from facturas_venta f where f.id=i.factura_venta_id)),
                ultimo_codigo_error=?,ultimo_intento_por_usuario_id=?
            where factura_venta_id=? and estado='PENDIENTE'
            """, attemptedDate, code, actorId, facturaId);
        if (changed == 1) audit.change(AuditAction.ACCOUNTING_SALES_INVOICE_RETRY_FAILED, facturaId,
            java.util.Set.of(AuditField.ESTADO, AuditField.FECHA_CONTABLE), AuditState.PENDIENTE, AuditState.PENDIENTE);
    }
}
