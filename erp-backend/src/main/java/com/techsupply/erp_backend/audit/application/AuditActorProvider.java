package com.techsupply.erp_backend.audit.application;

import java.util.Optional;

/** Port implemented by security; audit never reads token or SecurityContext internals. */
public interface AuditActorProvider {
    Optional<Long> currentUserId();
}
