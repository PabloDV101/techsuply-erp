package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
import com.techsupply.erp_backend.contabilidad.domain.model.*;
import com.techsupply.erp_backend.contabilidad.infrastructure.persistence.*;
import com.techsupply.erp_backend.security.application.AuthenticatedActor;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import com.techsupply.erp_backend.security.infrastructure.persistence.UsuarioRepository;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ContabilidadService {
    private static final Map<String,String> SORT=Map.of("id","id","codigo","codigo","folio","folio","fechaContable","fechaContable");
    private final CuentaContableRepository cuentas;
    private final PeriodoContableRepository periodos;
    private final AsientoContableRepository asientos;
    private final UsuarioRepository usuarios;
    private final AuthenticatedUserProvider actorProvider;
    private final EntityManager em;

    public ContabilidadService(CuentaContableRepository cuentas,PeriodoContableRepository periodos,AsientoContableRepository asientos,
            UsuarioRepository usuarios,AuthenticatedUserProvider actorProvider,EntityManager em){
        this.cuentas=cuentas;this.periodos=periodos;this.asientos=asientos;this.usuarios=usuarios;this.actorProvider=actorProvider;this.em=em;
    }

    @Transactional public CuentaContableResponse crearCuenta(CuentaContableRequest r){
        String codigo=r.codigo().trim().toUpperCase(Locale.ROOT);
        if(cuentas.existsByCodigoIgnoreCase(codigo)) throw conflicto("El código de cuenta ya existe");
        CuentaContable c=new CuentaContable(); c.setCodigo(codigo); c.setNombre(r.nombre().trim()); c.setTipo(r.tipo().toUpperCase(Locale.ROOT)); c.setNaturaleza(r.naturaleza().toUpperCase(Locale.ROOT)); c.setNivel(r.nivel()==0?1:r.nivel()); c.setAceptaMovimientos(r.aceptaMovimientos());
        if(r.cuentaPadreId()!=null){CuentaContable padre=cuentas.findById(r.cuentaPadreId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cuenta padre no encontrada"));if(!padre.isActiva())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La cuenta padre está inactiva");c.setCuentaPadre(padre);}
        return cuentaResponse(cuentas.saveAndFlush(c));
    }
    @Transactional public CuentaContableResponse actualizarCuenta(Long id,CuentaContableRequest r){
        CuentaContable c=cuentas.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cuenta contable no encontrada"));
        String codigo=r.codigo().trim().toUpperCase(Locale.ROOT);if(cuentas.existsByCodigoIgnoreCaseAndIdNot(codigo,id))throw conflicto("El código de cuenta ya existe");
        c.setCodigo(codigo);c.setNombre(r.nombre().trim());c.setTipo(r.tipo().toUpperCase(Locale.ROOT));c.setNaturaleza(r.naturaleza().toUpperCase(Locale.ROOT));c.setNivel(r.nivel()==0?1:r.nivel());c.setAceptaMovimientos(r.aceptaMovimientos());
        if(r.cuentaPadreId()!=null){CuentaContable padre=cuentas.findById(r.cuentaPadreId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cuenta padre no encontrada"));if(padre.getId().equals(id))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Una cuenta no puede ser su propia padre");if(!padre.isActiva())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La cuenta padre está inactiva");c.setCuentaPadre(padre);}else c.setCuentaPadre(null);
        return cuentaResponse(cuentas.saveAndFlush(c));
    }
    @Transactional(readOnly=true) public java.util.List<CuentaContableResponse> listarCuentas(){return cuentas.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.ASC,"codigo")).stream().map(ContabilidadService::cuentaResponse).toList();}
    @Transactional public CuentaContableResponse estadoCuenta(Long id,boolean activa){CuentaContable c=cuentas.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cuenta contable no encontrada"));c.setActiva(activa);return cuentaResponse(cuentas.saveAndFlush(c));}

    @Transactional public PeriodoContableResponse crearPeriodo(PeriodoContableRequest r){
        if(r.fechaFin().isBefore(r.fechaInicio()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La fecha final no puede ser anterior a la inicial");
        if(periodos.existsByFechaInicioLessThanEqualAndFechaFinGreaterThanEqual(r.fechaFin(),r.fechaInicio()))throw conflicto("El periodo contable se traslapa con otro periodo");
        PeriodoContable p=new PeriodoContable();p.setFechaInicio(r.fechaInicio());p.setFechaFin(r.fechaFin());p.setEstado("ABIERTO");return periodoResponse(periodos.saveAndFlush(p));
    }
    @Transactional(readOnly=true) public java.util.List<PeriodoContableResponse> listarPeriodos(){return periodos.findAll(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"fechaInicio")).stream().map(ContabilidadService::periodoResponse).toList();}
    @Transactional public PeriodoContableResponse cerrarPeriodo(Long id){
        PeriodoContable p=periodos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Periodo contable no encontrado"));
        if("CERRADO".equals(p.getEstado()))return periodoResponse(p);
        p.setEstado("CERRADO");p.setCerradoPor(usuarioActual());p.setCerradoEn(Instant.now());return periodoResponse(periodos.saveAndFlush(p));
    }

    @Transactional public AsientoContableResponse crearAsiento(AsientoContableRequest r){
        if(r.claveIdempotencia()!=null&&!r.claveIdempotencia().isBlank()){
            var existente=asientos.findByClaveIdempotencia(r.claveIdempotencia().trim());
            if(existente.isPresent()){
                if(!coincide(existente.get(),r))throw conflicto("La clave de idempotencia ya se usó con otra solicitud");
                return asientoResponse(existente.get());
            }
        }
        PeriodoContable periodo=periodos.abiertoEn(r.fechaContable()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"La fecha no pertenece a un periodo contable abierto"));
        if(r.partidas()==null||r.partidas().size()<2)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El asiento requiere al menos dos partidas");
        AsientoContable a=new AsientoContable();a.setFolio("AS-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT));a.setPeriodo(periodo);a.setFechaContable(r.fechaContable());a.setDescripcion(r.descripcion().trim());a.setOrigenModulo(r.origenModulo().trim().toUpperCase(Locale.ROOT));a.setOrigenTipo(r.origenTipo().trim().toUpperCase(Locale.ROOT));a.setOrigenId(r.origenId());a.setClaveIdempotencia(v(r.claveIdempotencia()));a.setCreadoPor(usuarioActual());a.setEstado("BORRADOR");
        short linea=1;for(var pr:r.partidas()){CuentaContable cuenta=cuentas.findById(pr.cuentaId()).orElseThrow(()->new ResponseStatusException(HttpStatus.BAD_REQUEST,"Cuenta contable no encontrada"));if(!cuenta.isActiva()||!cuenta.isAceptaMovimientos())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"La cuenta no acepta movimientos");if(pr.debe().signum()<0||pr.haber().signum()<0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Los importes no pueden ser negativos");if(pr.debe().signum()>0&&pr.haber().signum()>0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Una partida no puede tener debe y haber");if(pr.debe().signum()==0&&pr.haber().signum()==0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Una partida debe tener un importe distinto de cero");PartidaAsiento p=new PartidaAsiento();p.setAsiento(a);p.setNumeroLinea(linea++);p.setCuenta(cuenta);p.setDebe(pr.debe());p.setHaber(pr.haber());p.setDescripcion(v(pr.descripcion()));a.getPartidas().add(p);}
        validarBalance(a);
        return asientoResponse(asientos.saveAndFlush(a));
    }
    @Transactional public AsientoContableResponse contabilizar(Long id){
        AsientoContable a=asientos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Asiento contable no encontrado"));
        if("CONTABILIZADO".equals(a.getEstado()))return asientoResponse(a);
        if(!"BORRADOR".equals(a.getEstado()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Solo puede contabilizarse un asiento borrador");
        if(!"ABIERTO".equals(a.getPeriodo().getEstado())||a.getFechaContable().isBefore(a.getPeriodo().getFechaInicio())||a.getFechaContable().isAfter(a.getPeriodo().getFechaFin()))throw new ResponseStatusException(HttpStatus.CONFLICT,"El asiento no pertenece a un periodo abierto");
        validarBalance(a);for(var p:a.getPartidas()){if(!p.getCuenta().isActiva()||!p.getCuenta().isAceptaMovimientos())throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Hay una cuenta inactiva o que no acepta movimientos");}
        a.setEstado("CONTABILIZADO");a.setContabilizadoPor(usuarioActual());a.setContabilizadoEn(Instant.now());return asientoResponse(asientos.saveAndFlush(a));
    }
    @Transactional public AsientoContableResponse revertir(Long id){
        AsientoContable original=asientos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Asiento contable no encontrado"));
        if(!"CONTABILIZADO".equals(original.getEstado()))throw new ResponseStatusException(HttpStatus.CONFLICT,"Solo se reversa un asiento contabilizado");
        if(original.getContraasientoDe()!=null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Este asiento ya es un contraasiento");
        String clave="REV:"+id;
        var existente=asientos.findByClaveIdempotencia(clave);if(existente.isPresent())return asientoResponse(existente.get());
        if(!"ABIERTO".equals(original.getPeriodo().getEstado()))throw new ResponseStatusException(HttpStatus.CONFLICT,"El periodo del asiento está cerrado");
        LocalDate fechaReversion=LocalDate.now();
        if(fechaReversion.isBefore(original.getPeriodo().getFechaInicio())||fechaReversion.isAfter(original.getPeriodo().getFechaFin()))throw new ResponseStatusException(HttpStatus.CONFLICT,"La fecha actual no pertenece al periodo abierto del asiento");
        AsientoContable contra=new AsientoContable();contra.setFolio("AS-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(Locale.ROOT));contra.setPeriodo(original.getPeriodo());contra.setFechaContable(fechaReversion);contra.setDescripcion("Reversión de "+original.getFolio());contra.setOrigenModulo(original.getOrigenModulo());contra.setOrigenTipo(original.getOrigenTipo());contra.setOrigenId(original.getOrigenId());contra.setClaveIdempotencia(clave);contra.setContraasientoDe(original);contra.setCreadoPor(usuarioActual());contra.setEstado("CONTABILIZADO");contra.setContabilizadoPor(usuarioActual());contra.setContabilizadoEn(Instant.now());
        short linea=1;for(var op:original.getPartidas()){PartidaAsiento p=new PartidaAsiento();p.setAsiento(contra);p.setNumeroLinea(linea++);p.setCuenta(op.getCuenta());p.setDebe(op.getHaber());p.setHaber(op.getDebe());p.setDescripcion("Reversión");contra.getPartidas().add(p);}
        original.setEstado("REVERSADO");asientos.save(original);return asientoResponse(asientos.saveAndFlush(contra));
    }
    @Transactional(readOnly=true) public PageResponse<AsientoContableResponse> listarAsientos(int page,int size,String sort,String direction,Long periodoId,String estado,String search){
        return PageResponse.from(asientos.search(periodoId,estado==null||estado.isBlank()?null:estado.trim().toUpperCase(Locale.ROOT),v(search),PageRequests.of(page,size,sort,direction,Map.of("id","id","folio","folio","fechaContable","fechaContable"),"id")),ContabilidadService::asientoResponse);
    }
    @Transactional(readOnly=true) public AsientoContableResponse obtenerAsiento(Long id){return asientoResponse(asientos.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Asiento contable no encontrado")));}

    @Transactional(readOnly=true)
    public ReportResponse reporteBalancePrueba(Long periodoId){
        PeriodoContable p=periodos.findById(periodoId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Periodo contable no encontrado"));
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery("""
            select c.id,c.codigo,c.nombre,c.tipo,c.naturaleza,
                   coalesce(sum(pa.debe),0),coalesce(sum(pa.haber),0)
            from cuentas_contables c
            left join partidas_asiento pa on pa.cuenta_contable_id=c.id
                and pa.asiento_id in (select a.id from asientos_contables a where a.periodo_id=:periodoId and a.estado='CONTABILIZADO')
            group by c.id,c.codigo,c.nombre,c.tipo,c.naturaleza
            having coalesce(sum(pa.debe),0)<>0 or coalesce(sum(pa.haber),0)<>0
            order by c.codigo
            """).setParameter("periodoId",periodoId).getResultList();
        List<ReportResponse.ReportLine> lines=new ArrayList<>();BigDecimal debit=ZERO,credit=ZERO;
        for(Object[] r:rows){BigDecimal d=bd(r[5]),h=bd(r[6]);debit=debit.add(d);credit=credit.add(h);BigDecimal bal="DEUDORA".equals(r[4])?d.subtract(h):h.subtract(d);lines.add(new ReportResponse.ReportLine((Long)r[0],String.valueOf(r[1]),String.valueOf(r[2]),String.valueOf(r[3]),d,h,bal));}
        return new ReportResponse("TRIAL_BALANCE",periodoId,p.getFechaInicio(),p.getFechaFin(),lines,debit.subtract(credit),"OK");
    }
    @Transactional(readOnly=true)
    public ReportResponse reporteTipo(Long periodoId,String tipo){
        PeriodoContable p=periodos.findById(periodoId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Periodo contable no encontrado"));
        String sql = """
            select c.id,c.codigo,c.nombre,c.tipo,c.naturaleza,
                   coalesce(sum(pa.debe),0),coalesce(sum(pa.haber),0)
            from cuentas_contables c join partidas_asiento pa on pa.cuenta_contable_id=c.id
            join asientos_contables a on a.id=pa.asiento_id and a.periodo_id=:periodoId and a.estado='CONTABILIZADO'
            where c.tipo in (:tipos)
            group by c.id,c.codigo,c.nombre,c.tipo,c.naturaleza
            order by c.codigo
            """;
        java.util.List<String> tipos=switch(tipo){case "INCOME"->List.of("INGRESO","COSTO","GASTO");case "BALANCE"->List.of("ACTIVO","PASIVO","CAPITAL");default->throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Tipo de estado financiero no válido");};
        @SuppressWarnings("unchecked") List<Object[]> rows=em.createNativeQuery(sql).setParameter("periodoId",periodoId).setParameter("tipos",tipos).getResultList();
        List<ReportResponse.ReportLine> lines=new ArrayList<>();Map<String,BigDecimal> totalsByType=new java.util.TreeMap<>();
        for(Object[] r:rows){BigDecimal d=bd(r[5]),h=bd(r[6]);BigDecimal bal="DEUDORA".equals(r[4])?d.subtract(h):h.subtract(d);String accountType=String.valueOf(r[3]);totalsByType.merge(accountType,bal,BigDecimal::add);lines.add(new ReportResponse.ReportLine((Long)r[0],String.valueOf(r[1]),String.valueOf(r[2]),accountType,d,h,bal));}
        return new ReportResponse(tipo,periodoId,p.getFechaInicio(),p.getFechaFin(),lines,null,"OK",totalsByType);
    }
    @Transactional(readOnly=true)
    public ReportResponse reporteMayor(Long periodoId,Long cuentaId){
        PeriodoContable p=periodos.findById(periodoId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Periodo contable no encontrado"));
        if(cuentaId!=null)cuentas.findById(cuentaId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Cuenta contable no encontrada"));
        String sql = """
            select c.id,c.codigo,c.nombre,c.tipo,coalesce(sum(pa.debe),0),coalesce(sum(pa.haber),0)
            from cuentas_contables c join partidas_asiento pa on pa.cuenta_contable_id=c.id
            join asientos_contables a on a.id=pa.asiento_id and a.periodo_id=:periodoId and a.estado='CONTABILIZADO'
            """ + (cuentaId==null?"":" where c.id=:cuentaId") + """
             group by c.id,c.codigo,c.nombre,c.tipo order by c.codigo
            """;
        jakarta.persistence.Query query=em.createNativeQuery(sql).setParameter("periodoId",periodoId);if(cuentaId!=null)query.setParameter("cuentaId",cuentaId);
        @SuppressWarnings("unchecked") List<Object[]> rows=query.getResultList();List<ReportResponse.ReportLine> lines=new ArrayList<>();BigDecimal total=ZERO;
        for(Object[] r:rows){BigDecimal d=bd(r[4]),h=bd(r[5]);BigDecimal bal=d.subtract(h);total=total.add(bal);lines.add(new ReportResponse.ReportLine((Long)r[0],String.valueOf(r[1]),String.valueOf(r[2]),String.valueOf(r[3]),d,h,bal));}
        return new ReportResponse("GENERAL_LEDGER",periodoId,p.getFechaInicio(),p.getFechaFin(),lines,total,"OK");
    }

    private boolean coincide(AsientoContable existente,AsientoContableRequest request){
        if(!existente.getFechaContable().equals(request.fechaContable())
            ||!existente.getDescripcion().equals(request.descripcion().trim())
            ||!existente.getOrigenModulo().equals(request.origenModulo().trim().toUpperCase(Locale.ROOT))
            ||!existente.getOrigenTipo().equals(request.origenTipo().trim().toUpperCase(Locale.ROOT))
            ||!java.util.Objects.equals(existente.getOrigenId(),request.origenId())
            ||existente.getPartidas().size()!=request.partidas().size())return false;
        List<PartidaAsiento> existingLines=existente.getPartidas().stream()
            .sorted(java.util.Comparator.comparing(PartidaAsiento::getNumeroLinea)).toList();
        for(int i=0;i<existingLines.size();i++){
            PartidaAsiento existing=existingLines.get(i);
            PartidaAsientoRequest requested=request.partidas().get(i);
            if(!existing.getCuenta().getId().equals(requested.cuentaId())
                ||existing.getDebe().compareTo(requested.debe())!=0
                ||existing.getHaber().compareTo(requested.haber())!=0
                ||!java.util.Objects.equals(existing.getDescripcion(),v(requested.descripcion())))return false;
        }
        return true;
    }
    private void validarBalance(AsientoContable a){if(a.getPartidas().size()<2)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El asiento requiere al menos dos partidas");BigDecimal d=ZERO,h=ZERO;for(var p:a.getPartidas()){d=d.add(p.getDebe()==null?ZERO:p.getDebe());h=h.add(p.getHaber()==null?ZERO:p.getHaber());}if(d.compareTo(h)!=0)throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"El asiento no está equilibrado");}
    private Usuario usuarioActual(){AuthenticatedActor actor=actorProvider.currentUser().orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Autenticación requerida"));return usuarios.findById(actor.usuarioId()).orElseThrow(()->new ResponseStatusException(HttpStatus.UNAUTHORIZED,"Usuario no encontrado"));}
    private static CuentaContableResponse cuentaResponse(CuentaContable c){return new CuentaContableResponse(c.getId(),c.getCodigo(),c.getNombre(),c.getTipo(),c.getNaturaleza(),c.getNivel(),c.getCuentaPadre()==null?null:c.getCuentaPadre().getId(),c.isAceptaMovimientos(),c.isActiva());}
    private static PeriodoContableResponse periodoResponse(PeriodoContable p){return new PeriodoContableResponse(p.getId(),p.getFechaInicio(),p.getFechaFin(),p.getEstado());}
    private static AsientoContableResponse asientoResponse(AsientoContable a){List<AsientoContableResponse.PartidaResponse> partidas=a.getPartidas().stream().map(p->new AsientoContableResponse.PartidaResponse(p.getCuenta().getId(),p.getCuenta().getCodigo(),p.getCuenta().getNombre(),p.getNumeroLinea(),p.getDebe(),p.getHaber(),p.getDescripcion())).toList();BigDecimal d=a.getPartidas().stream().map(p->p.getDebe()==null?ZERO:p.getDebe()).reduce(ZERO,BigDecimal::add),h=a.getPartidas().stream().map(p->p.getHaber()==null?ZERO:p.getHaber()).reduce(ZERO,BigDecimal::add);return new AsientoContableResponse(a.getId(),a.getFolio(),a.getPeriodo().getId(),a.getFechaContable(),a.getDescripcion(),a.getOrigenModulo(),a.getOrigenTipo(),a.getOrigenId(),a.getEstado(),partidas,d,h);}
    private static String v(String value){return value==null||value.isBlank()?null:value.trim();}
    private static ResponseStatusException conflicto(String message){return new ResponseStatusException(HttpStatus.CONFLICT,message);}
    private static final BigDecimal ZERO=BigDecimal.ZERO;
    private static BigDecimal bd(Object value){return value==null?ZERO:new BigDecimal(String.valueOf(value));}
}
