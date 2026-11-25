package com.techsupply.erp_backend.contabilidad.application;

import com.techsupply.erp_backend.contabilidad.application.dto.ResultadoFinancieroResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Proyección del resultado existente; no calcula ni consulta partidas por separado. */
@Service
public class ResultadoFinancieroService {
    private final EstadosFinancierosService estados;

    public ResultadoFinancieroService(EstadosFinancierosService estados) { this.estados = estados; }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    @PreAuthorize("hasAuthority('CONTABILIDAD_REPORTES_LEER')")
    public ResultadoFinancieroResponse consultar(Long periodId) {
        if (periodId == null || periodId < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Periodo inválido");
        var estado = estados.resultados(periodId);
        if (!"DISPONIBLE".equals(estado.estado()))
            throw new IllegalStateException("El estado de resultados no está disponible");
        var resultado = estado.rubros().stream()
            .filter(rubro -> "RESULTADO".equals(rubro.nombre()))
            .map(rubro -> rubro.importe())
            .findFirst().orElseThrow(() -> new IllegalStateException("Falta el resultado contable"));
        return new ResultadoFinancieroResponse(estado.periodoId(), estado.inicio(), estado.fin(), resultado);
    }
}
