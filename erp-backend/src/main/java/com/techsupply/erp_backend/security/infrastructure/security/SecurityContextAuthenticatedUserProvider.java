package com.techsupply.erp_backend.security.infrastructure.security;

import com.techsupply.erp_backend.security.application.AuthenticatedActor;
import com.techsupply.erp_backend.security.application.AuthenticatedUserProvider;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityContextAuthenticatedUserProvider implements AuthenticatedUserProvider {
    @Override
    public java.util.Optional<AuthenticatedActor> currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof SecurityActor actor)) {
            return java.util.Optional.empty();
        }
        Set<String> authorities = authentication.getAuthorities().stream()
            .map(granted -> granted.getAuthority())
            .collect(Collectors.toUnmodifiableSet());
        return java.util.Optional.of(new AuthenticatedActor(actor.usuarioId(), actor.username(), authorities));
    }
}
