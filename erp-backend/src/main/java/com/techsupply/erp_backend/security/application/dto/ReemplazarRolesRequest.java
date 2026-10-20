package com.techsupply.erp_backend.security.application.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record ReemplazarRolesRequest(@NotEmpty Set<@NotBlank @Size(max = 30) String> roles) {}
