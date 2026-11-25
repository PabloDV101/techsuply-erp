package com.techsupply.erp_backend.reporteria.application;

import com.techsupply.erp_backend.common.api.PageResponse;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.namedparam.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class AnalyticalReportsService {
    private final NamedParameterJdbcTemplate db;
    public AnalyticalReportsService(NamedParameterJdbcTemplate db) { this.db=db; }
    public record Filter(LocalDate from, LocalDate to, LocalDate asOf, Integer days, Long warehouseId,
                         Long productId, Long customerId, int page, int size, String sort, String direction) {}
    private MapSqlParameterSource params(Filter f, boolean range) {
        if(f.page()<0 || f.size()<1 || f.size()>100 || (f.direction()==null || !Set.of("asc","desc").contains(f.direction()))
            || (f.warehouseId()!=null && f.warehouseId()<1) || (f.productId()!=null && f.productId()<1)
            || (f.customerId()!=null && f.customerId()<1)) bad();
        if(range && (f.from()==null || f.to()==null || f.from().isAfter(f.to()) || f.to().isAfter(LocalDate.now(ZoneId.of("America/Mexico_City")))
                || java.time.temporal.ChronoUnit.DAYS.between(f.from(),f.to())>3660)) bad();
        return new MapSqlParameterSource().addValue("from",f.from()).addValue("to",f.to()).addValue("asOf",f.asOf())
            .addValue("days",f.days()).addValue("warehouse",f.warehouseId()).addValue("product",f.productId())
            .addValue("customer",f.customerId()).addValue("limit",f.size()).addValue("offset",(long)f.page()*f.size());
    }
    private static void bad() { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Filtros de reporte inválidos"); }
    private PageResponse<Map<String,Object>> page(String query, MapSqlParameterSource p, Filter f, Set<String> sorts, String tie) {
        if(f.sort()==null || !sorts.contains(f.sort())) bad();
        long total=db.queryForObject("select count(*) from ("+query+") q",p,Long.class);
        // Sort identifiers come exclusively from the per-report allowlist.
        var rows=db.queryForList("select * from ("+query+") q order by \""+f.sort()+"\" "+f.direction()+" nulls last,"+tie+" limit :limit offset :offset",p);
        for(var row:rows) row.replaceAll((key,value)->value instanceof java.sql.Timestamp t?t.toInstant():value instanceof java.sql.Date d?d.toLocalDate():value);
        return new PageResponse<>(rows,f.page(),f.size(),total,(int)Math.min(Integer.MAX_VALUE,(total+f.size()-1)/f.size()));
    }
    @PreAuthorize("hasAuthority('REPORTERIA_INVENTARIO_LEER')")
    public PageResponse<Map<String,Object>> lowStock(Filter f) {
        return page("""
            select p.id "productId",a.id "warehouseId",coalesce(e.cantidad_fisica,0) physical,
             coalesce(e.cantidad_reservada,0) reserved,coalesce(e.cantidad_fisica-e.cantidad_reservada,0) available,
             n.minimo "reorderPoint",statement_timestamp() "consultedAt"
            from minimos_producto_almacen n join productos p on p.id=n.producto_id and p.activo
             join almacenes a on a.id=n.almacen_id and a.activo
             left join existencias e on e.producto_id=p.id and e.almacen_id=a.id
            where coalesce(e.cantidad_fisica-e.cantidad_reservada,0)<=n.minimo
             and (cast(:warehouse as bigint) is null or a.id=:warehouse)
             and (cast(:product as bigint) is null or p.id=:product)
            """,params(f,false),f,Set.of("productId","warehouseId","available"),"\"productId\",\"warehouseId\"");
    }
    @PreAuthorize("hasAuthority('REPORTERIA_INVENTARIO_LEER')")
    public PageResponse<Map<String,Object>> inactive(Filter f) {
        if(f.asOf()==null || f.asOf().isAfter(LocalDate.now(ZoneId.of("America/Mexico_City"))) || f.days()==null || f.days()<1 || f.days()>36500) bad();
        return page("""
            with pairs as (select producto_id,almacen_id from existencias union select producto_id,almacen_id from minimos_producto_almacen
                          union select producto_id,almacen_id from movimientos_inventario), last_move as (
             select x.producto_id,x.almacen_id,max(m.fecha) last_at from pairs x
             left join movimientos_inventario m on m.producto_id=x.producto_id and m.almacen_id=x.almacen_id
              and m.fecha<((cast(:asOf as date)+1)::timestamp at time zone 'America/Mexico_City')
             group by x.producto_id,x.almacen_id)
            select l.producto_id "productId",l.almacen_id "warehouseId",cast(:asOf as date) "asOf",cast(:days as integer) "thresholdDays",
             last_at "lastMovementAt",case when last_at is null then 'NUNCA_REGISTRADO' else 'SIN_MOVIMIENTO' end status
            from last_move l join productos p on p.id=l.producto_id and p.activo join almacenes a on a.id=l.almacen_id and a.activo
            where (cast(:warehouse as bigint) is null or l.almacen_id=:warehouse)
             and (last_at is null or (last_at at time zone 'America/Mexico_City')::date<=cast(:asOf as date)-cast(:days as integer))
            """,params(f,false),f,Set.of("productId","lastMovementAt"),"\"productId\",\"warehouseId\"");
    }
    @PreAuthorize("hasAuthority('REPORTERIA_INVENTARIO_LEER')")
    public PageResponse<Map<String,Object>> abc(Filter f) {
        return page("""
          with basis as (
           select m.producto_id,sum(m.cantidad*m.costo_unitario) amount from movimientos_inventario m
           join detalles_despacho d on d.id=m.detalle_despacho_id join despachos s on s.id=d.despacho_id and s.estado='CONFIRMADO'
           where m.motivo='DESPACHO_VENTA' and m.fecha>=(cast(:from as timestamp) at time zone 'America/Mexico_City')
            and m.fecha<((cast(:to as date)+1)::timestamp at time zone 'America/Mexico_City')
            and (cast(:warehouse as bigint) is null or m.almacen_id=:warehouse) group by m.producto_id), ranked as (
           select *,sum(amount) over() total,sum(amount) over(order by amount desc,producto_id rows unbounded preceding) cumulative from basis)
          select producto_id "productId",cast(:from as date) "from",cast(:to as date) "to",amount "basisAmount",
           case when total>0 then 'DISPONIBLE' else 'NO_DISPONIBLE' end availability,
           round(cumulative*100/nullif(total,0),6) "cumulativePercent",
           case when total=0 then null when (cumulative-amount)*100/total<80 then 'A'
                when (cumulative-amount)*100/total<95 then 'B' else 'C' end classification,
           case when total=0 then 'Base de costo total cero' end "unavailabilityReason" from ranked
          """,params(f,true),f,Set.of("productId","basisAmount","cumulativePercent"),"\"productId\"");
    }
    @PreAuthorize("hasAuthority('REPORTERIA_INVENTARIO_LEER')")
    public PageResponse<Map<String,Object>> turnover(Filter f) {
        return page("""
          with moves as (
           select m.*,case when tipo='ENTRADA' then cantidad else -cantidad end delta,
             (fecha at time zone 'America/Mexico_City')::date movement_day from movimientos_inventario m
           where cast(:warehouse as bigint) is null or almacen_id=:warehouse), running as (
           select *,sum(delta) over(partition by producto_id,almacen_id order by fecha,id rows unbounded preceding) running from moves), pairs as (
           select producto_id,almacen_id,coalesce(sum(delta),0) total,min(running) minimum,min(movement_day) first_day,
            coalesce(sum(cantidad) filter(where motivo='DESPACHO_VENTA' and movement_day between :from and :to),0) outbound,
            coalesce(sum(delta*case when movement_day<:from then (cast(:to as date)-cast(:from as date)+1)
              when movement_day<=:to then (cast(:to as date)-movement_day+1) else 0 end),0)/(cast(:to as date)-cast(:from as date)+1) average
           from running group by producto_id,almacen_id), checked as (
           select coalesce(x.producto_id,e.producto_id) product,
            coalesce(x.outbound,0) outbound,coalesce(x.average,0) average,
            (x.first_day is not null and x.first_day<=:from and x.minimum>=0 and x.total=coalesce(e.cantidad_fisica,0)) valid
           from pairs x full join (select * from existencias where cast(:warehouse as bigint) is null or almacen_id=:warehouse) e
            on e.producto_id=x.producto_id and e.almacen_id=x.almacen_id), grouped as (
           select product,sum(outbound) numerator,sum(average) denominator,bool_and(coalesce(valid,false)) valid from checked group by product)
          select product "productId",cast(:from as date) "from",cast(:to as date) "to",'kardex: salidas / físico medio diario al cierre' source,
           case when valid and denominator>0 then 'DISPONIBLE' else 'NO_DISPONIBLE' end availability,
           case when valid and denominator>0 then round(numerator/denominator,6) end value,
           case when valid then numerator end numerator,case when valid then denominator end denominator,
           case when not valid then 'Cobertura del kardex o conciliación insuficiente' when denominator<=0 then 'Existencia media cero' end "unavailabilityReason"
          from grouped
          """,params(f,true),f,Set.of("productId","value"),"\"productId\"");
    }
    @PreAuthorize("hasAuthority('VENTAS_FACTURAS_LEER') and hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public PageResponse<Map<String,Object>> profitability(Filter f) {
        return page("""
          with lines as (
           select l.id,f.cliente_id,l.subtotal,l.cantidad,coalesce(sum(m.cantidad),0) shipped,
            coalesce(sum(m.cantidad*m.costo_unitario),0) cost
           from facturas_venta f join detalles_factura_venta l on l.factura_venta_id=f.id
           left join detalles_despacho d on d.detalle_pedido_id=l.detalle_pedido_id
           left join despachos s on s.id=d.despacho_id and s.estado='CONFIRMADO'
           left join movimientos_inventario m on m.detalle_despacho_id=d.id and s.id is not null and m.motivo='DESPACHO_VENTA'
           where f.estado='EMITIDA' and f.fecha_emision between :from and :to
             and (cast(:customer as bigint) is null or f.cliente_id=:customer)
           group by l.id,f.cliente_id), totals as (
           select cliente_id,sum(subtotal) revenue,sum(cost) cost,bool_and(shipped=cantidad) valid from lines group by cliente_id)
          select cliente_id "customerId",cast(:from as date) "from",cast(:to as date) "to",'facturas emitidas y costo de despachos conciliados' source,
           case when valid then 'DISPONIBLE' else 'NO_DISPONIBLE' end availability,revenue,
           case when valid then cost end cost,case when valid then revenue-cost end result,
           case when not valid then 'Cantidades facturadas y despachadas no concilian' end "unavailabilityReason" from totals
          """,params(f,true),f,Set.of("customerId","result"),"\"customerId\"");
    }
}
