package com.techsupply.erp_backend.notifications.application;

import com.techsupply.erp_backend.notifications.domain.model.InternalNotification;
import com.techsupply.erp_backend.notifications.domain.model.NotificationStatus;
import java.time.Instant;

public record NotificationResponse(Long id, NotificationType tipoEvento, String modulo, String entidad,
        Long entidadId, String asunto, String cuerpo, NotificationStatus estado, Instant creadaEn, Instant leidaEn) {
    public static NotificationResponse from(InternalNotification value) {
        return new NotificationResponse(value.getId(), value.getTipoEvento(), value.getModulo(),
            value.getEntidad(), value.getEntidadId(), value.getAsunto(), value.getCuerpo(),
            value.getEstado(), value.getCreadaEn(), value.getLeidaEn());
    }
}
