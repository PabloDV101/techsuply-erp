package com.techsupply.erp_backend.security.infrastructure.security;

import com.techsupply.erp_backend.audit.application.AuditActorProvider;
import com.techsupply.erp_backend.security.application.AuthenticatedActor;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class SecurityAuditActorProvider implements AuditActorProvider {
    private final AuthenticatedUserProvider users;

    public SecurityAuditActorProvider(AuthenticatedUserProvider users) {
        this.users = users;
    }

    @Override
    public Optional<Long> currentUserId() {
        return users.currentUser().map(AuthenticatedActor::usuarioId);
    }
}
