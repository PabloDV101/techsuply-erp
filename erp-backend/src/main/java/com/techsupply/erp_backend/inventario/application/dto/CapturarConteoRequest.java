package com.techsupply.erp_backend.inventario.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CapturarConteoRequest(@NotNull @DecimalMin("0") @Digits(integer = 11, fraction = 3) BigDecimal cantidadContada) {
    @JsonAnySetter public void propiedadDesconocida(String nombre, Object valor) {
        throw new IllegalArgumentException("Propiedad no admitida");
    }
}
