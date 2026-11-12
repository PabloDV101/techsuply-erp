package com.techsupply.erp_backend.security.application.dto;

import java.time.Instant;
import java.util.Set;

public record LoginResponse(
    String accessToken,
    String tokenType,
    Instant expiresAt,
    UsuarioResumen usuario
) {
    public record UsuarioResumen(Long id, String username, String correo, Set<String> roles, Set<String> permisos) {}
}
