package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditField;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Posts approved inventory source facts in the same transaction as their stock movement. */
@Service
public class IntegracionInventarioContableService {
    private static final String INVENTARIO = "INVENTARIO";
    private static final String RECIBIDA = "MERCANCIA_RECIBIDA_NO_FACTURADA";
    private static final String COSTO_VENTAS = "COSTO_DE_VENTAS";
    private static final String AJUSTE_POSITIVO = "AJUSTE_INVENTARIO_POSITIVO";
    private static final String AJUSTE_NEGATIVO = "AJUSTE_INVENTARIO_NEGATIVO";
    private static final java.time.ZoneId BUSINESS_ZONE = java.time.ZoneId.of("America/Mexico_City");
    private final JdbcTemplate db;
    private final AsientoContableService entries;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;

    public IntegracionInventarioContableService(JdbcTemplate db, AsientoContableService entries,
            AuthenticatedUserProvider users, AuditTrail audit) {
        this.db = db; this.entries = entries; this.users = users; this.audit = audit;
    }

    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('CONTABILIDAD_INVENTARIO_LEER')")
    public ConfiguracionCuentasInventarioResponse configuracion() {
        var rows = db.query("select m.concepto,c.id,c.codigo,c.nombre from configuracion_cuentas_inventario m " +
                "join cuentas_contables c on c.id=m.cuenta_contable_id order by m.concepto",
            (rs, row) -> new ConfiguracionCuentasInventarioResponse.Cuenta(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getString(4)));
        return new ConfiguracionCuentasInventarioResponse(rows.size() == 5, rows);
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_INVENTARIO_CONFIGURAR')")
    public ConfiguracionCuentasInventarioResponse configurar(ConfiguracionCuentasInventarioRequest request) {
        Map<String, Long> desired = new LinkedHashMap<>();
        desired.put(INVENTARIO, request.inventarioCuentaId());
        desired.put(RECIBIDA, request.mercanciaRecibidaNoFacturadaCuentaId());
        desired.put(COSTO_VENTAS, request.costoDeVentasCuentaId());
        desired.put(AJUSTE_POSITIVO, request.ajustePositivoCuentaId());
        desired.put(AJUSTE_NEGATIVO, request.ajusteNegativoCuentaId());
        var ids = new HashSet<>(desired.values());
        var accounts = db.query("select id,tipo,naturaleza,activa,acepta_movimientos from cuentas_contables where id in (" +
                String.join(",", Collections.nCopies(ids.size(), "?")) + ")",
            (rs, row) -> new Account(rs.getLong("id"), rs.getString("tipo"), rs.getString("naturaleza"),
                rs.getBoolean("activa"), rs.getBoolean("acepta_movimientos")), ids.toArray());
        if (accounts.size() != ids.size()) throw missing("No se encontraron todas las cuentas indicadas");
        var byId = new HashMap<Long, Account>(); accounts.forEach(a -> byId.put(a.id(), a));
        requireAccount(byId, desired.get(INVENTARIO), "ACTIVO", "DEUDORA");
        requireAccount(byId, desired.get(RECIBIDA), "PASIVO", "ACREEDORA");
        requireAccount(byId, desired.get(COSTO_VENTAS), "COSTO", "DEUDORA");
        requireAccount(byId, desired.get(AJUSTE_POSITIVO), "INGRESO", "ACREEDORA");
        requireAccount(byId, desired.get(AJUSTE_NEGATIVO), "GASTO", "DEUDORA");
        if (Objects.equals(desired.get(INVENTARIO), desired.get(RECIBIDA))
                || Objects.equals(desired.get(INVENTARIO), desired.get(COSTO_VENTAS))
                || Objects.equals(desired.get(INVENTARIO), desired.get(AJUSTE_POSITIVO))
                || Objects.equals(desired.get(INVENTARIO), desired.get(AJUSTE_NEGATIVO)))
            throw bad("La cuenta de inventario debe distinguirse de sus contrapartidas");
        long actor = actorId();
        db.update("delete from configuracion_cuentas_inventario");
        desired.forEach((concept, id) -> db.update("insert into configuracion_cuentas_inventario" +
            "(concepto,cuenta_contable_id,actualizado_por_usuario_id,actualizado_en) values (?,?,?,?)",
            concept, id, actor, java.sql.Timestamp.from(java.time.Instant.now())));
        audit.change(AuditAction.ACCOUNTING_INVENTORY_CONFIGURED, 1L, Set.of(AuditField.CUENTAS_CONTABLES), null, null);
        return configuracion();
    }

    @Transactional
    public void contabilizarRecepcion(Long recepcionId, LocalDate fecha) {
        var values = amountPerMovement("select m.cantidad*m.costo_unitario from movimientos_inventario m " +
            "join detalles_recepcion d on d.id=m.detalle_recepcion_id where d.recepcion_id=?", recepcionId);
        postIfValued(values, INVENTARIO, RECIBIDA, "COMPRAS", "RECEPCION_COMPRA", recepcionId,
            "Recepción de compra " + recepcionId, fecha, AuditAction.ACCOUNTING_INVENTORY_RECEIPT_POSTED);
    }

    @Transactional
    public void contabilizarDespacho(Long despachoId, LocalDate fecha) {
        var values = amountPerMovement("select m.cantidad*m.costo_unitario from movimientos_inventario m " +
            "join detalles_despacho d on d.id=m.detalle_despacho_id where d.despacho_id=?", despachoId);
        postIfValued(values, INVENTARIO, COSTO_VENTAS, "VENTAS", "DESPACHO_VENTA", despachoId,
            "Costo de despacho " + despachoId, fecha, AuditAction.ACCOUNTING_INVENTORY_DISPATCH_POSTED);
    }

    @Transactional
    public void contabilizarAjuste(Long ajusteId, LocalDate fecha) {
        var values = db.query("select d.tipo,m.cantidad*m.costo_unitario from movimientos_inventario m " +
                "join detalles_ajuste_inventario d on d.id=m.detalle_ajuste_id where d.ajuste_inventario_id=? order by d.id",
            (rs, row) -> new ValuedMovement(rs.getString(1), rs.getBigDecimal(2)), ajusteId);
        Map<String, BigDecimal> positive = new LinkedHashMap<>(), negative = new LinkedHashMap<>();
        for (var value : values) {
            BigDecimal amount = cents(value.amount());
            var target = "POSITIVO".equals(value.type()) ? positive : negative;
            target.merge(value.type(), amount, BigDecimal::add);
        }
        var mapping = mapping();
        requireOpenPeriod(fecha);
        var lines = new ArrayList<PartidaAsientoCreateRequest>();
        addLine(lines, mapping.get(INVENTARIO), positive.getOrDefault("POSITIVO", BigDecimal.ZERO), BigDecimal.ZERO, "Aumento por ajuste de inventario");
        addLine(lines, mapping.get(AJUSTE_POSITIVO), BigDecimal.ZERO, positive.getOrDefault("POSITIVO", BigDecimal.ZERO), "Contrapartida de ajuste positivo");
        addLine(lines, mapping.get(AJUSTE_NEGATIVO), negative.getOrDefault("NEGATIVO", BigDecimal.ZERO), BigDecimal.ZERO, "Pérdida por ajuste de inventario");
        addLine(lines, mapping.get(INVENTARIO), BigDecimal.ZERO, negative.getOrDefault("NEGATIVO", BigDecimal.ZERO), "Disminución por ajuste de inventario");
        if (lines.isEmpty()) return;
        post(lines, "INVENTARIO", "AJUSTE_INVENTARIO", ajusteId, "Ajuste de inventario " + ajusteId,
            fecha, AuditAction.ACCOUNTING_INVENTORY_ADJUSTMENT_POSTED);
    }

    private void postIfValued(List<BigDecimal> amounts, String debitConcept, String creditConcept, String module,
            String type, Long sourceId, String description, LocalDate date, AuditAction action) {
        var mapping = mapping();
        requireOpenPeriod(date);
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (var amount : amounts) total = total.add(cents(amount));
        if (total.signum() == 0) return;
        var lines = List.of(
            new PartidaAsientoCreateRequest(mapping.get(debitConcept), total, BigDecimal.ZERO.setScale(2), description),
            new PartidaAsientoCreateRequest(mapping.get(creditConcept), BigDecimal.ZERO.setScale(2), total, description));
        post(lines, module, type, sourceId, description, date, action);
    }

    private void post(List<PartidaAsientoCreateRequest> lines, String module, String type, Long id,
            String description, LocalDate date, AuditAction action) {
        if (lines.size() < 2) return;
        Long periodId = db.query("select id from periodos_contables where estado='ABIERTO' and fecha_inicio<=? and fecha_fin>=?",
            (rs, row) -> rs.getLong(1), date, date).stream().findFirst()
            .orElseThrow(() -> conflict("La fecha del movimiento requiere un periodo abierto"));
        String key = "INVENTARIO-" + type + "-" + id;
        var entry = entries.contabilizarOrigenInventario(new AsientoContableCreateRequest(periodId, date,
            description, key, lines), module, type, id);
        audit.change(action, id, Set.of(AuditField.ESTADO, AuditField.FECHA_CONTABLE), null,
            com.techsupply.erp_backend.audit.application.AuditState.CONTABILIZADO);
    }

    private List<BigDecimal> amountPerMovement(String sql, Long sourceId) {
        return db.query(sql, (rs, row) -> rs.getBigDecimal(1), sourceId);
    }

    private Map<String, Long> mapping() {
        Map<String, Long> accounts = new HashMap<>();
        var rows = db.query("select m.concepto,c.id,c.tipo,c.naturaleza,c.activa,c.acepta_movimientos " +
                "from configuracion_cuentas_inventario m join cuentas_contables c on c.id=m.cuenta_contable_id",
            (rs, row) -> Map.entry(rs.getString("concepto"), rs.getLong("id")));
        rows.forEach(row -> accounts.put(row.getKey(), row.getValue()));
        if (accounts.size() != 5) throw conflict("Configure las cinco cuentas contables de Inventario");
        for (var mapping : expectedTypes().entrySet()) {
            var account = db.query("select tipo,naturaleza,activa,acepta_movimientos from cuentas_contables where id=?",
                (rs, row) -> new Account(accounts.get(mapping.getKey()), rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getBoolean(4)),
                accounts.get(mapping.getKey())).stream().findFirst().orElseThrow(() -> conflict("Una cuenta configurada ya no existe"));
            requireAccount(Map.of(account.id(), account), account.id(), mapping.getValue().tipo(), mapping.getValue().naturaleza());
        }
        return accounts;
    }

    private static Map<String, Expected> expectedTypes() {
        return Map.of(INVENTARIO, new Expected("ACTIVO", "DEUDORA"), RECIBIDA, new Expected("PASIVO", "ACREEDORA"),
            COSTO_VENTAS, new Expected("COSTO", "DEUDORA"), AJUSTE_POSITIVO, new Expected("INGRESO", "ACREEDORA"),
            AJUSTE_NEGATIVO, new Expected("GASTO", "DEUDORA"));
    }
    private static void requireAccount(Map<Long, Account> accounts, Long id, String type, String nature) {
        var account = accounts.get(id);
        if (account == null) throw missing("Cuenta contable no encontrada");
        if (!type.equals(account.type()) || !nature.equals(account.nature()) || !account.active() || !account.accepts())
            throw conflict("Una cuenta configurada no tiene tipo, naturaleza o estado requerido");
    }
    private void requireOpenPeriod(LocalDate date) {
        if (date == null || db.query("select id from periodos_contables where estado='ABIERTO' and fecha_inicio<=? and fecha_fin>=?",
                (rs, row) -> rs.getLong(1), date, date).isEmpty())
            throw conflict("La fecha del movimiento requiere un periodo abierto");
    }
    private static BigDecimal cents(BigDecimal amount) {
        try { return amount.setScale(2, RoundingMode.UNNECESSARY); }
        catch (ArithmeticException ex) { throw conflict("El costo del movimiento excede la precisión monetaria contable"); }
    }
    private static void addLine(List<PartidaAsientoCreateRequest> lines, Long account, BigDecimal debit, BigDecimal credit, String description) {
        if (debit.signum() > 0) lines.add(new PartidaAsientoCreateRequest(account, debit, BigDecimal.ZERO.setScale(2), description));
        if (credit.signum() > 0) lines.add(new PartidaAsientoCreateRequest(account, BigDecimal.ZERO.setScale(2), credit, description));
    }
    private long actorId() { return users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida")).usuarioId(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND, message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT, message); }
    private record Account(Long id, String type, String nature, boolean active, boolean accepts) {}
    private record Expected(String tipo, String naturaleza) {}
    private record ValuedMovement(String type, BigDecimal amount) {}
}
