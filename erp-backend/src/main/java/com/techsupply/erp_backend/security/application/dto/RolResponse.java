package com.techsupply.erp_backend.security.application.dto;

import java.util.Set;

public record RolResponse(Long id, String codigo, String nombre, String descripcion, Set<String> permisos) {}
