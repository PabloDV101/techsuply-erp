package com.techsupply.erp_backend.reporteria.application;

import com.techsupply.erp_backend.audit.application.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReportingPoliciesService {
    private final JdbcTemplate db; private final AuditTrail audit; private final AuditActorProvider actor;
    public ReportingPoliciesService(JdbcTemplate db,AuditTrail audit,AuditActorProvider actor) {this.db=db;this.audit=audit;this.actor=actor;}
    public record Minimum(@NotNull @Positive Long productId,@NotNull @Positive Long warehouseId,
                          @NotNull @DecimalMin("0") @Digits(integer=11,fraction=3) BigDecimal minimum) {}
    public record Classification(@NotNull @Positive Long accountId,@NotBlank String classification,
                                 @NotNull LocalDate from,@NotNull LocalDate to) {}
    @Transactional @PreAuthorize("hasAuthority('REPORTERIA_MINIMOS_CONFIGURAR')")
    public Minimum minimum(Minimum r) {
        if(r==null || r.productId()==null || r.productId()<1 || r.warehouseId()==null || r.warehouseId()<1 || r.minimum()==null
            || r.minimum().signum()<0 || r.minimum().scale()>3 || r.minimum().precision()-r.minimum().scale()>11) bad();
        if(db.queryForObject("select count(*) from productos p cross join almacenes a where p.id=? and a.id=? and p.activo and a.activo",Integer.class,r.productId(),r.warehouseId())!=1)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Producto o almacén activo no encontrado");
        db.update("insert into minimos_producto_almacen(producto_id,almacen_id,minimo) values (?,?,?) on conflict(producto_id,almacen_id) do update set minimo=excluded.minimo",r.productId(),r.warehouseId(),r.minimum());
        audit.change(AuditAction.REPORT_MINIMUM_CONFIGURED,r.productId()); return r;
    }
    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_MARGEN_CLASIFICAR')")
    public Map<String,Object> classify(Classification r) {
        if(r==null || r.accountId()==null || r.accountId()<1 || r.from()==null || r.to()==null || r.from().isAfter(r.to())
          || r.classification()==null || !Set.of("INGRESO_OPERATIVO","COSTO_OPERATIVO","GASTO_OPERATIVO","NO_OPERATIVO").contains(r.classification())) bad();
        var accounts=db.queryForList("select tipo from cuentas_contables where id=? and acepta_movimientos for update",r.accountId());
        if(accounts.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Cuenta de movimiento no encontrada");
        String type=(String)accounts.getFirst().get("tipo");
        if(!Set.of("INGRESO","COSTO","GASTO").contains(type) || (!r.classification().equals("NO_OPERATIVO") && !r.classification().startsWith(type+"_"))) bad();
        if(db.queryForObject("select count(*) from clasificaciones_margen_cuenta where anulada_en is null and cuenta_contable_id=? and fecha_inicio<=? and fecha_fin>=?",Integer.class,r.accountId(),r.to(),r.from())>0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"La clasificación se solapa con otra vigente");
        Long id=db.queryForObject("insert into clasificaciones_margen_cuenta(cuenta_contable_id,clasificacion,fecha_inicio,fecha_fin,registrado_por_usuario_id) values (?,?,?,?,?) returning id",Long.class,r.accountId(),r.classification(),r.from(),r.to(),actor.currentUserId().orElseThrow());
        audit.change(AuditAction.OPERATING_ACCOUNT_CLASSIFIED,id); return Map.of("id",id,"accountId",r.accountId(),"classification",r.classification(),"from",r.from(),"to",r.to());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public Map<String,Object> margin(Long periodId) {
        if(periodId==null || periodId<1) bad();
        var periods=db.queryForList("select fecha_inicio,fecha_fin from periodos_contables where id=?",periodId);
        if(periods.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Periodo no encontrado");
        var period=periods.getFirst();
        var rows=db.queryForList("""
           select c.tipo,k.clasificacion,sum(case when c.tipo='INGRESO' then p.haber-p.debe else p.debe-p.haber end) amount
           from partidas_asiento p join asientos_contables a on a.id=p.asiento_id join cuentas_contables c on c.id=p.cuenta_contable_id
           left join clasificaciones_margen_cuenta k on k.cuenta_contable_id=c.id and k.anulada_en is null and a.fecha_contable between k.fecha_inicio and k.fecha_fin
           where a.estado in ('CONTABILIZADO','REVERSADO') and a.fecha_contable between ? and ? and c.tipo in ('INGRESO','COSTO','GASTO')
           group by c.tipo,k.clasificacion
           """,period.get("fecha_inicio"),period.get("fecha_fin"));
        BigDecimal income=BigDecimal.ZERO,cost=BigDecimal.ZERO,expense=BigDecimal.ZERO; boolean complete=true;
        for(var row:rows) {
            String kind=(String)row.get("clasificacion"),type=(String)row.get("tipo");
            if(kind==null || (!kind.equals("NO_OPERATIVO") && !kind.startsWith(type+"_"))) {complete=false;continue;}
            BigDecimal amount=(BigDecimal)row.get("amount");
            switch(kind) {case "INGRESO_OPERATIVO" -> income=income.add(amount);case "COSTO_OPERATIVO" -> cost=cost.add(amount);case "GASTO_OPERATIVO" -> expense=expense.add(amount);default -> {}}
        }
        boolean available=complete && income.signum()>0;
        Map<String,Object> out=new LinkedHashMap<>();out.put("periodId",periodId);out.put("source","partidas contabilizadas con clasificación explícita vigente");
        out.put("availability",available?"DISPONIBLE":"NO_DISPONIBLE");
        out.put("components",complete?List.of(Map.of("code","INCOME","label","Ingresos operativos","amount",income),Map.of("code","COST","label","Costos operativos","amount",cost),Map.of("code","EXPENSE","label","Gastos operativos","amount",expense)):List.of());
        out.put("value",available?income.subtract(cost).subtract(expense).multiply(BigDecimal.valueOf(100)).divide(income,6,java.math.RoundingMode.HALF_UP):null);
        out.put("unavailabilityReason",available?null:complete?"Ingresos operativos no positivos":"Falta clasificación explícita válida de cuentas");return out;
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER') or hasAuthority('CONTABILIDAD_MARGEN_CLASIFICAR')")
    public List<Map<String,Object>> classifications(Long accountId) {
        if(accountId==null || accountId<1) bad();
        return db.queryForList("select id,cuenta_contable_id,clasificacion,fecha_inicio,fecha_fin,anulada_en,motivo_anulacion from clasificaciones_margen_cuenta where cuenta_contable_id=? order by fecha_inicio,id",accountId);
    }
    public record VoidReason(@NotBlank @Size(max=300) String reason) {}
    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_MARGEN_CLASIFICAR')")
    public void voidClassification(Long id,VoidReason reason) {
        if(id==null || id<1 || reason==null || reason.reason()==null || reason.reason().isBlank() || reason.reason().length()>300) bad();
        var rows=db.queryForList("select id,anulada_en from clasificaciones_margen_cuenta where id=? for update",id);
        if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND,"Clasificación no encontrada");
        if(rows.getFirst().get("anulada_en")!=null) return;
        db.update("update clasificaciones_margen_cuenta set anulada_en=now(),motivo_anulacion=? where id=?",reason.reason().trim(),id);
        audit.change(AuditAction.OPERATING_CLASSIFICATION_VOIDED,id);
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('REPORTERIA_INVENTARIO_LEER') or hasAuthority('REPORTERIA_MINIMOS_CONFIGURAR')")
    public List<Map<String,Object>> minimums(Long productId) {
        if(productId==null || productId<1) bad();
        return db.queryForList("select producto_id,almacen_id,minimo from minimos_producto_almacen where producto_id=? order by almacen_id",productId);
    }
    private static void bad() {throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Política o periodo inválido");}
}
