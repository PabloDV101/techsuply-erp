package com.techsupply.erp_backend.contabilidad.application.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record PagoProveedorResponse(Long id, String folio, Long proveedorId, Instant fechaPago,
        BigDecimal monto, String medio, String referencia, String estado, Long asientoContableId,
        LocalDate fechaContable, List<Aplicacion> aplicaciones) {
    public record Aplicacion(Long cuentaPorPagarId, BigDecimal montoAplicado) {}
}
