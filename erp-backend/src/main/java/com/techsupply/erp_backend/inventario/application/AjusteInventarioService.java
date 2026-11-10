package com.techsupply.erp_backend.inventario.application;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditField;
import com.techsupply.erp_backend.audit.application.AuditState;
import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.dto.AjusteResponse;
import com.techsupply.erp_backend.inventario.application.dto.CrearAjusteRequest;
import com.techsupply.erp_backend.inventario.application.dto.RechazarAjusteRequest;
import com.techsupply.erp_backend.inventario.domain.model.*;
import com.techsupply.erp_backend.inventario.infrastructure.persistence.AjusteInventarioRepository;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AjusteInventarioService {
    private final AjusteInventarioRepository adjustments;
    private final JdbcTemplate db;
    private final EntityManager em;
    private final AuthenticatedUserProvider users;
    private final AuditTrail audit;
    private final InventarioService inventory;
    public AjusteInventarioService(AjusteInventarioRepository adjustments, JdbcTemplate db, EntityManager em, AuthenticatedUserProvider users, AuditTrail audit, InventarioService inventory) {
        this.adjustments=adjustments; this.db=db; this.em=em; this.users=users; this.audit=audit; this.inventory=inventory;
    }
    public record Resultado(AjusteResponse ajuste, boolean creado) {}
    @Transactional @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_REGISTRAR')")
    public Resultado crear(CrearAjusteRequest request) {
        String key=request.claveIdempotencia().trim(), reason=request.motivo().trim();
        if (key.isEmpty() || reason.isEmpty()) throw bad("Clave o motivo inválido");
        List<Long> products=request.detalles().stream().map(CrearAjusteRequest.Linea::productoId).toList();
        if (new HashSet<>(products).size()!=products.size()) throw conflict("Producto duplicado");
        for (var line:request.detalles()) {
            if (line.tipo()==TipoAjuste.POSITIVO && line.costoUnitario()==null || line.tipo()==TipoAjuste.NEGATIVO && line.costoUnitario()!=null)
                throw bad("Costo incompatible con tipo");
        }
        // Lock the globally unique normalized key even when no row exists yet.
        db.queryForObject("select 1 from pg_advisory_xact_lock(hashtextextended(?, 4610119))", Integer.class, key);
        var existing=adjustments.findByClaveIdempotencia(key);
        if (existing.isPresent()) {
            if (!equivalente(existing.get(),request)) throw conflict("La clave ya corresponde a otro contenido");
            return new Resultado(response(existing.get()),false);
        }
        activo("almacenes",request.almacenId());
        for (Long product:products.stream().sorted().toList()) activo("productos",product);
        for (var line:request.detalles().stream().filter(l->l.detalleConteoId()!=null).sorted(Comparator.comparing(CrearAjusteRequest.Linea::detalleConteoId)).toList())
            validarConteo(request.almacenId(),line);
        long seq=Objects.requireNonNull(db.queryForObject("select nextval('ajuste_inventario_folio_seq')",Long.class));
        if(seq>999999999999L) throw conflict("Secuencia de folios agotada");
        Long actor=users.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autenticación requerida")).usuarioId();
        AjusteInventario adjustment=new AjusteInventario("AI-"+String.format(Locale.ROOT,"%012d",seq),request.almacenId(),reason,actor,key,Instant.now());
        try {
            for(var line:request.detalles()) adjustment.agregarDetalle(line.productoId(),line.tipo(),line.cantidad(),line.costoUnitario(),line.detalleConteoId(),optional(line.motivoLinea()));
        } catch(IllegalArgumentException ex) { throw bad("Línea de ajuste inválida"); }
        adjustments.save(adjustment); em.flush();
        audit.change(AuditAction.INVENTORY_ADJUSTMENT_CREATED,adjustment.getId());
        return new Resultado(response(adjustment),true);
    }
    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_LEER')")
    public AjusteResponse obtener(Long id) { return response(adjustments.findById(validarId(id)).orElseThrow(()->missing("Ajuste"))); }
    @Transactional(readOnly = true) @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_LEER')")
    public PageResponse<AjusteResponse> listar(int page,int size,Long almacenId,String estado) {
        if(almacenId!=null) validarId(almacenId);
        EstadoAjuste filter=null;
        if(estado!=null) { try { filter=EstadoAjuste.valueOf(estado); } catch(IllegalArgumentException ex) { throw bad("Estado inválido"); } }
        Pageable paging=PageRequests.of(page,size,"id","desc",Map.of("id","id"));
        Page<AjusteInventario> result;
        if(almacenId!=null && filter!=null) result=adjustments.findByAlmacenIdAndEstado(almacenId,filter,paging);
        else if(almacenId!=null) result=adjustments.findByAlmacenId(almacenId,paging);
        else if(filter!=null) result=adjustments.findByEstado(filter,paging);
        else result=adjustments.findAll(paging);
        if(!result.isEmpty()) adjustments.conDetalles(result.getContent().stream().map(AjusteInventario::getId).toList());
        return PageResponse.from(result,AjusteInventarioService::response);
    }
    @Transactional @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_APROBAR')")
    public AjusteResponse aprobar(Long id) { return autorizar(id, true, null); }
    @Transactional @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_APROBAR')")
    public AjusteResponse rechazar(Long id, RechazarAjusteRequest request) {
        if(request==null || request.motivo()==null || request.motivo().isBlank() || request.motivo().length()>1000)
            throw bad("Motivo de rechazo inválido");
        return autorizar(id, false, request.motivo());
    }
    private AjusteResponse autorizar(Long id, boolean approved, String motivoRechazo) {
        AjusteInventario adjustment=adjustments.bloquearPorId(validarId(id)).orElseThrow(()->missing("Ajuste"));
        Long actor=users.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autenticación requerida")).usuarioId();
        try {
            if(approved) adjustment.aprobar(actor,Instant.now());
            else adjustment.rechazar(actor,Instant.now(),motivoRechazo);
        } catch(IllegalStateException | IllegalArgumentException ex) { throw conflict("Transición de ajuste no permitida"); }
        em.flush();
        audit.change(approved ? AuditAction.INVENTORY_ADJUSTMENT_APPROVED : AuditAction.INVENTORY_ADJUSTMENT_REJECTED,
            adjustment.getId(),Set.of(AuditField.ESTADO),AuditState.PENDIENTE,approved ? AuditState.APROBADO : AuditState.RECHAZADO);
        return response(adjustment);
    }
    @Transactional @PreAuthorize("hasAuthority('INVENTARIO_AJUSTES_APLICAR')")
    public AjusteResponse aplicar(Long id) {
        AjusteInventario adjustment=adjustments.bloquearPorId(validarId(id)).orElseThrow(()->missing("Ajuste"));
        if (adjustment.getEstado()==EstadoAjuste.APLICADO) return response(adjustment);
        if (adjustment.getEstado()!=EstadoAjuste.APROBADO || adjustment.getDetalles().isEmpty()) throw conflict("Ajuste no aplicable");
        for (DetalleAjusteInventario line:adjustment.getDetalles().stream().sorted(Comparator.comparing(DetalleAjusteInventario::getProductoId)).toList()) {
            BigDecimal cost=inventory.registrarAjuste(line.getId());
            if (line.getTipo()==TipoAjuste.NEGATIVO) adjustment.fijarCostoSalida(line.getId(),cost);
        }
        // Persist exit costs while the parent is still approved; V10 freezes applied details.
        em.flush();
        adjustment.marcarAplicado(Instant.now()); em.flush();
        audit.change(AuditAction.INVENTORY_ADJUSTMENT_APPLIED,adjustment.getId(),Set.of(AuditField.ESTADO),AuditState.APROBADO,AuditState.APLICADO);
        return response(adjustment);
    }
    private void validarConteo(Long almacen,CrearAjusteRequest.Linea line) {
        var found=db.query("select c.estado,c.almacen_id,d.producto_id,d.diferencia from detalles_conteo d join conteos_inventario c on c.id=d.conteo_id where d.id=? for share of c,d",
            (rs,row)->new Referencia(rs.getString("estado"),rs.getLong("almacen_id"),rs.getLong("producto_id"),rs.getBigDecimal("diferencia")),line.detalleConteoId());
        if(found.isEmpty()) throw missing("Detalle de conteo");
        Referencia ref=found.getFirst();
        boolean positive=ref.diferencia().signum()>0, negative=ref.diferencia().signum()<0;
        if(!ref.estado().equals("CERRADO") || !Objects.equals(ref.almacenId(),almacen) || !Objects.equals(ref.productoId(),line.productoId())
            || !(positive && line.tipo()==TipoAjuste.POSITIVO || negative && line.tipo()==TipoAjuste.NEGATIVO)
            || ref.diferencia().abs().compareTo(line.cantidad())!=0) throw conflict("El detalle no coincide con el conteo cerrado");
    }
    private void activo(String table,Long id) {
        validarId(id);
        String sql=switch(table) { case "almacenes" -> "select activo from almacenes where id=? for share"; case "productos" -> "select activo from productos where id=? for share"; default -> throw new IllegalArgumentException("Catálogo no admitido"); };
        List<Boolean> rows=db.query(sql,(rs,row)->rs.getBoolean(1),id);
        if(rows.isEmpty()) throw missing("Referencia"); if(!rows.getFirst()) throw conflict("Referencia inactiva");
    }
    private static boolean equivalente(AjusteInventario old,CrearAjusteRequest request) {
        if(!old.getAlmacenId().equals(request.almacenId()) || !old.getMotivo().equals(request.motivo().trim()) || old.getDetalles().size()!=request.detalles().size()) return false;
        var lines=old.getDetalles().stream().collect(Collectors.toMap(DetalleAjusteInventario::getProductoId,d->d));
        return request.detalles().stream().allMatch(line->{
            var saved=lines.get(line.productoId());
            return saved!=null && saved.getTipo()==line.tipo() && saved.getCantidad().compareTo(line.cantidad())==0
                && (line.tipo()==TipoAjuste.NEGATIVO || saved.getCostoUnitario()!=null && saved.getCostoUnitario().compareTo(line.costoUnitario())==0)
                && Objects.equals(saved.getDetalleConteoId(),line.detalleConteoId()) && Objects.equals(saved.getMotivoLinea(),optional(line.motivoLinea()));
        });
    }
    private static AjusteResponse response(AjusteInventario a) {
        var lines=a.getDetalles().stream().sorted(Comparator.comparing(DetalleAjusteInventario::getProductoId)).map(d->new AjusteResponse.Linea(
            d.getId(),d.getProductoId(),d.getTipo(),d.getCantidad(),d.getCostoUnitario(),d.getDetalleConteoId(),d.getMotivoLinea())).toList();
        return new AjusteResponse(a.getId(),a.getFolio(),a.getAlmacenId(),a.getMotivo(),a.getSolicitadoPorUsuarioId(),a.getAutorizadoPorUsuarioId(),
            a.getClaveIdempotencia(),a.getEstado(),a.getMotivoRechazo(),a.getFecha(),a.getAutorizadoEn(),a.getAplicadoEn(),lines);
    }
    private static String optional(String s) { if(s==null) return null; String t=s.trim(); return t.isEmpty()?null:t; }
    private static Long validarId(Long id) { if(id==null || id<1) throw bad("Identificador inválido"); return id; }
    private record Referencia(String estado,Long almacenId,Long productoId,BigDecimal diferencia) {}
    private static ResponseStatusException bad(String m) { return new ResponseStatusException(HttpStatus.BAD_REQUEST,m); }
    private static ResponseStatusException missing(String m) { return new ResponseStatusException(HttpStatus.NOT_FOUND,m); }
    private static ResponseStatusException conflict(String m) { return new ResponseStatusException(HttpStatus.CONFLICT,m); }
}
