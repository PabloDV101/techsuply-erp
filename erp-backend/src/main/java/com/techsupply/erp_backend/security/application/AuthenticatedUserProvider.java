package com.techsupply.erp_backend.security.application;

import java.util.Optional;

/** Contrato transversal para que los módulos no dependan de SecurityContextHolder. */
public interface AuthenticatedUserProvider {
    Optional<AuthenticatedActor> currentUser();
}
