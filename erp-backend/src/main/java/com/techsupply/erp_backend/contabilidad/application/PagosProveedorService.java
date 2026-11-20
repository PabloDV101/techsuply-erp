package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.contabilidad.application.dto.PagoProveedorRequests.*;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PagosProveedorService {
    private final JdbcTemplate db; private final AsientoContableService entries;
    private final AuthenticatedUserProvider users; private final AuditTrail audit;
    public PagosProveedorService(JdbcTemplate db, AsientoContableService entries, AuthenticatedUserProvider users, AuditTrail audit) {
        this.db=db; this.entries=entries; this.users=users; this.audit=audit;
    }
    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_PAGOS_PROVEEDOR_REGISTRAR')")
    public PagoProveedorResponse registrar(Registrar r) {
        String key=r.claveIdempotencia().trim(); db.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(?,812200))",Integer.class,key);
        var old=db.queryForList("select * from pagos_proveedor where clave_idempotencia=?",key);
        Instant date=r.fechaPago().truncatedTo(ChronoUnit.MICROS); String ref=r.referencia()==null||r.referencia().isBlank()?null:r.referencia().trim();
        if(!old.isEmpty()) { var p=old.getFirst();
            if (((Number)p.get("proveedor_id")).longValue()!=r.proveedorId() || ((BigDecimal)p.get("monto")).compareTo(r.monto())!=0 || !p.get("medio").equals(r.medio()) || !Objects.equals(p.get("referencia"),ref) || !((java.sql.Timestamp)p.get("fecha_pago")).toInstant().equals(date)) throw conflict("Clave utilizada con otros datos");
            return read(((Number)p.get("id")).longValue()); }
        long seq=db.queryForObject("select nextval('pago_proveedor_folio_seq')",Long.class);
        Long id=db.queryForObject("insert into pagos_proveedor(folio,proveedor_id,fecha_pago,monto,medio,referencia,clave_idempotencia,estado,registrado_por_usuario_id) values (?,?,?,?,?,?,?,'REGISTRADO',?) returning id",Long.class,"PP-"+String.format(Locale.ROOT,"%010d",seq),r.proveedorId(),java.sql.Timestamp.from(date),r.monto(),r.medio(),ref,key,actor());
        audit.change(AuditAction.ACCOUNTING_SUPPLIER_PAYMENT_CREATED,id); return read(id);
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CONTABILIDAD_PAGOS_PROVEEDOR_LEER')")
    public PagoProveedorResponse obtener(Long id) { return read(id); }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CONTABILIDAD_PAGOS_PROVEEDOR_CONFIGURAR')")
    public Configurar configuracion() {
        return db.query("select cuenta_fondos_id from configuracion_cuenta_pago_proveedor where id=1",
            (rs,n)->new Configurar(rs.getLong(1))).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Configuración de fondos no encontrada"));
    }
    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_PAGOS_PROVEEDOR_CONFIGURAR')")
    public void configurar(Configurar r) {
        requireAccount(r.cuentaFondosId(),"ACTIVO","DEUDORA");
        db.update("insert into configuracion_cuenta_pago_proveedor(id,cuenta_fondos_id,actualizado_por_usuario_id) values (1,?,?) on conflict(id) do update set cuenta_fondos_id=excluded.cuenta_fondos_id,actualizado_por_usuario_id=excluded.actualizado_por_usuario_id,actualizado_en=now()",r.cuentaFondosId(),actor());
        audit.change(AuditAction.ACCOUNTING_SUPPLIER_PAYMENT_CONFIGURED,1L);
    }
    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_PAGOS_PROVEEDOR_CONFIRMAR') and hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR') and hasAuthority('CONTABILIDAD_ASIENTOS_CONTABILIZAR')")
    public PagoProveedorResponse confirmar(Long id, Confirmar r) {
        var rows=db.queryForList("select * from pagos_proveedor where id=? for update",id); if(rows.isEmpty()) throw missing();
        var p=rows.getFirst();
        if("CONFIRMADO".equals(p.get("estado"))) {
            var existing=db.queryForList("select cuenta_por_pagar_id,monto_aplicado from aplicaciones_pago_proveedor where pago_proveedor_id=? order by cuenta_por_pagar_id",id);
            var requested=r.aplicaciones().stream().sorted(Comparator.comparing(Aplicacion::cuentaPorPagarId)).toList();
            if(existing.size()!=requested.size()) throw conflict("Pago confirmado con otras aplicaciones");
            for(int i=0;i<existing.size();i++) if(((Number)existing.get(i).get("cuenta_por_pagar_id")).longValue()!=requested.get(i).cuentaPorPagarId() || ((BigDecimal)existing.get(i).get("monto_aplicado")).compareTo(requested.get(i).monto())!=0) throw conflict("Pago confirmado con otras aplicaciones");
            return read(id);
        }
        var apps=r.aplicaciones().stream().sorted(Comparator.comparing(Aplicacion::cuentaPorPagarId)).toList();
        if(apps.stream().map(Aplicacion::cuentaPorPagarId).distinct().count()!=apps.size()) throw conflict("Cuenta repetida");
        BigDecimal total=apps.stream().map(Aplicacion::monto).reduce(BigDecimal.ZERO,BigDecimal::add);
        if(total.compareTo((BigDecimal)p.get("monto"))!=0) throw conflict("Las aplicaciones deben sumar el pago");
        long actor=actor();
        for(var a:apps) {
            String key="PAGO-"+id+"-"+a.cuentaPorPagarId();
            db.update("insert into aplicaciones_pago_proveedor(pago_proveedor_id,cuenta_por_pagar_id,monto_aplicado,aplicado_por_usuario_id,clave_idempotencia,huella_solicitud) values (?,?,?,?,?,?)",id,a.cuentaPorPagarId(),a.monto(),actor,key,hash(key+"|"+a.monto().stripTrailingZeros().toPlainString()));
        }
        LocalDate date=LocalDate.now(ZoneId.of("America/Mexico_City"));
        long period=one("select id from periodos_contables where estado='ABIERTO' and fecha_inicio<=? and fecha_fin>=?",date,date);
        long funds=one("select cuenta_fondos_id from configuracion_cuenta_pago_proveedor where id=1");
        requireAccount(funds,"ACTIVO","DEUDORA");
        Map<Long,BigDecimal> cuentas=new TreeMap<>();
        for (var a:apps) {
            long payable=one("select p.cuenta_contable_id from cuentas_por_pagar c join partidas_asiento p on p.asiento_id=c.asiento_contable_id where c.id=? and p.haber=c.monto_original and p.debe=0",a.cuentaPorPagarId());
            requireAccount(payable,"PASIVO","ACREEDORA");
            cuentas.merge(payable,a.monto(),BigDecimal::add);
        }
        var lines=new ArrayList<PartidaAsientoCreateRequest>();
        cuentas.forEach((account,amount)->lines.add(new PartidaAsientoCreateRequest(account,amount,BigDecimal.ZERO,"Pago a proveedor")));
        lines.add(new PartidaAsientoCreateRequest(funds,BigDecimal.ZERO,total,"Salida de fondos"));
        var entry=entries.crearDesdePagoProveedor(new AsientoContableCreateRequest(period,date,"Pago a proveedor "+id,"PAGO-PROVEEDOR-"+id,lines),id);
        var posted=entries.contabilizar(entry.asiento().id());
        db.update("update pagos_proveedor set estado='CONFIRMADO',asiento_contable_id=?,fecha_contable=?,confirmado_por_usuario_id=?,confirmado_en=now() where id=?",posted.id(),date,actor,id);
        audit.change(AuditAction.ACCOUNTING_SUPPLIER_PAYMENT_CONFIRMED,id); return read(id);
    }
    private PagoProveedorResponse read(long id) {
        var applications=db.query("select cuenta_por_pagar_id,monto_aplicado from aplicaciones_pago_proveedor where pago_proveedor_id=? order by cuenta_por_pagar_id", (rs,n)->new PagoProveedorResponse.Aplicacion(rs.getLong(1),rs.getBigDecimal(2)),id);
        return db.query("select id,folio,proveedor_id,fecha_pago,monto,medio,referencia,estado,asiento_contable_id,fecha_contable from pagos_proveedor where id=?",(rs,n)->new PagoProveedorResponse(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getTimestamp(4).toInstant(),rs.getBigDecimal(5),rs.getString(6),rs.getString(7),rs.getString(8),(Long)rs.getObject(9),rs.getObject(10,LocalDate.class),applications),id).stream().findFirst().orElseThrow(PagosProveedorService::missing);
    }
    private long one(String sql,Object...args) { return db.query(sql,(rs,n)->rs.getLong(1),args).stream().findFirst().orElseThrow(()->conflict("Falta configuración o periodo abierto")); }
    private void requireAccount(long id,String type,String nature) { if(db.queryForObject("select count(*) from cuentas_contables where id=? and tipo=? and naturaleza=? and activa and acepta_movimientos",Integer.class,id,type,nature)!=1) throw conflict("Cuenta inválida"); }
    private long actor() { return users.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED)).usuarioId(); }
    private static ResponseStatusException conflict(String text) { return new ResponseStatusException(HttpStatus.CONFLICT,text); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Pago no encontrado"); }
    private static String hash(String v) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8))); } catch(Exception e) { throw new IllegalStateException(e); } }
}
