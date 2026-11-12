package com.techsupply.erp_backend.ventas.application.dto;

import com.techsupply.erp_backend.ventas.domain.model.ModalidadVenta;
import jakarta.validation.constraints.*;

public record PedidoCreateRequest(@NotNull @Positive Long cotizacionId, @NotNull @Positive Long almacenId,
        @NotNull ModalidadVenta modalidad, @NotBlank @Size(max = 100) String claveIdempotencia) {}
