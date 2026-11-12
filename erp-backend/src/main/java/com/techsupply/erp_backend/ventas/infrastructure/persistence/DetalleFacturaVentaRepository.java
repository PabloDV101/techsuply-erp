package com.techsupply.erp_backend.ventas.infrastructure.persistence;

import com.techsupply.erp_backend.ventas.domain.model.DetalleFacturaVenta;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface DetalleFacturaVentaRepository extends Repository<DetalleFacturaVenta,Long> {
    <S extends DetalleFacturaVenta> S save(S line);
    List<DetalleFacturaVenta> findByFacturaVentaIdOrderByIdAsc(Long id);
    List<DetalleFacturaVenta> findByFacturaVentaIdInOrderByFacturaVentaIdAscIdAsc(List<Long> ids);
}
