package com.techsupply.erp_backend.audit.application;

import java.util.Set;

/** Public contract for modules. Successful changes require the caller's transaction. */
public interface AuditTrail {
    void change(AuditAction action, Long resourceId, Set<AuditField> fields,
                AuditState previousState, AuditState newState);

    default void change(AuditAction action, Long resourceId) {
        change(action, resourceId, Set.of(), null, null);
    }

    void verifiedLogin(Long userId, Long sessionId);
    void systemBootstrap(Long userId);
    void deniedLoginAfterCommit();
    void logoutAfterCommit(Long userId, Long sessionId);
    void deniedAuthorization();
}
