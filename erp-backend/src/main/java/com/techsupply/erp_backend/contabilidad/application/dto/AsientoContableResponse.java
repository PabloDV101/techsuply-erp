package com.techsupply.erp_backend.contabilidad.application.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record AsientoContableResponse(Long id,String folio,Long periodoId,LocalDate fechaContable,String descripcion,String origenModulo,String origenTipo,Long origenId,String estado,List<PartidaResponse> partidas,BigDecimal totalDebe,BigDecimal totalHaber) {
    public record PartidaResponse(Long cuentaId,String cuentaCodigo,String cuentaNombre,short numeroLinea,BigDecimal debe,BigDecimal haber,String descripcion) {}
}
