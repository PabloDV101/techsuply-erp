package com.techsupply.erp_backend.notifications.application;

import java.util.Set;

/** Internal application contract; never populate from an HTTP request. */
public record NotificationRequest(NotificationType type, String module, String resource,
        Long resourceId, String operationKey, Set<Long> recipientIds) {
    public NotificationRequest {
        if (type == null || resourceId == null || resourceId <= 0 || operationKey == null
                || operationKey.isBlank() || operationKey.length() > 120
                || operationKey.contains("\n") || operationKey.contains("\r")
                || !code(module, 40) || !code(resource, 80)
                || recipientIds == null || recipientIds.isEmpty()
                || recipientIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new IllegalArgumentException("Solicitud interna de notificación inválida");
        }
        recipientIds = Set.copyOf(recipientIds);
    }

    private static boolean code(String value, int maxLength) {
        return value != null && value.length() <= maxLength && value.matches("[A-Z][A-Z0-9_]*");
    }
}
