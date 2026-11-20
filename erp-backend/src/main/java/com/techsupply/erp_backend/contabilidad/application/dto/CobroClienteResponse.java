package com.techsupply.erp_backend.contabilidad.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

public record CobroClienteResponse(Long id, String folio, Long clienteId, Instant fecha, BigDecimal monto,
        String medio, String referencia, String estado, BigDecimal montoAplicado, BigDecimal montoDisponible,
        Long asientoContableId, LocalDate fechaContable) {}
