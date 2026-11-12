package com.techsupply.erp_backend.inventario.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RechazarAjusteRequest(@NotBlank @Size(max = 1000) String motivo) {
    @JsonAnySetter public void propiedadDesconocida(String nombre, Object valor) { throw new IllegalArgumentException("Propiedad no admitida"); }
}
