package com.techsupply.erp_backend.contabilidad.application.dto;

import com.techsupply.erp_backend.contabilidad.domain.model.PartidaAsiento;
import java.math.BigDecimal;

public record PartidaAsientoResponse(Long id, short numeroLinea, Long cuentaId,
        BigDecimal debe, BigDecimal haber, String descripcion) {
    public static PartidaAsientoResponse from(PartidaAsiento partida) {
        return new PartidaAsientoResponse(partida.getId(), partida.getNumeroLinea(), partida.getCuentaContableId(),
            partida.getDebe(), partida.getHaber(), partida.getDescripcion());
    }
}
