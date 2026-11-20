package com.techsupply.erp_backend.contabilidad.application.dto;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
public final class ClasificacionFlujoRequests {
    private ClasificacionFlujoRequests() {}
    public record Cuenta(@NotNull @Positive Long cuentaId, @NotBlank @Pattern(regexp="EFECTIVO|CAPITAL_TRABAJO|INVERSION|FINANCIACION|NO_MONETARIO|RESULTADO") String clasificacion, @NotNull LocalDate desde, @NotNull LocalDate hasta) {}
    public record Partida(@NotNull @Positive Long partidaId, @NotBlank @Pattern(regexp="OPERACION|INVERSION|FINANCIACION|NO_MONETARIO") String clasificacion) {}
    public record Anulacion(@NotBlank @Size(max=300) String motivo) {}
}
