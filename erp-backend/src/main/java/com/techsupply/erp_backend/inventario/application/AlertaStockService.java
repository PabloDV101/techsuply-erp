package com.techsupply.erp_backend.inventario.application;

import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.dto.AlertaStockResponse;
import com.techsupply.erp_backend.inventario.infrastructure.persistence.AlertaStockRepository;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AlertaStockService {
    private final AlertaStockRepository alerts;
    public AlertaStockService(AlertaStockRepository alerts) { this.alerts = alerts; }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INVENTARIO_ALERTAS_LEER')")
    public PageResponse<AlertaStockResponse> listar(int page, int size, Long productoId) {
        if (productoId != null && productoId < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de producto inválido");
        var pageable = PageRequests.of(page, size, "id", "asc", Map.of("id", "id"));
        return alerts.buscar(pageable, productoId);
    }
}
