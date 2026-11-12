package com.techsupply.erp_backend.ventas.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelarPedidoRequest(@NotBlank @Size(max = 1000) String motivo) {}
