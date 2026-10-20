package com.techsupply.erp_backend.audit.application;

import com.techsupply.erp_backend.audit.domain.model.AuditActorType;
import com.techsupply.erp_backend.audit.domain.model.AuditEvent;
import com.techsupply.erp_backend.audit.domain.model.AuditResult;
import java.time.Instant;
import java.util.List;

public record AuditEventResponse(Long id, Long usuarioId, AuditActorType actorTipo, String modulo,
        String entidad, Long entidadId, String accion, AuditResult resultado, String motivoCodigo,
        Instant fecha, List<String> camposModificados, String estadoAnterior, String estadoNuevo) {
    public static AuditEventResponse from(AuditEvent event) {
        Object fields = event.getDetalle().get("campos");
        List<String> names = fields instanceof List<?> list ? list.stream().map(String::valueOf).toList() : List.of();
        return new AuditEventResponse(event.getId(), event.getUsuarioId(), event.getActorTipo(),
            event.getModulo(), event.getEntidad(), event.getEntidadId(), event.getAccion(),
            event.getResultado(), event.getMotivoCodigo(), event.getFecha(), names,
            (String) event.getDetalle().get("estadoAnterior"), (String) event.getDetalle().get("estadoNuevo"));
    }
}
