package com.techsupply.erp_backend.inventario.application.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.techsupply.erp_backend.inventario.domain.model.TipoAjuste;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;

public record CrearAjusteRequest(@NotNull @Positive Long almacenId, @NotBlank @Size(max = 1000) String motivo,
    @NotBlank @Size(max = 120) String claveIdempotencia, @NotEmpty List<@NotNull @Valid Linea> detalles) {
    @JsonAnySetter public void propiedadDesconocida(String nombre, Object valor) { throw new IllegalArgumentException("Propiedad no admitida"); }
    public record Linea(@NotNull @Positive Long productoId, @NotNull TipoAjuste tipo,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 11, fraction = 3) BigDecimal cantidad,
        @DecimalMin("0") @Digits(integer = 12, fraction = 4) BigDecimal costoUnitario,
        @Positive Long detalleConteoId, @Size(min = 1, max = 250) String motivoLinea) {
        @JsonAnySetter public void propiedadDesconocida(String nombre, Object valor) { throw new IllegalArgumentException("Propiedad no admitida"); }
    }
}
