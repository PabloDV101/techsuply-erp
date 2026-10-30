package com.techsupply.erp_backend.inventario.infrastructure.persistence;

import com.techsupply.erp_backend.inventario.domain.model.MovimientoInventario;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface MovimientoInventarioRepository extends Repository<MovimientoInventario, Long> {
    Optional<MovimientoInventario> findById(Long id);
}
