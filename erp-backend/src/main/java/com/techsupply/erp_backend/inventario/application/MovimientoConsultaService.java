package com.techsupply.erp_backend.inventario.application;

import com.techsupply.erp_backend.common.api.PageRequests;
import com.techsupply.erp_backend.common.api.PageResponse;
import com.techsupply.erp_backend.inventario.application.dto.MovimientoResponse;
import com.techsupply.erp_backend.inventario.domain.model.MovimientoInventario;
import com.techsupply.erp_backend.inventario.infrastructure.persistence.MovimientoInventarioRepository;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MovimientoConsultaService {
    private final MovimientoInventarioRepository movements;

    public MovimientoConsultaService(MovimientoInventarioRepository movements) { this.movements = movements; }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('INVENTARIO_MOVIMIENTOS_LEER')")
    public PageResponse<MovimientoResponse> listar(int page, int size, Long productoId, Long almacenId) {
        if ((productoId != null && productoId < 1) || (almacenId != null && almacenId < 1))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Filtro de movimiento inválido");
        Pageable pageable = PageRequests.of(page, size, "fecha", "desc", Map.of("fecha", "fecha"), "id");
        Page<MovimientoInventario> result;
        if (productoId != null && almacenId != null) result = movements.findByProductoIdAndAlmacenId(productoId, almacenId, pageable);
        else if (productoId != null) result = movements.findByProductoId(productoId, pageable);
        else if (almacenId != null) result = movements.findByAlmacenId(almacenId, pageable);
        else result = movements.findAll(pageable);
        return PageResponse.from(result, MovimientoConsultaService::response);
    }

    private static MovimientoResponse response(MovimientoInventario m) {
        return new MovimientoResponse(m.getId(), m.getProductoId(), m.getAlmacenId(), m.getTipo(), m.getMotivo(),
            m.getCantidad(), m.getCostoUnitario(), m.getFecha(), m.getUsuarioId(), m.getDetalleRecepcionId(), m.getDetalleAjusteId(), m.getDetalleDespachoId());
    }
}
