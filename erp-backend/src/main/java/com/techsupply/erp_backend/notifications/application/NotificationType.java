package com.techsupply.erp_backend.notifications.application;

/** Approved RF-SYS-02 categories. A module must still approve its trigger and recipient rule. */
public enum NotificationType {
    STOCK_BAJO("Stock bajo", "Hay existencias por debajo del umbral; consulte Inventario."),
    APROBACION_PENDIENTE("Aprobación pendiente", "Hay una solicitud pendiente; consulte el módulo correspondiente."),
    RESULTADO_OPERACION("Resultado de operación", "El resultado de una operación está disponible en el módulo de origen.");

    private final String subject;
    private final String body;

    NotificationType(String subject, String body) {
        this.subject = subject;
        this.body = body;
    }

    public String subject() { return subject; }
    public String body() { return body; }
}
