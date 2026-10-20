package com.techsupply.erp_backend.security.application;

import java.util.Set;

public record AuthenticatedActor(Long usuarioId, String username, Set<String> authorities) {}
