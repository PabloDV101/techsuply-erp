package com.techsupply.erp_backend.notifications.application;

import com.techsupply.erp_backend.notifications.infrastructure.persistence.InternalNotificationWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class AfterCommitNotificationPublisher implements NotificationPublisher {
    private static final Logger log = LoggerFactory.getLogger(AfterCommitNotificationPublisher.class);
    private final InternalNotificationWriter writer;

    public AfterCommitNotificationPublisher(InternalNotificationWriter writer) { this.writer = writer; }

    @Override
    public void publishAfterCommit(NotificationRequest request) {
        if (request == null || !TransactionSynchronizationManager.isSynchronizationActive()
                || !TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("La notificación requiere una transacción de negocio activa");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() {
                try {
                    writer.append(request);
                } catch (RuntimeException ex) {
                    // A notification failure must not change the committed business outcome.
                    // No operation key, recipient, request body or SQL is written to this log.
                    log.warn("No se pudo publicar una notificación interna del tipo {}", request.type());
                }
            }
        });
    }
}
