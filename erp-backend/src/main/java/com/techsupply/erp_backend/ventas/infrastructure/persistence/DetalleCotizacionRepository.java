package com.techsupply.erp_backend.ventas.infrastructure.persistence;

import com.techsupply.erp_backend.ventas.domain.model.DetalleCotizacion;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface DetalleCotizacionRepository extends Repository<DetalleCotizacion, Long> {
    Optional<DetalleCotizacion> findById(Long id);
    List<DetalleCotizacion> findByCotizacionIdOrderByIdAsc(Long id);
}
