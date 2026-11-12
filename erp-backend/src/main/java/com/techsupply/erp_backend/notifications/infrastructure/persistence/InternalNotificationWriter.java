package com.techsupply.erp_backend.notifications.infrastructure.persistence;

import com.techsupply.erp_backend.notifications.application.NotificationRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** PostgreSQL handles repeated operations and concurrent publishers atomically. */
@Service
public class InternalNotificationWriter {
    private final JdbcTemplate jdbc;

    public InternalNotificationWriter(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(NotificationRequest request) {
        String sql = "insert into notificaciones(usuario_id,tipo_evento,modulo,entidad,entidad_id,"
            + "clave_operacion,canal,asunto,cuerpo,estado) values (?,?,?,?,?,?,'APP',?,?,'NO_LEIDA') "
            + "on conflict (tipo_evento,clave_operacion,usuario_id) do nothing";
        for (Long recipientId : request.recipientIds()) {
            jdbc.update(sql, recipientId, request.type().name(), request.module(), request.resource(),
                request.resourceId(), request.operationKey(), request.type().subject(), request.type().body());
        }
    }
}
