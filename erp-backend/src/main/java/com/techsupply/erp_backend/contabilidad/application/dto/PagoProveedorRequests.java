package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class PagoProveedorRequests {
    private PagoProveedorRequests() {}
    public record Registrar(@NotNull @Positive Long proveedorId, @NotNull Instant fechaPago,
        @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal monto,
        @NotBlank @Pattern(regexp="TRANSFERENCIA|CHEQUE|EFECTIVO|TARJETA|OTRO") String medio,
        @Size(max=100) String referencia, @NotBlank @Size(max=100) String claveIdempotencia) {}
    public record Aplicacion(@NotNull @Positive Long cuentaPorPagarId,
        @NotNull @DecimalMin("0.01") @Digits(integer=12,fraction=2) BigDecimal monto) {}
    public record Confirmar(@NotEmpty @Size(max=100) List<@NotNull @Valid Aplicacion> aplicaciones) {}
    public record Configurar(@NotNull @Positive Long cuentaFondosId) {}
}
