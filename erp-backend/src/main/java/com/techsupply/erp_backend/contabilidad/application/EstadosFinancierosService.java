package com.techsupply.erp_backend.contabilidad.application;
import com.techsupply.erp_backend.contabilidad.application.dto.EstadoFinancieroResponse;
import com.techsupply.erp_backend.contabilidad.infrastructure.persistence.PeriodoContableRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
@Service
public class EstadosFinancierosService {
    private final JdbcTemplate db; private final PeriodoContableRepository periodos;
    public EstadosFinancierosService(JdbcTemplate db,PeriodoContableRepository periodos) { this.db=db; this.periodos=periodos; }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public EstadoFinancieroResponse resultados(Long id) {
        var p=periodos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Periodo no encontrado"));
        var v=importes(p.getFechaInicio(),p.getFechaFin());
        return new EstadoFinancieroResponse(id,p.getFechaInicio(),p.getFechaFin(),"DISPONIBLE","Saldos netos por tipo de cuenta",List.of(r("INGRESOS",v.get("INGRESO")),r("COSTOS",v.get("COSTO")),r("GASTOS",v.get("GASTO")),r("RESULTADO",resultado(v))));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public EstadoFinancieroResponse balance(Long id) {
        var p=periodos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Periodo no encontrado"));
        var v=importes(null,p.getFechaFin()); var result=resultado(v); var patrimonio=v.get("CAPITAL").add(result);
        return new EstadoFinancieroResponse(id,null,p.getFechaFin(),"DISPONIBLE","Saldos acumulados a la fecha de corte",List.of(r("ACTIVOS",v.get("ACTIVO")),r("PASIVOS",v.get("PASIVO")),r("CAPITAL",v.get("CAPITAL")),r("RESULTADO_ACUMULADO",result),r("PATRIMONIO",patrimonio),r("DIFERENCIA",v.get("ACTIVO").subtract(v.get("PASIVO")).subtract(patrimonio))));
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ) @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public EstadoFinancieroResponse flujo(Long id) {
        var p=periodos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Periodo no encontrado"));
        Integer cashAccounts=db.queryForObject("select count(*) from clasificaciones_flujo_cuenta where clasificacion='EFECTIVO' and anulada_en is null and fecha_inicio<=? and fecha_fin>=?",Integer.class,p.getFechaInicio(),p.getFechaFin());
        if(cashAccounts==null || cashAccounts==0) return noDisponible(id,p.getFechaInicio(),p.getFechaFin(),"Falta identificar efectivo y equivalentes durante el periodo");
        var rows=db.query("""
            select a.id,a.fecha_contable,p.debe-p.haber neto,cc.clasificacion cuenta,cp.clasificacion partida
            from asientos_contables a join partidas_asiento p on p.asiento_id=a.id
            left join clasificaciones_flujo_cuenta cc on cc.cuenta_contable_id=p.cuenta_contable_id
              and cc.anulada_en is null and a.fecha_contable between cc.fecha_inicio and cc.fecha_fin
            left join clasificaciones_flujo_partida cp on cp.partida_asiento_id=p.id and cp.anulada_en is null
            where a.estado in ('CONTABILIZADO','REVERSADO') and a.fecha_contable<=? order by a.id,p.id
            """,(rs,n)->new FlujoLinea(rs.getLong(1),rs.getObject(2,LocalDate.class),rs.getBigDecimal(3),rs.getString(4),rs.getString(5)),p.getFechaFin());
        BigDecimal apertura=BigDecimal.ZERO, cierre=BigDecimal.ZERO;
        Map<String,BigDecimal> actividad=new HashMap<>(); for(String c:List.of("OPERACION","INVERSION","FINANCIACION","NO_MONETARIO")) actividad.put(c,BigDecimal.ZERO);
        Map<Long,BigDecimal> noMonetario=new HashMap<>(); Set<Long> asientosConEfectivo=new HashSet<>(), asientosConActividad=new HashSet<>();
        for(var line:rows) {
            if(line.cuenta()==null) return noDisponible(id,p.getFechaInicio(),p.getFechaFin(),"Falta clasificación vigente de una cuenta con movimientos");
            boolean cash=line.cuenta().equals("EFECTIVO");
            if(cash) { cierre=cierre.add(line.neto()); if(line.fecha().isBefore(p.getFechaInicio())) apertura=apertura.add(line.neto()); }
            if(line.fecha().isBefore(p.getFechaInicio())) continue;
            if(cash) asientosConEfectivo.add(line.asiento());
            if(!cash) {
                if(line.partida()==null) return noDisponible(id,p.getFechaInicio(),p.getFechaFin(),"Falta clasificación de una partida del periodo");
                if(!line.partida().equals("NO_MONETARIO")) asientosConActividad.add(line.asiento());
                actividad.merge(line.partida(),line.neto().negate(),BigDecimal::add);
                if(line.partida().equals("NO_MONETARIO")) noMonetario.merge(line.asiento(),line.neto(),BigDecimal::add);
            }
        }
        if(!asientosConEfectivo.containsAll(asientosConActividad)) return noDisponible(id,p.getFechaInicio(),p.getFechaFin(),"Una actividad carece de movimiento de efectivo en su asiento");
        if(noMonetario.values().stream().anyMatch(x->x.signum()!=0)) return noDisponible(id,p.getFechaInicio(),p.getFechaFin(),"Una operación no monetaria no se compensa en su asiento");
        BigDecimal cambio=cierre.subtract(apertura), op=actividad.get("OPERACION"), inv=actividad.get("INVERSION"), fin=actividad.get("FINANCIACION");
        if(op.add(inv).add(fin).compareTo(cambio)!=0) return noDisponible(id,p.getFechaInicio(),p.getFechaFin(),"Las actividades no concilian con la variación de efectivo");
        BigDecimal utilidad=resultado(importes(p.getFechaInicio(),p.getFechaFin()));
        return new EstadoFinancieroResponse(id,p.getFechaInicio(),p.getFechaFin(),"DISPONIBLE","Método indirecto con partidas clasificadas y conciliación de efectivo",List.of(r("RESULTADO",utilidad),r("AJUSTES_OPERACION",op.subtract(utilidad)),r("OPERACION",op),r("INVERSION",inv),r("FINANCIACION",fin),r("VARIACION_EFECTIVO",cambio),r("EFECTIVO_INICIAL",apertura),r("EFECTIVO_FINAL",cierre)));
    }
    private EstadoFinancieroResponse noDisponible(Long id,LocalDate inicio,LocalDate fin,String motivo) { return new EstadoFinancieroResponse(id,inicio,fin,"NO_DISPONIBLE",motivo,List.of()); }
    private record FlujoLinea(long asiento,LocalDate fecha,BigDecimal neto,String cuenta,String partida) {}
    private Map<String,BigDecimal> importes(LocalDate inicio,LocalDate fin) {
        Map<String,BigDecimal> v=new HashMap<>(); for(String t:List.of("ACTIVO","PASIVO","CAPITAL","INGRESO","COSTO","GASTO")) v.put(t,BigDecimal.ZERO);
        db.query("""
          SELECT c.tipo,coalesce(sum(CASE WHEN c.tipo IN ('ACTIVO','COSTO','GASTO') THEN p.debe-p.haber ELSE p.haber-p.debe END),0)
          FROM cuentas_contables c JOIN partidas_asiento p ON p.cuenta_contable_id=c.id JOIN asientos_contables a ON a.id=p.asiento_id
          WHERE a.estado IN ('CONTABILIZADO','REVERSADO') AND a.fecha_contable<=? AND (cast(? as date) IS NULL OR a.fecha_contable>=?) GROUP BY c.tipo
          """,rs->{v.put(rs.getString(1),rs.getBigDecimal(2));},fin,inicio,inicio); return v;
    }
    private BigDecimal resultado(Map<String,BigDecimal> v) {return v.get("INGRESO").subtract(v.get("COSTO")).subtract(v.get("GASTO"));}
    private EstadoFinancieroResponse.Rubro r(String nombre,BigDecimal valor) {return new EstadoFinancieroResponse.Rubro(nombre,valor);}
}
