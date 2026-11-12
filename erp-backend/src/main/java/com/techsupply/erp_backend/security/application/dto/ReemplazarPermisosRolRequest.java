package com.techsupply.erp_backend.security.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ReemplazarPermisosRolRequest(@NotNull Set<@NotBlank @Size(max = 60) String> permisos) {}
