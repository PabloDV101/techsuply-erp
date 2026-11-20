package com.techsupply.erp_backend.contabilidad.application.dto;

import com.techsupply.erp_backend.contabilidad.domain.model.AsientoContable;
import com.techsupply.erp_backend.contabilidad.domain.model.EstadoAsientoContable;
import java.time.LocalDate;
import java.util.List;

public record AsientoContableResponse(Long id, String folio, LocalDate fechaContable, Long periodoId,
        String descripcion, EstadoAsientoContable estado, String origenModulo, String origenTipo,
        Long origenId, Long contraasientoDeId, Long creadoPorUsuarioId, List<PartidaAsientoResponse> partidas) {
    public AsientoContableResponse { partidas = List.copyOf(partidas); }
    public static AsientoContableResponse from(AsientoContable asiento, List<PartidaAsientoResponse> partidas) {
        return new AsientoContableResponse(asiento.getId(), asiento.getFolio(), asiento.getFechaContable(),
            asiento.getPeriodoId(), asiento.getDescripcion(), asiento.getEstado(), asiento.getOrigenModulo(),
            asiento.getOrigenTipo(), asiento.getOrigenId(), asiento.getContraasientoDeId(), asiento.getCreadoPorUsuarioId(), partidas);
    }
}
