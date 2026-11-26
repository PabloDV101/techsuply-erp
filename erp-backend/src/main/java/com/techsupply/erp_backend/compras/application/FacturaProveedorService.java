package com.techsupply.erp_backend.compras.application;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.common.domain.CalculoImportes;
import com.techsupply.erp_backend.compras.application.dto.*;
import com.techsupply.erp_backend.contabilidad.application.AsientoContableService;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class FacturaProveedorService {
    private static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    private final JdbcTemplate db;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    private final AsientoContableService entries;

    public FacturaProveedorService(JdbcTemplate db, AuthenticatedUserProvider users,
            AuditTrail audit, AsientoContableService entries) {
        this.db=db; this.users=users; this.audit=audit; this.entries=entries;
    }

    @Transactional @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_REGISTRAR')")
    public FacturaProveedorResponse registrar(FacturaProveedorWriteRequest request) {
        validateRequest(request);
        Order order = order(request.ordenCompraId());
        Prepared prepared = prepare(request, order.id());
        long sequence = db.queryForObject("select nextval('factura_proveedor_folio_seq')", Long.class);
        if (sequence > 999999999999L) throw conflict("Secuencia de facturas agotada");
        long id;
        try {
            id = db.queryForObject("""
                insert into facturas_proveedor(folio_interno,proveedor_id,orden_compra_id,numero_factura_proveedor,
                fecha_emision,fecha_vencimiento,subtotal,impuestos,total,registrada_por_usuario_id)
                values (?,?,?,?,?,?,?,?,?,?) returning id
                """, Long.class, "FP-" + String.format(Locale.ROOT,"%012d",sequence), order.supplierId(), order.id(),
                request.numeroFacturaProveedor().trim(), request.fechaEmision(), request.fechaVencimiento(),
                prepared.totals().subtotal(), prepared.totals().impuestos(), prepared.totals().total(), actor());
        } catch (DataIntegrityViolationException ex) { throw conflict("Ya existe factura para la orden o número del proveedor"); }
        insertLines(id, prepared.lines());
        audit.change(AuditAction.PURCHASE_SUPPLIER_INVOICE_REGISTERED,id);
        return getInternal(id);
    }

    @Transactional @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_REGISTRAR')")
    public FacturaProveedorResponse editar(Long id, FacturaProveedorWriteRequest request) {
        validateRequest(request);
        Long orderId = db.query("select orden_compra_id from facturas_proveedor where id=?",
            (rs,n)->rs.getLong(1), id).stream().findFirst().orElseThrow(()->missing("Factura"));
        Order order = order(orderId);
        Invoice invoice = invoice(id);
        if (!orderId.equals(request.ordenCompraId()) || !invoice.status().equals("REGISTRADA"))
            throw conflict("Solo se edita una factura registrada de la misma orden");
        Prepared prepared = prepare(request, order.id());
        try {
            db.update("""
                update facturas_proveedor set numero_factura_proveedor=?,fecha_emision=?,fecha_vencimiento=?,
                    subtotal=?,impuestos=?,total=? where id=?
                """, request.numeroFacturaProveedor().trim(), request.fechaEmision(), request.fechaVencimiento(),
                prepared.totals().subtotal(),prepared.totals().impuestos(),prepared.totals().total(),id);
            db.update("delete from detalles_factura_proveedor where factura_proveedor_id=?",id);
            insertLines(id,prepared.lines());
        } catch (DataIntegrityViolationException ex) { throw conflict("Número de factura duplicado o datos inválidos"); }
        audit.change(AuditAction.PURCHASE_SUPPLIER_INVOICE_EDITED,id);
        return getInternal(id);
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_LEER')")
    public PageResponse<FacturaProveedorResumenResponse> listar(Long proveedorId,int page,int size) {
        var pageable=PageRequests.of(page,size,"fechaEmision","desc",
            Map.of("fechaEmision","f.fecha_emision","folioInterno","f.folio_interno"),"f.id");
        if(proveedorId!=null && proveedorId<1) throw bad("Identificador de proveedor inválido");
        String where=proveedorId==null?"":" where f.proveedor_id=?";
        long total=proveedorId==null?db.queryForObject("select count(*) from facturas_proveedor",Long.class)
            :db.queryForObject("select count(*) from facturas_proveedor where proveedor_id=?",Long.class,proveedorId);
        var mapper=(org.springframework.jdbc.core.RowMapper<FacturaProveedorResumenResponse>)(rs,n)->
            new FacturaProveedorResumenResponse(rs.getLong("id"),rs.getString("folio_interno"),rs.getLong("proveedor_id"),
                rs.getLong("orden_compra_id"),rs.getString("numero_factura_proveedor"),rs.getObject("fecha_emision",LocalDate.class),
                rs.getObject("fecha_vencimiento",LocalDate.class),rs.getBigDecimal("subtotal"),rs.getBigDecimal("impuestos"),
                rs.getBigDecimal("total"),rs.getString("estado"),rs.getObject("asiento_contable_id",Long.class));
        String select="select f.id,f.folio_interno,f.proveedor_id,f.orden_compra_id,f.numero_factura_proveedor,"
            +"f.fecha_emision,f.fecha_vencimiento,f.subtotal,f.impuestos,f.total,f.estado,f.asiento_contable_id from facturas_proveedor f";
        String order=" order by f.fecha_emision desc,f.id desc limit ? offset ?";
        var rows=proveedorId==null?db.query(select+order,mapper,pageable.getPageSize(),pageable.getOffset())
            :db.query(select+where+order,mapper,proveedorId,pageable.getPageSize(),pageable.getOffset());
        return new PageResponse<>(rows,page,size,total,(int)Math.ceil((double)total/size));
    }

    @Transactional @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_VALIDAR')")
    public FacturaProveedorResponse validar(Long id) {
        Long orderId = db.query("select orden_compra_id from facturas_proveedor where id=?",
            (rs,n)->rs.getLong(1), id).stream().findFirst().orElseThrow(()->missing("Factura"));
        Order order = order(orderId);
        Invoice invoice = invoice(id);
        if (invoice.status().equals("VALIDADA")) return getInternal(id);
        if (!invoice.status().equals("REGISTRADA") || !order.status().equals("RECIBIDA")
            || !order.supplierId().equals(invoice.supplierId())) throw conflict("Orden no recibida o factura inválida");

        var rows = db.query("""
            select d.detalle_orden_compra_id,d.cantidad_facturada,d.precio_unitario,d.descuento_pct,d.tasa_impuesto,
                d.subtotal,d.impuestos,d.total,o.cantidad,o.precio_unitario,o.descuento_pct,o.tasa_impuesto,
                coalesce((select sum(dr.cantidad_aceptada) from detalles_recepcion dr
                  join recepciones_compra r on r.id=dr.recepcion_id
                  where dr.detalle_orden_compra_id=o.id and r.estado='CONFIRMADA'),0) as aceptada,
                coalesce((select sum(m.cantidad*m.costo_unitario) from movimientos_inventario m
                  join detalles_recepcion dr on dr.id=m.detalle_recepcion_id
                  join recepciones_compra r on r.id=dr.recepcion_id
                  where dr.detalle_orden_compra_id=o.id and r.estado='CONFIRMADA'),0) as costo
            from detalles_factura_proveedor d join detalles_orden_compra o on o.id=d.detalle_orden_compra_id
            where d.factura_proveedor_id=? and o.orden_compra_id=? order by o.id
            """, (rs,n)->new Reconcile(rs.getBigDecimal(2),rs.getBigDecimal(3),rs.getBigDecimal(4),rs.getBigDecimal(5),
                rs.getBigDecimal(6),rs.getBigDecimal(7),rs.getBigDecimal(8),rs.getBigDecimal(9),rs.getBigDecimal(10),
                rs.getBigDecimal(11),rs.getBigDecimal(12),rs.getBigDecimal(13),rs.getBigDecimal(14)),id,orderId);
        long orderLines = db.queryForObject("select count(*) from detalles_orden_compra where orden_compra_id=?",Long.class,orderId);
        if (rows.isEmpty() || rows.size()!=orderLines) throw conflict("Faltan líneas de factura u orden");
        BigDecimal accrued = BigDecimal.ZERO.setScale(2);
        for (var line:rows) {
            if (line.qty().compareTo(line.orderQty())!=0 || line.accepted().compareTo(line.orderQty())!=0
                || line.price().compareTo(line.orderPrice())!=0 || line.discount().compareTo(line.orderDiscount())!=0
                || line.taxRate().compareTo(line.orderTax())!=0) throw conflict("Factura no concilia cantidades o valores de la orden");
            BigDecimal netUnit = line.price().multiply(BigDecimal.ONE.subtract(line.discount().movePointLeft(2)));
            try { netUnit=netUnit.setScale(2,RoundingMode.UNNECESSARY); }
            catch (ArithmeticException ex) { throw conflict("Precio neto no concilia con costo recibido a centavos"); }
            BigDecimal expectedCost = line.qty().multiply(netUnit);
            if (line.cost().compareTo(expectedCost)!=0 || line.cost().compareTo(line.subtotal())!=0)
                throw conflict("Costo recibido y subtotal de factura no concilian");
            accrued=accrued.add(line.cost());
        }
        if (accrued.compareTo(invoice.subtotal())!=0 || invoice.total().compareTo(order.total())!=0)
            throw conflict("Factura no concilia con recepción y orden");
        LocalDate date=LocalDate.now(ZONE);
        Long period=db.query("select id from periodos_contables where estado='ABIERTO' and fecha_inicio<=? and fecha_fin>=?",
            (rs,n)->rs.getLong(1),date,date).stream().findFirst().orElseThrow(()->conflict("Falta periodo contable abierto"));
        Map<String,Long> accounts=accounts();
        var lines=new ArrayList<PartidaAsientoCreateRequest>();
        lines.add(new PartidaAsientoCreateRequest(accounts.get("MERCANCIA_RECIBIDA_NO_FACTURADA"),invoice.subtotal(),BigDecimal.ZERO,"Liberar mercancía recibida no facturada"));
        if (invoice.taxes().signum()>0) lines.add(new PartidaAsientoCreateRequest(accounts.get("IVA_ACREDITABLE"),invoice.taxes(),BigDecimal.ZERO,"Impuesto acreditable"));
        lines.add(new PartidaAsientoCreateRequest(accounts.get("CUENTAS_POR_PAGAR"),BigDecimal.ZERO,invoice.total(),"Obligación con proveedor"));
        var seat=entries.contabilizarFacturaProveedor(new AsientoContableCreateRequest(period,date,
            "Factura de proveedor " + id,"PROVEEDOR-FACTURA-" + id,lines),id);
        int updated=db.update("""
            update facturas_proveedor set estado='VALIDADA',validada_por_usuario_id=?,validada_en=now(),asiento_contable_id=?
            where id=? and estado='REGISTRADA'
            """,actor(),seat.id(),id);
        if (updated!=1) throw conflict("Factura ya fue validada");
        db.update("""
            insert into cuentas_por_pagar(factura_proveedor_id,proveedor_id,asiento_contable_id,monto_original,fecha_emision,fecha_vencimiento)
            values (?,?,?,?,?,?)
            """,id,invoice.supplierId(),seat.id(),invoice.total(),invoice.issue(),invoice.due());
        audit.change(AuditAction.PURCHASE_SUPPLIER_INVOICE_VALIDATED,id);
        audit.change(AuditAction.ACCOUNTING_PAYABLE_CREATED,id);
        return getInternal(id);
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('COMPRAS_FACTURAS_PROVEEDOR_LEER')")
    public FacturaProveedorResponse obtener(Long id) { return getInternal(id); }

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CONTABILIDAD_CXP_LEER')")
    public Map<String,Object> cuentaPorPagar(Long id) {
        return db.query("""
            select c.id,c.factura_proveedor_id,c.proveedor_id,c.asiento_contable_id,c.monto_original,
              c.fecha_emision,c.fecha_vencimiento,
              c.monto_original-coalesce((select sum(a.monto_aplicado) from aplicaciones_pago_proveedor a
                join pagos_proveedor p on p.id=a.pago_proveedor_id and p.estado='CONFIRMADO'
                where a.cuenta_por_pagar_id=c.id),0) saldo_pendiente from cuentas_por_pagar c where c.id=?
            """,(rs,n)->Map.<String,Object>of("id",rs.getLong(1),"facturaProveedorId",rs.getLong(2),
                "proveedorId",rs.getLong(3),"asientoContableId",rs.getLong(4),"montoOriginal",rs.getBigDecimal(5),
                "saldoPendiente",rs.getBigDecimal(8),"fechaEmision",rs.getDate(6).toLocalDate(),
                "fechaVencimiento",rs.getDate(7).toLocalDate()),id).stream().findFirst().orElseThrow(()->missing("Cuenta por pagar"));
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CONTABILIDAD_CXP_LEER')")
    public PageResponse<Map<String,Object>> listarCuentasPorPagar(Long proveedorId, int page, int size) {
        var pageable=PageRequests.of(page,size,"fechaVencimiento","asc",
            Map.of("fechaVencimiento","c.fecha_vencimiento"),"c.id");
        if(proveedorId!=null && proveedorId<1) throw bad("Identificador de proveedor inválido");
        String where=proveedorId==null?"":" where c.proveedor_id=?";
        Long total=proveedorId==null?db.queryForObject("select count(*) from cuentas_por_pagar",Long.class)
            :db.queryForObject("select count(*) from cuentas_por_pagar where proveedor_id=?",Long.class,proveedorId);
        String select="""
            select c.id,c.factura_proveedor_id,c.proveedor_id,f.folio_interno,c.asiento_contable_id,
              c.monto_original,c.fecha_emision,c.fecha_vencimiento,
              c.monto_original-coalesce((select sum(a.monto_aplicado) from aplicaciones_pago_proveedor a
                join pagos_proveedor p on p.id=a.pago_proveedor_id and p.estado='CONFIRMADO'
                where a.cuenta_por_pagar_id=c.id),0) saldo_pendiente
            from cuentas_por_pagar c join facturas_proveedor f on f.id=c.factura_proveedor_id
            """;
        var mapper=(org.springframework.jdbc.core.RowMapper<Map<String,Object>>)(rs,n)->Map.of(
            "id",rs.getLong("id"),"facturaProveedorId",rs.getLong("factura_proveedor_id"),
            "proveedorId",rs.getLong("proveedor_id"),"folioFactura",rs.getString("folio_interno"),
            "asientoContableId",rs.getLong("asiento_contable_id"),"montoOriginal",rs.getBigDecimal("monto_original"),
            "saldoPendiente",rs.getBigDecimal("saldo_pendiente"),"fechaEmision",rs.getDate("fecha_emision").toLocalDate(),
            "fechaVencimiento",rs.getDate("fecha_vencimiento").toLocalDate());
        var rows=proveedorId==null?db.query(select+" order by c.fecha_vencimiento,c.id limit ? offset ?",mapper,
            pageable.getPageSize(),pageable.getOffset()):db.query(select+where+" order by c.fecha_vencimiento,c.id limit ? offset ?",mapper,
            proveedorId,pageable.getPageSize(),pageable.getOffset());
        return new PageResponse<>(rows,page,size,total,(int)Math.ceil((double)total/size));
    }

    @Transactional @PreAuthorize("hasAuthority('CONTABILIDAD_CXP_CONFIGURAR')")
    public Map<String,Long> configurar(Long iva,Long pagar) {
        if (iva==null || pagar==null || iva.equals(pagar)) throw bad("Cuentas distintas requeridas");
        requireAccount(iva,"ACTIVO","DEUDORA"); requireAccount(pagar,"PASIVO","ACREEDORA");
        db.update("delete from configuracion_cuentas_factura_proveedor");
        db.update("insert into configuracion_cuentas_factura_proveedor(concepto,cuenta_contable_id,actualizado_por_usuario_id) values ('IVA_ACREDITABLE',?,?)",iva,actor());
        db.update("insert into configuracion_cuentas_factura_proveedor(concepto,cuenta_contable_id,actualizado_por_usuario_id) values ('CUENTAS_POR_PAGAR',?,?)",pagar,actor());
        audit.change(AuditAction.ACCOUNTING_SUPPLIER_INVOICE_CONFIGURED,1L);
        return Map.of("ivaAcreditableCuentaId",iva,"cuentasPorPagarCuentaId",pagar);
    }

    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('CONTABILIDAD_CXP_LEER')")
    public Map<String,Long> configuracion() {
        var map=new HashMap<String,Long>();
        db.query("select concepto,cuenta_contable_id from configuracion_cuentas_factura_proveedor",
            rs->{map.put(rs.getString(1),rs.getLong(2));});
        return Map.copyOf(map);
    }

    private Map<String,Long> accounts() {
        var result=new HashMap<String,Long>();
        db.query("""
            select m.concepto,m.cuenta_contable_id from configuracion_cuentas_factura_proveedor m
            union all select m.concepto,m.cuenta_contable_id from configuracion_cuentas_inventario m
            where m.concepto='MERCANCIA_RECIBIDA_NO_FACTURADA'
            """,rs->{result.put(rs.getString(1),rs.getLong(2));});
        if (result.size()!=3) throw conflict("Configure las cuentas de factura e Inventario");
        requireAccount(result.get("MERCANCIA_RECIBIDA_NO_FACTURADA"),"PASIVO","ACREEDORA");
        requireAccount(result.get("IVA_ACREDITABLE"),"ACTIVO","DEUDORA");
        requireAccount(result.get("CUENTAS_POR_PAGAR"),"PASIVO","ACREEDORA");
        if (new HashSet<>(result.values()).size()!=3) throw conflict("Las cuentas configuradas deben ser distintas");
        return result;
    }

    private void requireAccount(Long id,String type,String nature) {
        if (id==null || db.query("""
            select 1 from cuentas_contables where id=? and tipo=? and naturaleza=? and activa=true and acepta_movimientos=true
            """,(rs,n)->rs.getInt(1),id,type,nature).isEmpty()) throw conflict("Cuenta contable no compatible o inactiva");
    }

    private Order order(Long id) {
        return db.query("select id,proveedor_id,estado,total from ordenes_compra where id=? for update",
            (rs,n)->new Order(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getBigDecimal(4)),id)
            .stream().findFirst().orElseThrow(()->missing("Orden"));
    }
    private Invoice invoice(Long id) {
        return db.query("""
            select proveedor_id,orden_compra_id,estado,subtotal,impuestos,total,fecha_emision,fecha_vencimiento
            from facturas_proveedor where id=? for update
            """,(rs,n)->new Invoice(rs.getLong(1),rs.getLong(2),rs.getString(3),rs.getBigDecimal(4),
                rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getDate(7).toLocalDate(),rs.getDate(8).toLocalDate()),id)
            .stream().findFirst().orElseThrow(()->missing("Factura"));
    }
    private Prepared prepare(FacturaProveedorWriteRequest request,Long orderId) {
        var distinct=new HashSet<Long>(); var prepared=new ArrayList<Line>();
        var values=new ArrayList<CalculoImportes.Linea>();
        for (var line:request.detalles()) {
            if (line==null || !distinct.add(line.detalleOrdenCompraId())) throw bad("Línea duplicada o inválida");
            if (db.query("select id from detalles_orden_compra where id=? and orden_compra_id=?",
                (rs,n)->rs.getLong(1),line.detalleOrdenCompraId(),orderId).isEmpty()) throw bad("Línea ajena a la orden");
            try {
                new com.techsupply.erp_backend.compras.domain.model.CalculoOrden.Linea(line.cantidadFacturada(),
                    line.precioUnitario(),line.descuentoPct(),line.tasaImpuesto());
                var amount=CalculoImportes.calcular(List.of(new CalculoImportes.Linea(line.cantidadFacturada(),
                    line.precioUnitario(),line.descuentoPct(),line.tasaImpuesto())));
                prepared.add(new Line(line,amount));
                values.add(new CalculoImportes.Linea(line.cantidadFacturada(),line.precioUnitario(),line.descuentoPct(),line.tasaImpuesto()));
            } catch (IllegalArgumentException | NullPointerException ex) { throw bad("Importe de línea inválido"); }
        }
        try {
            var totals=CalculoImportes.calcular(values);
            if (totals.total().signum()<=0) throw bad("El total de factura debe ser positivo");
            return new Prepared(prepared,totals);
        }
        catch (IllegalArgumentException ex) { throw bad("Importe de factura inválido"); }
    }
    private void insertLines(Long id,List<Line> lines) {
        for (var prepared:lines) {
            var line=prepared.input(); var amount=prepared.amount();
            db.update("""
                insert into detalles_factura_proveedor(factura_proveedor_id,detalle_orden_compra_id,cantidad_facturada,
                  precio_unitario,descuento_pct,tasa_impuesto,subtotal,impuestos,total) values (?,?,?,?,?,?,?,?,?)
                """,id,line.detalleOrdenCompraId(),line.cantidadFacturada(),line.precioUnitario(),
                line.descuentoPct(),line.tasaImpuesto(),amount.subtotal(),amount.impuestos(),amount.total());
        }
    }
    private FacturaProveedorResponse getInternal(Long id) {
        var header=db.query("""
            select id,folio_interno,proveedor_id,orden_compra_id,numero_factura_proveedor,fecha_emision,
              fecha_vencimiento,subtotal,impuestos,total,estado,asiento_contable_id from facturas_proveedor where id=?
            """,(rs,n)->new FacturaProveedorResponse(rs.getLong(1),rs.getString(2),rs.getLong(3),rs.getLong(4),
                rs.getString(5),rs.getDate(6).toLocalDate(),rs.getDate(7).toLocalDate(),rs.getBigDecimal(8),
                rs.getBigDecimal(9),rs.getBigDecimal(10),rs.getString(11),(Long)rs.getObject(12),List.of()),id)
            .stream().findFirst().orElseThrow(()->missing("Factura"));
        var lines=db.query("""
            select detalle_orden_compra_id,cantidad_facturada,precio_unitario,descuento_pct,tasa_impuesto,
              subtotal,impuestos,total from detalles_factura_proveedor where factura_proveedor_id=? order by id
            """,(rs,n)->new FacturaProveedorResponse.Linea(rs.getLong(1),rs.getBigDecimal(2),rs.getBigDecimal(3),
                rs.getBigDecimal(4),rs.getBigDecimal(5),rs.getBigDecimal(6),rs.getBigDecimal(7),rs.getBigDecimal(8)),id);
        return new FacturaProveedorResponse(header.id(),header.folioInterno(),header.proveedorId(),header.ordenCompraId(),
            header.numeroFacturaProveedor(),header.fechaEmision(),header.fechaVencimiento(),header.subtotal(),
            header.impuestos(),header.total(),header.estado(),header.asientoContableId(),lines);
    }
    private static void validateRequest(FacturaProveedorWriteRequest request) {
        if (request==null || request.ordenCompraId()==null || request.ordenCompraId()<1
          || request.numeroFacturaProveedor()==null || request.numeroFacturaProveedor().isBlank()
          || request.numeroFacturaProveedor().trim().length()>60 || request.fechaEmision()==null
          || request.fechaVencimiento()==null || request.fechaVencimiento().isBefore(request.fechaEmision())
          || request.detalles()==null || request.detalles().isEmpty()) throw bad("Factura de proveedor inválida");
    }
    private long actor() { return users.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autenticación requerida")).usuarioId(); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException missing(String message) { return new ResponseStatusException(HttpStatus.NOT_FOUND,message+" no encontrada"); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private record Order(Long id,Long supplierId,String status,BigDecimal total) {}
    private record Invoice(Long supplierId,Long orderId,String status,BigDecimal subtotal,BigDecimal taxes,BigDecimal total,LocalDate issue,LocalDate due) {}
    private record Prepared(List<Line> lines,CalculoImportes.Totales totals) {}
    private record Line(FacturaProveedorWriteRequest.Linea input,CalculoImportes.Totales amount) {}
    private record Reconcile(BigDecimal qty,BigDecimal price,BigDecimal discount,BigDecimal taxRate,
        BigDecimal subtotal,BigDecimal taxes,BigDecimal total,BigDecimal orderQty,BigDecimal orderPrice,
        BigDecimal orderDiscount,BigDecimal orderTax,BigDecimal accepted,BigDecimal cost) {}
}
