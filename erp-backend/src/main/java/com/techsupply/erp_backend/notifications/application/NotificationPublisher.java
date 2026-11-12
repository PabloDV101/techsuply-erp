package com.techsupply.erp_backend.notifications.application;

/** Called inside a business transaction; the inbox insert runs only after its commit. */
public interface NotificationPublisher {
    void publishAfterCommit(NotificationRequest request);
}
