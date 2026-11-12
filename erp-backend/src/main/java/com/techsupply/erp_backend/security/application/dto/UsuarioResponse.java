package com.techsupply.erp_backend.security.application.dto;

import java.time.Instant;
import java.util.Set;

public record UsuarioResponse(
    Long id,
    String username,
    String correo,
    boolean activo,
    Long empleadoId,
    Long clienteId,
    Instant ultimoAcceso,
    Set<String> roles,
    Set<String> permisos
) {}
