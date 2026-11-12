package com.techsupply.erp_backend.inventario.application.dto;

import com.techsupply.erp_backend.inventario.domain.model.ClaseConteo;
import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;

public record CrearConteoRequest(@NotNull @Positive Long almacenId, @NotNull LocalDate fechaProgramada, @NotNull ClaseConteo clase) {
    @JsonAnySetter public void propiedadDesconocida(String nombre, Object valor) {
        throw new IllegalArgumentException("Propiedad no admitida");
    }
}
