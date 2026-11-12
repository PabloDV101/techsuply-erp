package com.techsupply.erp_backend.ventas.application;

import com.techsupply.erp_backend.audit.application.*;
import com.techsupply.erp_backend.common.api.*;
import com.techsupply.erp_backend.inventario.application.InventarioService;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import com.techsupply.erp_backend.ventas.application.dto.*;
import com.techsupply.erp_backend.ventas.domain.model.*;
import com.techsupply.erp_backend.ventas.infrastructure.persistence.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.server.ResponseStatusException;

@Service @Validated
public class PedidoService {
    private final PedidoVentaRepository pedidos;
    private final DetallePedidoRepository detalles;
    private final CotizacionRepository cotizaciones;
    private final DetalleCotizacionRepository lineasCotizacion;
    private final JdbcTemplate db;
    private final NamedParameterJdbcTemplate namedDb;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    private final Clock clock;
    private final InventarioService inventario;
    private final EntityManager entityManager;
    public PedidoService(PedidoVentaRepository pedidos, DetallePedidoRepository detalles, CotizacionRepository cotizaciones,
            DetalleCotizacionRepository lineasCotizacion, JdbcTemplate db, NamedParameterJdbcTemplate namedDb,
            AuthenticatedUserProvider users, AuditTrail audit, Clock clock, InventarioService inventario, EntityManager entityManager) {
        this.pedidos=pedidos; this.detalles=detalles; this.cotizaciones=cotizaciones; this.lineasCotizacion=lineasCotizacion;
        this.db=db; this.namedDb=namedDb; this.users=users; this.audit=audit; this.clock=clock;
        this.inventario=inventario; this.entityManager=entityManager;
    }
    public record Resultado(PedidoResponse pedido, boolean creado) {}
    @Transactional @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_REGISTRAR')")
    public Resultado crear(@NotNull @Valid PedidoCreateRequest request) {
        String key=request.claveIdempotencia().trim();
        if (key.isEmpty()) throw bad("Clave inválida");
        Long actor=users.currentUser().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida")).usuarioId();
        db.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(?, 6182048))", Integer.class, key);
        var previous=pedidos.findByClaveIdempotencia(key);
        if (previous.isPresent()) {
            PedidoVenta p=previous.get();
            if (!p.getCreadoPorUsuarioId().equals(actor) || !p.getCotizacionId().equals(request.cotizacionId())
                    || !p.getAlmacenId().equals(request.almacenId()) || p.getModalidad()!=request.modalidad())
                throw conflict("La clave corresponde a otro contenido");
            return new Resultado(response(p,detalles.findByPedidoIdOrderByIdAsc(p.getId())),false);
        }
        Cotizacion quote=cotizaciones.bloquearPorId(request.cotizacionId()).orElseThrow(PedidoService::missing);
        if (pedidos.findByCotizacionId(quote.getId()).isPresent()) throw conflict("La cotización ya tiene un pedido");
        var instant=clock.instant();
        if (quote.getEstado()!=EstadoCotizacion.ACEPTADA || !VigenciaCotizacion.vigente(quote.getVigenciaHasta(),VigenciaCotizacion.fechaNegocio(instant)))
            throw conflict("La cotización debe estar aceptada y vigente");
        validarActivo("clientes",quote.getClienteId());
        validarActivo("almacenes",request.almacenId());
        var lines=lineasCotizacion.findByCotizacionIdOrderByIdAsc(quote.getId());
        if (lines.isEmpty()) throw conflict("Cotización sin líneas");
        var ids=lines.stream().map(DetalleCotizacion::getProductoId).sorted().toList();
        var products=namedDb.queryForList("select id, activo from productos where id in (:ids) order by id for share",Map.of("ids",ids));
        if (products.size()!=ids.size()) throw missing();
        if (products.stream().anyMatch(row -> !Boolean.TRUE.equals(row.get("activo")))) throw conflict("Producto inactivo");
        long seq=Objects.requireNonNull(db.queryForObject("select nextval('pedido_venta_folio_seq')",Long.class));
        if (seq>999999999999L) throw conflict("Secuencia de folios agotada");
        PedidoVenta p=pedidos.save(new PedidoVenta("PED-"+String.format(Locale.ROOT,"%012d",seq),quote,request.almacenId(),actor,instant,key));
        lines.forEach(line -> detalles.save(new DetallePedido(p.getId(),line)));
        pedidos.flush();
        audit.change(AuditAction.SALES_ORDER_CREATED,p.getId());
        return new Resultado(response(p,detalles.findByPedidoIdOrderByIdAsc(p.getId())),true);
    }
    private void validarActivo(String table, Long id) {
        // Table names are fixed server-owned constants, never external input.
        if (!Set.of("clientes","almacenes").contains(table)) throw new IllegalArgumentException();
        var rows=db.query("select activo from "+table+" where id=? for share",(rs,n)->rs.getBoolean(1),id);
        if (rows.isEmpty()) throw missing();
        if (!rows.getFirst()) throw conflict("Recurso inactivo");
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_LEER')")
    public PedidoResponse obtener(Long id) {
        validarId(id); var p=pedidos.findById(id).orElseThrow(PedidoService::missing);
        return response(p,detalles.findByPedidoIdOrderByIdAsc(id));
    }
    @Transactional(readOnly=true) @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_LEER')")
    public PageResponse<PedidoResponse> listar(int page,int size,Long clienteId) {
        if (clienteId!=null) validarId(clienteId);
        var paging=PageRequests.of(page,size,"id","desc",Map.of("id","id"));
        var result=clienteId==null ? pedidos.findAll(paging) : pedidos.findByClienteId(clienteId,paging);
        var ids=result.getContent().stream().map(PedidoVenta::getId).toList();
        Map<Long,List<DetallePedido>> grouped=ids.isEmpty()?Map.of():detalles.findByPedidoIdInOrderByPedidoIdAscIdAsc(ids)
            .stream().collect(Collectors.groupingBy(DetallePedido::getPedidoId));
        return PageResponse.from(result,p->response(p,grouped.getOrDefault(p.getId(),List.of())));
    }
    @Transactional @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_CONFIRMAR')")
    public PedidoResponse confirmar(Long id) {
        validarId(id);
        PedidoVenta pedido=pedidos.bloquearPorId(id).orElseThrow(PedidoService::missing);
        var lines=detalles.findByPedidoIdOrderByIdAsc(id);
        if (pedido.getEstado()==EstadoPedidoVenta.CONFIRMADO) return response(pedido,lines);
        if (pedido.getEstado()!=EstadoPedidoVenta.BORRADOR || lines.isEmpty() || pedido.getModalidad()!=ModalidadVenta.CONTADO)
            throw conflict("Estado de pedido incompatible");
        Cotizacion quote=cotizaciones.bloquearPorId(pedido.getCotizacionId()).orElseThrow(PedidoService::missing);
        if (quote.getEstado()!=EstadoCotizacion.ACEPTADA || !VigenciaCotizacion.vigente(quote.getVigenciaHasta(),VigenciaCotizacion.fechaNegocio(clock.instant())))
            throw conflict("La cotización debe estar aceptada y vigente");
        validarActivo("clientes",pedido.getClienteId());
        validarActivo("almacenes",pedido.getAlmacenId());
        validarCondiciones(pedido,quote,lines,lineasCotizacion.findByCotizacionIdOrderByIdAsc(quote.getId()));
        for (DetallePedido line:lines.stream().sorted(Comparator.comparing(DetallePedido::getProductoId)).toList()) {
            inventario.reservarLineaPedido(line.getId());
            entityManager.refresh(line);
        }
        pedido.confirmar();
        pedidos.flush();
        audit.change(AuditAction.SALES_ORDER_CONFIRMED,id);
        return response(pedido,lines);
    }
    @Transactional @PreAuthorize("hasAuthority('VENTAS_PEDIDOS_CANCELAR')")
    public PedidoResponse cancelar(Long id, @NotNull @Valid CancelarPedidoRequest request) {
        validarId(id);
        String motivo=request.motivo().trim();
        if (motivo.isEmpty()) throw bad("Motivo inválido");
        PedidoVenta pedido=pedidos.bloquearPorId(id).orElseThrow(PedidoService::missing);
        var lines=detalles.findByPedidoIdOrderByIdAsc(id);
        if (pedido.getEstado()==EstadoPedidoVenta.CANCELADO) {
            if (!motivo.equals(pedido.getMotivoCancelacion())) throw conflict("El pedido fue cancelado con otro motivo o sin metadatos");
            return response(pedido,lines);
        }
        if (pedido.getEstado()!=EstadoPedidoVenta.BORRADOR && pedido.getEstado()!=EstadoPedidoVenta.CONFIRMADO
                && pedido.getEstado()!=EstadoPedidoVenta.PARCIALMENTE_DESPACHADO) throw conflict("El pedido no se puede cancelar");
        if (Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from despachos where pedido_id=? and estado='BORRADOR')",Boolean.class,id)))
            throw conflict("Cancele primero los despachos en borrador");
        if (lines.isEmpty()) throw conflict("Pedido sin líneas");
        boolean draft=pedido.getEstado()==EstadoPedidoVenta.BORRADOR;
        for (var line:lines) {
            if (draft ? line.getCantidadReservada().signum()!=0 || line.getCantidadDespachada().signum()!=0
                    : line.getCantidadReservada().compareTo(line.pendiente())!=0)
                throw conflict("Reserva o cantidades incompatibles");
        }
        for (var line:lines.stream().filter(l->l.getCantidadReservada().signum()>0).sorted(Comparator.comparing(DetallePedido::getProductoId)).toList()) {
            inventario.liberarReservaPedido(line.getId());
            entityManager.refresh(line);
        }
        Long actor=users.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autenticación requerida")).usuarioId();
        AuditState before=AuditState.valueOf(pedido.getEstado().name());
        pedido.cancelar(motivo,actor,clock.instant());
        pedidos.flush();
        audit.change(AuditAction.SALES_ORDER_CANCELLED,id,Set.of(AuditField.ESTADO),before,AuditState.CANCELADO);
        return response(pedido,lines);
    }
    private static void validarCondiciones(PedidoVenta pedido,Cotizacion quote,List<DetallePedido> lines,List<DetalleCotizacion> quoted) {
        if (!pedido.getClienteId().equals(quote.getClienteId()) || !pedido.getVendedorUsuarioId().equals(quote.getVendedorUsuarioId())
                || lines.size()!=quoted.size() || quoted.isEmpty()) throw conflict("Condiciones comerciales incompatibles");
        var byProduct=quoted.stream().collect(Collectors.toMap(DetalleCotizacion::getProductoId,x->x));
        for (var line:lines) {
            var origin=byProduct.get(line.getProductoId());
            if (origin==null || line.getCantidadReservada().signum()!=0 || line.getCantidadDespachada().signum()!=0
                    || line.getCantidad().compareTo(origin.getCantidad())!=0 || line.getPrecioUnitario().compareTo(origin.getPrecioUnitario())!=0
                    || line.getDescuentoPct().compareTo(origin.getDescuentoPct())!=0 || line.getTasaImpuesto().compareTo(origin.getTasaImpuesto())!=0)
                throw conflict("Condiciones comerciales incompatibles");
        }
        try {
            var totals=CalculoCotizacion.calcular(lines.stream().map(DetallePedido::lineaComercial).toList());
            if (totals.subtotal().compareTo(quote.getSubtotal())!=0 || totals.impuestos().compareTo(quote.getImpuestos())!=0
                    || totals.total().compareTo(quote.getTotal())!=0 || totals.subtotal().compareTo(pedido.getSubtotal())!=0
                    || totals.impuestos().compareTo(pedido.getImpuestos())!=0 || totals.total().compareTo(pedido.getTotal())!=0)
                throw conflict("Totales comerciales incompatibles");
        } catch (IllegalArgumentException ex) { throw conflict("Condiciones comerciales incompatibles"); }
    }
    private static PedidoResponse response(PedidoVenta p,List<DetallePedido> lines) {
        return new PedidoResponse(p.getId(),p.getFolio(),p.getCotizacionId(),p.getClienteId(),p.getAlmacenId(),p.getCreadoPorUsuarioId(),p.getVendedorUsuarioId(),
            p.getModalidad(),p.getEstado(),p.getFecha(),p.getSubtotal(),p.getImpuestos(),p.getTotal(),lines.stream().map(l->
                new PedidoResponse.Linea(l.getId(),l.getProductoId(),l.getCantidad(),l.getPrecioUnitario(),l.getDescuentoPct(),l.getTasaImpuesto(),l.getCantidadReservada(),l.getCantidadDespachada())).toList(),
            p.getMotivoCancelacion(),p.getCanceladoPorUsuarioId(),p.getCanceladoEn());
    }
    private static void validarId(Long id) { if (id==null || id<1) throw bad("Identificador inválido"); }
    private static ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,message); }
    private static ResponseStatusException conflict(String message) { return new ResponseStatusException(HttpStatus.CONFLICT,message); }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND,"Recurso no encontrado"); }
}
