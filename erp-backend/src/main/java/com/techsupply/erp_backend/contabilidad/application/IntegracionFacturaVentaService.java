package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Durable handoff and user-authorized retry for issued sales invoices. */
@Service
public class IntegracionFacturaVentaService {
    private final JdbcTemplate db;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    private final ProcesadorFacturaVentaContable processor;
    private final RegistrarFalloIntegracionFacturaVenta failureRecorder;

    public IntegracionFacturaVentaService(JdbcTemplate db, AuthenticatedUserProvider users, AuditTrail audit,
            ProcesadorFacturaVentaContable processor, RegistrarFalloIntegracionFacturaVenta failureRecorder) {
        this.db = db; this.users = users; this.audit = audit;
        this.processor = processor; this.failureRecorder = failureRecorder;
    }

    /** Called by invoice emission inside the same transaction, so no issued invoice loses its durable signal. */
    @Transactional
    public void registrarEmision(Long facturaVentaId) {
        db.update("insert into integracion_factura_venta_contabilidad(factura_venta_id,emitida_en) values (?,?)",
            facturaVentaId, Timestamp.from(Instant.now()));
        audit.change(AuditAction.ACCOUNTING_SALES_INVOICE_QUEUED, facturaVentaId,
            Set.of(AuditField.ESTADO), null, AuditState.PENDIENTE);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTABILIDAD_INTEGRACIONES_LEER')")
    public PageResponse<IntegracionFacturaVentaResponse> pendientes(int page, int size) {
        var paging = PageRequests.of(page, size, "emitidaEn", "asc", Map.of(
            "emitidaEn", "i.emitida_en", "facturaVentaId", "i.factura_venta_id"));
        long total = db.queryForObject("select count(*) from integracion_factura_venta_contabilidad where estado='PENDIENTE'", Long.class);
        var content = db.query(SELECT_STATUS + " where i.estado='PENDIENTE' order by i.emitida_en asc,i.factura_venta_id asc limit ? offset ?",
            STATUS_MAPPER, paging.getPageSize(), paging.getOffset());
        return new PageResponse<>(content, page, size, total, (int) Math.ceil((double) total / size));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CONTABILIDAD_INTEGRACIONES_LEER')")
    public IntegracionFacturaVentaResponse obtener(Long facturaVentaId) {
        return status(facturaVentaId);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTABILIDAD_INTEGRACIONES_ADMIN')")
    public ConfiguracionFacturaVentaResponse configuracion() {
        return cargarConfiguracion();
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTABILIDAD_INTEGRACIONES_ADMIN')")
    public ConfiguracionFacturaVentaResponse configurar(ConfiguracionFacturaVentaRequest request) {
        var ids = List.of(request.clientesPorCobrarCuentaId(), request.ventasCuentaId(), request.impuestosTrasladadosCuentaId());
        if (new HashSet<>(ids).size() != 3)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cada concepto requiere una cuenta distinta");
        var rows = db.query("select id,codigo,tipo,naturaleza,activa,acepta_movimientos from cuentas_contables where id in (?,?,?)",
            (rs, row) -> new CuentaConfig(rs.getLong("id"), rs.getString("codigo"), rs.getString("tipo"),
                rs.getString("naturaleza"), rs.getBoolean("activa"), rs.getBoolean("acepta_movimientos")), ids.toArray());
        if (rows.size() != 3) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No se encontraron todas las cuentas indicadas");
        validarCuenta(rows, ids.get(0), "ACTIVO", "DEUDORA");
        validarCuenta(rows, ids.get(1), "INGRESO", "ACREEDORA");
        validarCuenta(rows, ids.get(2), "PASIVO", "ACREEDORA");
        long actor = actorId();
        db.update("delete from configuracion_cuentas_factura_venta");
        guardarCuenta("CLIENTES_POR_COBRAR", ids.get(0), actor);
        guardarCuenta("VENTAS", ids.get(1), actor);
        guardarCuenta("IMPUESTOS_TRASLADADOS", ids.get(2), actor);
        audit.change(AuditAction.ACCOUNTING_SALES_INVOICE_CONFIGURED, 1L,
            Set.of(AuditField.CUENTAS_CONTABLES), null, null);
        return cargarConfiguracion();
    }

    @PreAuthorize("hasAuthority('CONTABILIDAD_INTEGRACIONES_REINTENTAR') and " +
        "hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR') and hasAuthority('CONTABILIDAD_ASIENTOS_CONTABILIZAR')")
    public IntegracionFacturaVentaResponse reintentar(Long facturaVentaId, ReintentoFacturaVentaRequest request) {
        LocalDate date = request == null ? null : request.fechaContable();
        try {
            return processor.procesar(facturaVentaId, date);
        } catch (IntegracionFacturaVentaException ex) {
            failureRecorder.registrar(facturaVentaId, date, actorId(), ex.codigo());
            throw ex;
        } catch (ResponseStatusException ex) {
            failureRecorder.registrar(facturaVentaId, date, actorId(), "ERROR_CONTABLE");
            throw ex;
        } catch (RuntimeException ex) {
            failureRecorder.registrar(facturaVentaId, date, actorId(), "ERROR_CONTABLE");
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "No fue posible contabilizar la factura; permanece pendiente para reintento");
        }
    }

    private IntegracionFacturaVentaResponse status(Long id) {
        if (id == null || id < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Identificador inválido");
        return db.query(SELECT_STATUS + " where i.factura_venta_id=?", STATUS_MAPPER, id).stream().findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Integración de factura no encontrada"));
    }

    private ConfiguracionFacturaVentaResponse cargarConfiguracion() {
        var rows = db.query("select m.concepto,c.id,c.codigo,c.nombre from configuracion_cuentas_factura_venta m " +
            "join cuentas_contables c on c.id=m.cuenta_contable_id order by m.concepto",
            (rs, row) -> new ConfiguracionFacturaVentaResponse.CuentaIntegracion(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4)));
        return new ConfiguracionFacturaVentaResponse(rows.size() == 3, rows);
    }

    private void guardarCuenta(String concepto, Long cuenta, Long actor) {
        db.update("insert into configuracion_cuentas_factura_venta(concepto,cuenta_contable_id,actualizado_por_usuario_id,actualizado_en) values (?,?,?,?)",
            concepto, cuenta, actor, Timestamp.from(Instant.now()));
    }

    private static void validarCuenta(List<CuentaConfig> rows, Long id, String type, String nature) {
        var account = rows.stream().filter(row -> row.id().equals(id)).findFirst().orElseThrow();
        if (!account.tipo().equals(type) || !account.naturaleza().equals(nature) || !account.activa() || !account.aceptaMovimientos())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La cuenta no cumple el tipo, naturaleza o estado requerido");
    }

    private Long actorId() {
        return users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
            "Autenticación requerida")).usuarioId();
    }

    private static final String SELECT_STATUS = """
        select i.factura_venta_id,f.folio,i.estado,i.asiento_contable_id,i.fecha_contable,i.intentos,
               i.ultima_fecha_intentada,i.ultimo_codigo_error,i.emitida_en,i.contabilizada_en
        from integracion_factura_venta_contabilidad i join facturas_venta f on f.id=i.factura_venta_id
        """;
    private static final org.springframework.jdbc.core.RowMapper<IntegracionFacturaVentaResponse> STATUS_MAPPER = (rs, row) ->
        new IntegracionFacturaVentaResponse(rs.getLong("factura_venta_id"), rs.getString("folio"), rs.getString("estado"),
            (Long) rs.getObject("asiento_contable_id"), rs.getObject("fecha_contable", LocalDate.class), rs.getInt("intentos"),
            rs.getObject("ultima_fecha_intentada", LocalDate.class), rs.getString("ultimo_codigo_error"),
            rs.getTimestamp("emitida_en").toInstant(), rs.getTimestamp("contabilizada_en") == null ? null : rs.getTimestamp("contabilizada_en").toInstant());
    private record CuentaConfig(Long id, String codigo, String tipo, String naturaleza, boolean activa, boolean aceptaMovimientos) {}
}
