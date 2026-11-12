package com.techsupply.erp_backend.inventario.application.dto;

import com.techsupply.erp_backend.inventario.domain.model.ClaseConteo;
import com.techsupply.erp_backend.inventario.domain.model.EstadoConteo;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ConteoResponse(Long id, String folio, Long almacenId, Long creadoPorUsuarioId, LocalDate fechaProgramada,
                             ClaseConteo clase, EstadoConteo estado, List<Linea> detalles) {
    public record Linea(Long id, Long productoId, BigDecimal cantidadSistema, BigDecimal cantidadContada,
                        BigDecimal diferencia, boolean existenciaPrevia, Integer versionExistencia) {}
}
