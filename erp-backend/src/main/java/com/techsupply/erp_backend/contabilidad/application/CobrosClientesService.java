package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Explicit customer receipts and same-customer applications with a balanced, atomic ledger handoff. */
@Service
public class CobrosClientesService {
    private final JdbcTemplate db;
    private final AsientoContableService entries;
    private final AuditTrail audit;
    private final AuthenticatedUserProvider users;

    public CobrosClientesService(JdbcTemplate db, AsientoContableService entries, AuditTrail audit,
            AuthenticatedUserProvider users) { this.db=db; this.entries=entries; this.audit=audit; this.users=users; }

    @Transactional
    @PreAuthorize("hasAuthority('CONTABILIDAD_COBROS_REGISTRAR')")
    public CobroClienteResponse registrar(CobroClienteCreateRequest request) {
        String key=request.claveIdempotencia().trim(); Instant receiptTime=request.fecha().truncatedTo(ChronoUnit.MICROS); lockKey(key);
        var previous=db.query("select id,cliente_id,fecha,monto,medio,referencia,clave_idempotencia from cobros_clientes where clave_idempotencia=?",
            (rs,n)->new Object[]{rs.getLong(1),rs.getLong(2),rs.getTimestamp(3).toInstant(),rs.getBigDecimal(4),rs.getString(5),rs.getString(6),rs.getString(7)},key);
        if (!previous.isEmpty()) {
            Object[] p=previous.getFirst();
            if (((Number)p[1]).longValue()!=request.clienteId() || !p[2].equals(receiptTime)
                    || ((BigDecimal)p[3]).compareTo(request.monto())!=0 || !p[4].equals(request.medio())
                    || !Objects.equals(p[5],trim(request.referencia()))) throw conflict("La clave de idempotencia ya se usó con otros datos");
            return obtener(((Number)p[0]).longValue());
        }
        long actor=actor(); long seq=db.queryForObject("select nextval('cobro_cliente_folio_seq')",Long.class);
        long id=db.queryForObject("insert into cobros_clientes(folio,cliente_id,fecha,monto,medio,referencia,registrado_por_usuario_id,clave_idempotencia,estado) values (?,?,?,?,?,?,?,?,'REGISTRADO') returning id",
            Long.class,"CB-"+String.format(Locale.ROOT,"%010d",seq),request.clienteId(),java.sql.Timestamp.from(receiptTime),request.monto(),request.medio(),trim(request.referencia()),actor,key);
        audit.change(AuditAction.ACCOUNTING_CUSTOMER_RECEIPT_CREATED,id,Set.of(AuditField.ESTADO),null,AuditState.PENDIENTE);
        return obtener(id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTABILIDAD_COBROS_CONFIRMAR') and hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR') and hasAuthority('CONTABILIDAD_ASIENTOS_CONTABILIZAR')")
    public CobroClienteResponse confirmar(Long id, ConfirmarCobroRequest request) {
        lockBusiness(); var receipt=receiptForUpdate(id);
        if ("CONFIRMADO".equals(receipt.estado())) {
            if (!request.fechaContable().equals(receipt.fechaContable())) throw conflict("El cobro ya fue confirmado con otra fecha contable");
            return obtener(id);
        }
        long period=periodFor(request.fechaContable()); var config=config(receipt.medio());
        long receivable=invoiceReceivableAccount();
        requireAccount(config.fundsId(),"ACTIVO","DEUDORA"); requireAccount(config.advanceId(),"PASIVO","ACREEDORA");
        if (config.fundsId()==config.advanceId() || config.fundsId()==receivable || config.advanceId()==receivable)
            throw conflict("Las cuentas de fondos, anticipos y CxC deben ser distintas");
        var lines=List.of(new PartidaAsientoCreateRequest(config.fundsId(),receipt.monto(),BigDecimal.ZERO,"Fondos recibidos " + receipt.folio()),
            new PartidaAsientoCreateRequest(config.advanceId(),BigDecimal.ZERO,receipt.monto(),"Anticipo de cliente " + receipt.folio()));
        var created=entries.crearDesdeOrigen(new AsientoContableCreateRequest(period,request.fechaContable(),
            "Confirmación de cobro " + receipt.folio(),"COBRO-CONFIRMAR-"+id,lines),"CONTABILIDAD","COBRO_CLIENTE",id);
        var posted=entries.contabilizar(created.asiento().id()); long actor=actor(); Instant now=Instant.now();
        int changed=db.update("update cobros_clientes set estado='CONFIRMADO',asiento_contable_id=?,fecha_contable=?,confirmado_por_usuario_id=?,confirmado_en=? where id=? and estado='REGISTRADO'",
            posted.id(),request.fechaContable(),actor,java.sql.Timestamp.from(now),id);
        if(changed!=1) throw conflict("El cobro cambió durante la confirmación");
        audit.change(AuditAction.ACCOUNTING_CUSTOMER_RECEIPT_CONFIRMED,id,Set.of(AuditField.ESTADO,AuditField.FECHA_CONTABLE),AuditState.PENDIENTE,AuditState.CONFIRMADO);
        return obtener(id);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CONTABILIDAD_COBROS_REGISTRAR') and hasAuthority('CONTABILIDAD_ASIENTOS_REGISTRAR') and hasAuthority('CONTABILIDAD_ASIENTOS_CONTABILIZAR')")
    public CobroClienteResponse aplicar(Long id, AplicarCobroRequest request) {
        String key=request.claveIdempotencia().trim(); lockKey(key); lockBusiness();
        String hash=hash(id+"|"+request.cuentaPorCobrarId()+"|"+request.monto().stripTrailingZeros().toPlainString()+"|"+request.fechaContable());
        var duplicate=db.query("select id,huella_solicitud from aplicaciones_cobro where clave_idempotencia=?",
            (rs,n)->new Object[]{rs.getLong(1),rs.getString(2)},key);
        if(!duplicate.isEmpty()) {
            if(!hash.equals(((String)duplicate.getFirst()[1]).trim())) throw conflict("La clave de idempotencia ya se usó con otra aplicación");
            return obtener(id);
        }
        var receipt=receiptForUpdate(id);
        if(!"CONFIRMADO".equals(receipt.estado())) throw conflict("Solo se aplican cobros confirmados");
        long receivableId=request.cuentaPorCobrarId();
        var cxc=db.query("select cliente_id,monto_original-(select coalesce(sum(a.monto_aplicado),0) from aplicaciones_cobro a where a.cuenta_por_cobrar_id=c.id) saldo from cuentas_por_cobrar c where id=? for update",
            (rs,n)->new Object[]{rs.getLong(1),rs.getBigDecimal(2)},receivableId).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cuenta por cobrar no encontrada"));
        if(((Number)cxc[0]).longValue()!=receipt.clienteId()) throw conflict("El cobro y la cuenta por cobrar deben pertenecer al mismo cliente");
        BigDecimal saldo=(BigDecimal)cxc[1], disponible=disponible(id,receipt.monto());
        if(request.monto().compareTo(saldo)>0 || request.monto().compareTo(disponible)>0)
            throw conflict("La aplicación supera el saldo de la cuenta o el disponible del cobro");
        long period=periodFor(request.fechaContable()); long actor=actor();
        long applicationId=db.queryForObject("insert into aplicaciones_cobro(cobro_id,cuenta_por_cobrar_id,monto_aplicado,aplicado_por_usuario_id,clave_idempotencia,huella_solicitud) values (?,?,?,?,?,?) returning id",
            Long.class,id,receivableId,request.monto(),actor,key,hash);
        Config config=config(receipt.medio()); long receivable=invoiceReceivableAccount();
        requireAccount(config.advanceId(),"PASIVO","ACREEDORA"); requireAccount(receivable,"ACTIVO","DEUDORA");
        var lines=List.of(new PartidaAsientoCreateRequest(config.advanceId(),request.monto(),BigDecimal.ZERO,"Aplicación de anticipo " + receipt.folio()),
            new PartidaAsientoCreateRequest(receivable,BigDecimal.ZERO,request.monto(),"Aplicación a cuenta por cobrar " + receivableId));
        var created=entries.crearDesdeOrigen(new AsientoContableCreateRequest(period,request.fechaContable(),
            "Aplicación de cobro " + receipt.folio(),"COBRO-APLICACION-"+key,lines),"CONTABILIDAD","APLICACION_COBRO",applicationId);
        var posted=entries.contabilizar(created.asiento().id());
        db.update("update aplicaciones_cobro set asiento_contable_id=? where id=?",posted.id(),applicationId);
        audit.change(AuditAction.ACCOUNTING_CUSTOMER_RECEIPT_APPLIED,applicationId,Set.of(AuditField.IMPORTE_ORIGINAL,AuditField.ESTADO),null,AuditState.APLICADO);
        return obtener(id);
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAuthority('CONTABILIDAD_COBROS_LEER')")
    public CobroClienteResponse obtener(Long id) {
        return db.query(SELECT+" where c.id=? group by c.id,c.folio,c.cliente_id,c.fecha,c.monto,c.medio,c.referencia,c.estado,c.asiento_contable_id,c.fecha_contable",(rs,n)->map(rs),id).stream().findFirst()
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cobro no encontrado"));
    }

    @Transactional(readOnly=true)
    @PreAuthorize("hasAuthority('CONTABILIDAD_COBROS_CONFIGURAR')")
    public ConfiguracionCobroResponse configuracion(){ return new ConfiguracionCobroResponse(db.query("select m.medio,f.id,f.codigo,a.id,a.codigo from configuracion_cuentas_cobro m join cuentas_contables f on f.id=m.cuenta_fondos_id join cuentas_contables a on a.id=m.cuenta_anticipos_id order by m.medio",
        (rs,n)->new ConfiguracionCobroResponse.Medio(rs.getString(1),rs.getLong(2),rs.getString(3),rs.getLong(4),rs.getString(5)))); }

    @Transactional
    @PreAuthorize("hasAuthority('CONTABILIDAD_COBROS_CONFIGURAR')")
    public ConfiguracionCobroResponse configurar(ConfiguracionCobroRequest request){
        if(request.cuentaFondosId().equals(request.cuentaAnticiposId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Las cuentas deben ser distintas");
        requireAccount(request.cuentaFondosId(),"ACTIVO","DEUDORA"); requireAccount(request.cuentaAnticiposId(),"PASIVO","ACREEDORA");
        if(request.cuentaFondosId()==invoiceReceivableAccount() || request.cuentaAnticiposId()==invoiceReceivableAccount())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Fondos y anticipos deben ser distintos de CxC");
        long actor=actor(); db.update("insert into configuracion_cuentas_cobro(medio,cuenta_fondos_id,cuenta_anticipos_id,actualizado_por_usuario_id,actualizado_en) values (?,?,?,?,?) on conflict(medio) do update set cuenta_fondos_id=excluded.cuenta_fondos_id,cuenta_anticipos_id=excluded.cuenta_anticipos_id,actualizado_por_usuario_id=excluded.actualizado_por_usuario_id,actualizado_en=excluded.actualizado_en",
            request.medio(),request.cuentaFondosId(),request.cuentaAnticiposId(),actor,java.sql.Timestamp.from(Instant.now()));
        audit.change(AuditAction.ACCOUNTING_CUSTOMER_RECEIPT_CONFIGURED,1L,Set.of(AuditField.CUENTAS_CONTABLES),null,null); return configuracion();
    }

    private static final String SELECT="""
        select c.id,c.folio,c.cliente_id,c.fecha,c.monto,c.medio,c.referencia,c.estado,
          coalesce(sum(a.monto_aplicado),0) aplicado,c.asiento_contable_id,c.fecha_contable
        from cobros_clientes c left join aplicaciones_cobro a on a.cobro_id=c.id
        """;
    private CobroClienteResponse map(java.sql.ResultSet rs)throws java.sql.SQLException{BigDecimal amount=rs.getBigDecimal("monto"),applied=rs.getBigDecimal("aplicado");return new CobroClienteResponse(rs.getLong("id"),rs.getString("folio"),rs.getLong("cliente_id"),rs.getTimestamp("fecha").toInstant(),amount,rs.getString("medio"),rs.getString("referencia"),rs.getString("estado"),applied,amount.subtract(applied),(Long)rs.getObject("asiento_contable_id"),rs.getObject("fecha_contable",LocalDate.class));}
    private Receipt receiptForUpdate(long id){return db.query("select id,folio,cliente_id,fecha,monto,medio,estado,fecha_contable from cobros_clientes where id=? for update",(rs,n)->new Receipt(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getTimestamp(4).toInstant(),rs.getBigDecimal(5),rs.getString(6),rs.getString(7),rs.getObject(8,LocalDate.class)),id).stream().findFirst().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cobro no encontrado"));}
    private BigDecimal disponible(long id,BigDecimal amount){return amount.subtract(db.queryForObject("select coalesce(sum(monto_aplicado),0) from aplicaciones_cobro where cobro_id=?",BigDecimal.class,id));}
    private long periodFor(LocalDate date){return db.query("select id from periodos_contables where estado='ABIERTO' and fecha_inicio<=? and fecha_fin>=?",(rs,n)->rs.getLong(1),date,date).stream().findFirst().orElseThrow(()->conflict("La fecha contable debe pertenecer a un periodo abierto"));}
    private Config config(String medio){return db.query("select cuenta_fondos_id,cuenta_anticipos_id from configuracion_cuentas_cobro where medio=?",(rs,n)->new Config(rs.getLong(1),rs.getLong(2)),medio).stream().findFirst().orElseThrow(()->conflict("Configure las cuentas del medio de cobro antes de confirmar"));}
    private long invoiceReceivableAccount(){return db.query("select cuenta_contable_id from configuracion_cuentas_factura_venta where concepto='CLIENTES_POR_COBRAR'",(rs,n)->rs.getLong(1)).stream().findFirst().orElseThrow(()->conflict("Configure primero la cuenta contable de CxC"));}
    private void requireAccount(long id,String type,String nature){Integer count=db.queryForObject("select count(*) from cuentas_contables where id=? and tipo=? and naturaleza=? and activa and acepta_movimientos",Integer.class,id,type,nature);if(count==null||count!=1)throw conflict("Una cuenta configurada no cumple el tipo o estado requerido");}
    private void lockKey(String key){db.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(?,6182050))",Integer.class,key);}
    private void lockBusiness(){db.execute("select pg_advisory_xact_lock(141474,41011)");}
    private long actor(){return users.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autenticación requerida")).usuarioId();}
    private static String hash(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}}
    private static String trim(String s){return s==null||s.isBlank()?null:s.trim();}
    private static ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
    private record Config(long fundsId,long advanceId){}
    private record Receipt(long id,String folio,long clienteId,Instant fecha,BigDecimal monto,String medio,String estado,LocalDate fechaContable){}
}
