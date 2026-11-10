package com.techsupply.erp_backend.inventario.infrastructure.persistence;

import com.techsupply.erp_backend.inventario.domain.model.DetalleConteo;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface DetalleConteoRepository extends Repository<DetalleConteo, Long> {
    Optional<DetalleConteo> findById(Long id);
}
