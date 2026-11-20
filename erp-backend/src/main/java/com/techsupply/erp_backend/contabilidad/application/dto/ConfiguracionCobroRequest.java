package com.techsupply.erp_backend.contabilidad.application.dto;

import jakarta.validation.constraints.*;

public record ConfiguracionCobroRequest(@NotBlank @Pattern(regexp="EFECTIVO|TRANSFERENCIA|CHEQUE|TARJETA|OTRO") String medio,
        @NotNull @Positive Long cuentaFondosId, @NotNull @Positive Long cuentaAnticiposId) {}
