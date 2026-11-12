package com.techsupply.erp_backend.ventas.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.*;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import com.techsupply.erp_backend.ventas.application.dto.*;
import com.techsupply.erp_backend.ventas.domain.model.*;
import com.techsupply.erp_backend.ventas.infrastructure.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated
public class FacturaService {
    private final FacturaVentaRepository facturas;
    private final DetalleFacturaVentaRepository detalles;
    private final PedidoVentaRepository pedidos;
    private final DetallePedidoRepository lineasPedido;
    private final JdbcTemplate db;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    private final Clock clock;
    public FacturaService(FacturaVentaRepository facturas,DetalleFacturaVentaRepository detalles,PedidoVentaRepository pedidos,
            DetallePedidoRepository lineasPedido,JdbcTemplate db,AuthenticatedUserProvider users,AuditTrail audit,Clock clock) {
        this.facturas=facturas;this.detalles=detalles;this.pedidos=pedidos;this.lineasPedido=lineasPedido;
        this.db=db;this.users=users;this.audit=audit;this.clock=clock;
    }
    public record Resultado(FacturaResponse factura,boolean creada) {}
    @Transactional @PreAuthorize("hasAuthority('VENTAS_FACTURAS_EMITIR')")
    public Resultado emitir(@NotNull @Valid FacturaCreateRequest request) {
        String key=request.claveIdempotencia().trim();if(key.isEmpty()) throw bad("Clave inválida");
        Long actor=users.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autenticación requerida")).usuarioId();
        db.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(?, 6182050))",Integer.class,key);
        var existing=facturas.findByClaveIdempotencia(key);
        if(existing.isPresent()) {
            var invoice=existing.get();
            if(!invoice.getPedidoId().equals(request.pedidoId()) || !invoice.getEmitidaPorUsuarioId().equals(actor)) throw conflict("La clave corresponde a otro contenido");
            return new Resultado(response(invoice,detalles.findByFacturaVentaIdOrderByIdAsc(invoice.getId())),false);
        }
        PedidoVenta order=pedidos.bloquearPorId(request.pedidoId()).orElseThrow(FacturaService::missing);
        if(facturas.findByPedidoId(order.getId()).isPresent()) throw conflict("El pedido ya tiene factura");
        if(order.getEstado()!=EstadoPedidoVenta.DESPACHADO) throw conflict("El pedido debe estar totalmente despachado");
        var lines=lineasPedido.findByPedidoIdOrderByIdAsc(order.getId());
        if(lines.isEmpty() || lines.stream().anyMatch(l->l.getCantidadDespachada().compareTo(l.getCantidad())!=0 || l.getCantidadReservada().signum()!=0))
            throw conflict("El pedido no está completamente despachado");
        Boolean missingExits=db.queryForObject("""
            select exists(select 1 from detalles_pedido l where l.pedido_id=? and
              coalesce((select sum(d.cantidad) from detalles_despacho d join despachos s on s.id=d.despacho_id
                join movimientos_inventario m on m.detalle_despacho_id=d.id
                where d.detalle_pedido_id=l.id and s.pedido_id=l.pedido_id and s.estado='CONFIRMADO'),0)<>l.cantidad)
            """,Boolean.class,order.getId());
        if(Boolean.TRUE.equals(missingExits)) throw conflict("El pedido no tiene salidas confirmadas completas");
        CalculoCotizacion.Totales totals;
        try {totals=CalculoCotizacion.calcular(lines.stream().map(DetallePedido::lineaComercial).toList());}
        catch(IllegalArgumentException ex){throw conflict("Condiciones comerciales incompatibles");}
        if(totals.subtotal().compareTo(order.getSubtotal())!=0 || totals.impuestos().compareTo(order.getImpuestos())!=0 || totals.total().compareTo(order.getTotal())!=0)
            throw conflict("Totales comerciales incompatibles");
        long seq=Objects.requireNonNull(db.queryForObject("select nextval('factura_venta_folio_seq')",Long.class));
        if(seq>999999999999L) throw conflict("Secuencia de folios agotada");
        var invoice=facturas.save(new FacturaVenta("FAC-"+String.format(Locale.ROOT,"%012d",seq),order,actor,VigenciaCotizacion.fechaNegocio(clock.instant()),key));
        for(var line:lines) detalles.save(new DetalleFacturaVenta(invoice.getId(),line,CalculoCotizacion.calcular(List.of(line.lineaComercial()))));
        facturas.flush();
        invoice.emitir();facturas.flush();
        audit.change(AuditAction.SALES_INVOICE_ISSUED,invoice.getId());
        return new Resultado(response(invoice,detalles.findByFacturaVentaIdOrderByIdAsc(invoice.getId())),true);
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('VENTAS_FACTURAS_LEER')")
    public FacturaResponse obtener(Long id){validarId(id);var invoice=facturas.findById(id).orElseThrow(FacturaService::missing);
        return response(invoice,detalles.findByFacturaVentaIdOrderByIdAsc(id));}
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('VENTAS_FACTURAS_LEER')")
    public PageResponse<FacturaResponse> listar(int page,int size,Long clienteId){
        if(clienteId!=null)validarId(clienteId);
        var paging=PageRequests.of(page,size,"id","desc",Map.of("id","id"));
        var result=clienteId==null?facturas.findAll(paging):facturas.findByClienteId(clienteId,paging);
        var ids=result.getContent().stream().map(FacturaVenta::getId).toList();
        Map<Long,List<DetalleFacturaVenta>> grouped=ids.isEmpty()?Map.of():detalles.findByFacturaVentaIdInOrderByFacturaVentaIdAscIdAsc(ids)
            .stream().collect(Collectors.groupingBy(DetalleFacturaVenta::getFacturaVentaId));
        return PageResponse.from(result,f->response(f,grouped.getOrDefault(f.getId(),List.of())));
    }
    private static FacturaResponse response(FacturaVenta f,List<DetalleFacturaVenta> lines){
        return new FacturaResponse(f.getId(),f.getFolio(),f.getPedidoId(),f.getClienteId(),f.getEmitidaPorUsuarioId(),f.getFechaEmision(),
            f.getFechaVencimiento(),f.getSubtotal(),f.getImpuestos(),f.getTotal(),f.getEstado(),lines.stream().map(l->new FacturaResponse.Linea(
                l.getId(),l.getDetallePedidoId(),l.getProductoId(),l.getCantidad(),l.getPrecioUnitario(),l.getDescuentoPct(),l.getTasaImpuesto(),
                l.getSubtotal(),l.getImpuestos(),l.getTotal())).toList());
    }
    private static void validarId(Long id){if(id==null||id<1)throw bad("Identificador inválido");}
    private static ResponseStatusException bad(String text){return new ResponseStatusException(HttpStatus.BAD_REQUEST,text);}
    private static ResponseStatusException conflict(String text){return new ResponseStatusException(HttpStatus.CONFLICT,text);}
    private static ResponseStatusException missing(){return new ResponseStatusException(HttpStatus.NOT_FOUND,"Recurso no encontrado");}
}
