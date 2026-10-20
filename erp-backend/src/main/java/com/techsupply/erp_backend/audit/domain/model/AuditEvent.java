package com.techsupply.erp_backend.audit.domain.model;

import com.techsupply.erp_backend.audit.application.AuditAction;
import com.techsupply.erp_backend.audit.application.AuditField;
import com.techsupply.erp_backend.audit.application.AuditState;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "eventos_auditoria")
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "usuario_id")
    private Long usuarioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_tipo", nullable = false, length = 10)
    private AuditActorType actorTipo;

    @Column(nullable = false, length = 40)
    private String modulo;

    @Column(nullable = false, length = 80)
    private String entidad;

    @Column(name = "entidad_id")
    private Long entidadId;

    @Column(nullable = false, length = 40)
    private String accion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AuditResult resultado;

    @Column(name = "motivo_codigo", length = 40)
    private String motivoCodigo;

    @Column(nullable = false)
    private Instant fecha;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> detalle = Map.of();

    protected AuditEvent() {}

    public AuditEvent(AuditAction action, AuditActorType actorTipo, Long usuarioId, Long entidadId,
            AuditResult resultado, String motivoCodigo, Set<AuditField> fields,
            AuditState previousState, AuditState newState, Instant fecha) {
        if ((actorTipo == AuditActorType.USUARIO) != (usuarioId != null)) {
            throw new IllegalArgumentException("Actor de auditoría incoherente");
        }
        this.actorTipo = actorTipo;
        this.usuarioId = usuarioId;
        this.modulo = action.module();
        this.entidad = action.resource();
        this.entidadId = entidadId;
        this.accion = action.name();
        this.resultado = resultado;
        this.motivoCodigo = motivoCodigo;
        this.fecha = fecha;
        Map<String, Object> safe = new HashMap<>();
        if (!fields.isEmpty()) {
            safe.put("campos", fields.stream().map(Enum::name).sorted().toList());
        }
        if (previousState != null) safe.put("estadoAnterior", previousState.name());
        if (newState != null) safe.put("estadoNuevo", newState.name());
        this.detalle = Map.copyOf(safe);
    }

    public Long getId() { return id; }
    public Long getUsuarioId() { return usuarioId; }
    public AuditActorType getActorTipo() { return actorTipo; }
    public String getModulo() { return modulo; }
    public String getEntidad() { return entidad; }
    public Long getEntidadId() { return entidadId; }
    public String getAccion() { return accion; }
    public AuditResult getResultado() { return resultado; }
    public String getMotivoCodigo() { return motivoCodigo; }
    public Instant getFecha() { return fecha; }
    public Map<String, Object> getDetalle() { return detalle; }
}
