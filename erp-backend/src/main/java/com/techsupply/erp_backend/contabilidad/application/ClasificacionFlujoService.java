package com.techsupply.erp_backend.contabilidad.application;
import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import com.techsupply.erp_backend.contabilidad.application.dto.ClasificacionFlujoRequests.*;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
@Service
public class ClasificacionFlujoService {
    private final JdbcTemplate db; private final AuthenticatedUserProvider users; private final AuditTrail audit;
    public ClasificacionFlujoService(JdbcTemplate db, AuthenticatedUserProvider users, AuditTrail audit) { this.db=db; this.users=users; this.audit=audit; }
    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_FLUJO_CLASIFICAR')")
    public Map<String,Long> cuenta(Cuenta req) {
        if(req.hasta().isBefore(req.desde())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Fechas inválidas");
        Long id=db.queryForObject("insert into clasificaciones_flujo_cuenta(cuenta_contable_id,clasificacion,fecha_inicio,fecha_fin,registrado_por_usuario_id) values (?,?,?,?,?) returning id",Long.class,req.cuentaId(),req.clasificacion(),req.desde(),req.hasta(),actor());
        audit.change(AuditAction.ACCOUNTING_CASH_FLOW_ACCOUNT_CLASSIFIED,id); return Map.of("id",id);
    }
    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_FLUJO_CLASIFICAR')")
    public Map<String,Long> partida(Partida req) {
        Long id=db.queryForObject("insert into clasificaciones_flujo_partida(partida_asiento_id,clasificacion,registrado_por_usuario_id) values (?,?,?) returning id",Long.class,req.partidaId(),req.clasificacion(),actor());
        audit.change(AuditAction.ACCOUNTING_CASH_FLOW_LINE_CLASSIFIED,id); return Map.of("id",id);
    }
    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_FLUJO_CLASIFICAR')")
    public void anularCuenta(Long id,Anulacion req) { anular("clasificaciones_flujo_cuenta",id,req,AuditAction.ACCOUNTING_CASH_FLOW_ACCOUNT_VOIDED); }
    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_FLUJO_CLASIFICAR')")
    public void anularPartida(Long id,Anulacion req) { anular("clasificaciones_flujo_partida",id,req,AuditAction.ACCOUNTING_CASH_FLOW_LINE_VOIDED); }
    private void anular(String tabla,Long id,Anulacion req,AuditAction action) {
        int n=db.update("update "+tabla+" set anulada_en=now(),anulada_por_usuario_id=?,motivo_anulacion=? where id=? and anulada_en is null",actor(),req.motivo().trim(),id);
        if(n!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Clasificación ausente o anulada");
        audit.change(action,id);
    }
    private long actor(){return users.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED)).usuarioId();}
}
