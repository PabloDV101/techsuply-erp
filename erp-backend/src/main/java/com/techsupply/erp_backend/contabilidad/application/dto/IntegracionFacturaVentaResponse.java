package com.techsupply.erp_backend.contabilidad.application.dto;

import java.time.Instant;
import java.time.LocalDate;

public record IntegracionFacturaVentaResponse(Long facturaVentaId, String folio, String estado,
    Long asientoContableId, LocalDate fechaContable, int intentos, LocalDate ultimaFechaIntentada,
    String ultimoCodigoError, Instant emitidaEn, Instant contabilizadaEn) {}
