package com.techsupply.erp_backend.ventas.application;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.audit.application.AuditField;
import com.techsupply.erp_backend.audit.application.AuditState;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.InventarioService;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import com.techsupply.erp_backend.ventas.application.dto.*;
import com.techsupply.erp_backend.ventas.domain.model.*;
import com.techsupply.erp_backend.ventas.infrastructure.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
public class DespachoService {
    private final DespachoRepository despachos;
    private final DetalleDespachoRepository detalles;
    private final PedidoVentaRepository pedidos;
    private final DetallePedidoRepository lineasPedido;
    private final JdbcTemplate db;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    private final Clock clock;
    private final InventarioService inventario;
    private final EntityManager entityManager;
    public DespachoService(DespachoRepository despachos, DetalleDespachoRepository detalles, PedidoVentaRepository pedidos,
            DetallePedidoRepository lineasPedido, JdbcTemplate db, AuthenticatedUserProvider users, AuditTrail audit, Clock clock,
            InventarioService inventario, EntityManager entityManager) {
        this.despachos=despachos; this.detalles=detalles; this.pedidos=pedidos; this.lineasPedido=lineasPedido;
        this.db=db; this.users=users; this.audit=audit; this.clock=clock;
        this.inventario=inventario; this.entityManager=entityManager;
    }
    public record Resultado(DespachoResponse despacho,boolean creado) {}
    @Transactional @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_REGISTRAR')")
    public Resultado crear(@NotNull @Valid DespachoCreateRequest request) {
        String key=request.claveIdempotencia().trim();
        if (key.isEmpty()) throw bad("Clave inválida");
        List<Long> ids=request.detalles().stream().map(DespachoCreateRequest.Linea::detallePedidoId).toList();
        if (new HashSet<>(ids).size()!=ids.size()) throw conflict("Línea de pedido duplicada");
        Long actor=users.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autenticación requerida")).usuarioId();
        db.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(?, 6182049))",Integer.class,key);
        var existing=despachos.findByClaveIdempotencia(key);
        if (existing.isPresent()) {
            Despacho prior=existing.get(); var lines=detalles.findByDespachoIdOrderByIdAsc(prior.getId());
            if (!equivalente(prior,lines,request,actor)) throw conflict("La clave corresponde a otro contenido");
            return new Resultado(response(prior,lines),false);
        }
        PedidoVenta pedido=pedidos.bloquearPorId(request.pedidoId()).orElseThrow(DespachoService::missing);
        if (pedido.getEstado()!=EstadoPedidoVenta.CONFIRMADO && pedido.getEstado()!=EstadoPedidoVenta.PARCIALMENTE_DESPACHADO)
            throw conflict("El pedido debe estar confirmado o parcialmente despachado");
        var byId=lineasPedido.findByPedidoIdOrderByIdAsc(pedido.getId()).stream().collect(Collectors.toMap(DetallePedido::getId,x->x));
        validarLineas(request,byId);
        long seq=Objects.requireNonNull(db.queryForObject("select nextval('despacho_folio_seq')",Long.class));
        if(seq>999999999999L) throw conflict("Secuencia de folios agotada");
        Despacho dispatch=despachos.save(new Despacho("DES-"+String.format(Locale.ROOT,"%012d",seq),pedido.getId(),pedido.getAlmacenId(),actor,clock.instant(),key));
        for(var line:request.detalles()) detalles.save(new DetalleDespacho(dispatch.getId(),line.detallePedidoId(),line.cantidad()));
        despachos.flush();
        audit.change(AuditAction.SALES_DISPATCH_CREATED,dispatch.getId());
        return new Resultado(response(dispatch,detalles.findByDespachoIdOrderByIdAsc(dispatch.getId())),true);
    }
    private void validarLineas(DespachoCreateRequest request,Map<Long,DetallePedido> byId) {
        Map<Long,BigDecimal> assignedByLine=new HashMap<>();
        db.query("""
            select d.detalle_pedido_id,sum(d.cantidad) as asignada
            from detalles_despacho d join despachos h on h.id=d.despacho_id
            where h.pedido_id=? and h.estado='BORRADOR' group by d.detalle_pedido_id
            """,rs-> { assignedByLine.put(rs.getLong("detalle_pedido_id"),rs.getBigDecimal("asignada")); },request.pedidoId());
        for(var line:request.detalles()) {
            var origin=byId.get(line.detallePedidoId());
            if (origin==null) throw conflict("La línea no pertenece al pedido");
            BigDecimal assigned=assignedByLine.getOrDefault(line.detallePedidoId(),BigDecimal.ZERO);
            BigDecimal available=origin.getCantidadReservada().min(origin.pendiente()).subtract(assigned);
            if (line.cantidad().compareTo(available)>0)
                throw conflict("Cantidad superior a la reserva disponible para despachos");
        }
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_LEER')")
    public DespachoResponse obtener(Long id) {
        validarId(id); Despacho dispatch=despachos.findById(id).orElseThrow(DespachoService::missing);
        return response(dispatch,detalles.findByDespachoIdOrderByIdAsc(id));
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_LEER')")
    public PageResponse<DespachoResponse> listar(int page,int size,Long pedidoId) {
        if (pedidoId!=null) validarId(pedidoId);
        var paging=PageRequests.of(page,size,"id","desc",Map.of("id","id"));
        var result=pedidoId==null?despachos.findAll(paging):despachos.findByPedidoId(pedidoId,paging);
        var ids=result.getContent().stream().map(Despacho::getId).toList();
        Map<Long,List<DetalleDespacho>> grouped=ids.isEmpty()?Map.of():detalles.findByDespachoIdInOrderByDespachoIdAscIdAsc(ids)
            .stream().collect(Collectors.groupingBy(DetalleDespacho::getDespachoId));
        return PageResponse.from(result,d->response(d,grouped.getOrDefault(d.getId(),List.of())));
    }
    @Transactional @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_CONFIRMAR')")
    public DespachoResponse confirmar(Long id) {
        validarId(id);
        var pedidoIds=db.query("select pedido_id from despachos where id=?",(rs,row)->rs.getLong(1),id);
        if (pedidoIds.isEmpty()) throw missing();
        PedidoVenta pedido=pedidos.bloquearPorId(pedidoIds.getFirst()).orElseThrow(DespachoService::missing);
        Despacho despacho=despachos.bloquearPorId(id).orElseThrow(DespachoService::missing);
        if (!despacho.getPedidoId().equals(pedido.getId())) throw conflict("Pedido de despacho incompatible");
        var lines=detalles.findByDespachoIdOrderByIdAsc(id);
        if (despacho.getEstado()==EstadoDespacho.CONFIRMADO) return response(despacho,lines);
        if (despacho.getEstado()!=EstadoDespacho.BORRADOR || lines.isEmpty()
                || (pedido.getEstado()!=EstadoPedidoVenta.CONFIRMADO && pedido.getEstado()!=EstadoPedidoVenta.PARCIALMENTE_DESPACHADO)
                || !pedido.getAlmacenId().equals(despacho.getAlmacenId())) throw conflict("Estado de despacho o pedido incompatible");
        var byId=lineasPedido.findByPedidoIdOrderByIdAsc(pedido.getId()).stream().collect(Collectors.toMap(DetallePedido::getId,x->x));
        if (lines.stream().anyMatch(line->!byId.containsKey(line.getDetallePedidoId()))) throw conflict("Línea de pedido incompatible");
        for (var line:lines.stream().sorted(Comparator.comparing(line->byId.get(line.getDetallePedidoId()).getProductoId())).toList()) {
            inventario.registrarSalidaDespacho(line.getId());
            entityManager.refresh(line);
        }
        Boolean pending=db.queryForObject("select exists(select 1 from detalles_pedido where pedido_id=? and cantidad_despachada<cantidad)",Boolean.class,pedido.getId());
        pedido.registrarDespacho(!Boolean.TRUE.equals(pending));
        despacho.confirmar();
        pedidos.flush();
        despachos.flush();
        audit.change(AuditAction.SALES_DISPATCH_CONFIRMED,id);
        return response(despacho,lines);
    }
    @Transactional @PreAuthorize("hasAuthority('VENTAS_DESPACHOS_REGISTRAR')")
    public DespachoResponse cancelar(Long id) {
        validarId(id);
        var pedidoIds=db.query("select pedido_id from despachos where id=?",(rs,row)->rs.getLong(1),id);
        if (pedidoIds.isEmpty()) throw missing();
        PedidoVenta pedido=pedidos.bloquearPorId(pedidoIds.getFirst()).orElseThrow(DespachoService::missing);
        Despacho despacho=despachos.bloquearPorId(id).orElseThrow(DespachoService::missing);
        if (!despacho.getPedidoId().equals(pedido.getId())) throw conflict("Pedido de despacho incompatible");
        var lines=detalles.findByDespachoIdOrderByIdAsc(id);
        if (despacho.getEstado()==EstadoDespacho.CANCELADO) return response(despacho,lines);
        try { despacho.cancelar(); }
        catch (IllegalStateException ex) { throw conflict("El despacho no se puede cancelar"); }
        despachos.flush();
        audit.change(AuditAction.SALES_DISPATCH_CANCELLED,id,Set.of(AuditField.ESTADO),AuditState.BORRADOR,AuditState.CANCELADO);
        return response(despacho,lines);
    }
    private static boolean equivalente(Despacho d,List<DetalleDespacho> lines,DespachoCreateRequest req,Long actor) {
        if (!d.getPedidoId().equals(req.pedidoId()) || !d.getRegistradoPorUsuarioId().equals(actor) || lines.size()!=req.detalles().size()) return false;
        var byLine=lines.stream().collect(Collectors.toMap(DetalleDespacho::getDetallePedidoId,x->x));
        return req.detalles().stream().allMatch(line->{ var original=byLine.get(line.detallePedidoId());
            return original!=null && original.getCantidad().compareTo(line.cantidad())==0; });
    }
    private static DespachoResponse response(Despacho d,List<DetalleDespacho> lines) {
        return new DespachoResponse(d.getId(),d.getFolio(),d.getPedidoId(),d.getAlmacenId(),d.getRegistradoPorUsuarioId(),d.getFecha(),d.getEstado(),
            lines.stream().map(l->new DespachoResponse.Linea(l.getId(),l.getDetallePedidoId(),l.getCantidad(),l.getCostoUnitario())).toList());
    }
    private static void validarId(Long id) { if(id==null || id<1) throw bad("Identificador inválido"); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Recurso no encontrado"); }
}
