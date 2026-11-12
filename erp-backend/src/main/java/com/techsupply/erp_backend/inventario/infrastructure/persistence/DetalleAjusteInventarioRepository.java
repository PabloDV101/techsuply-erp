package com.techsupply.erp_backend.inventario.infrastructure.persistence;

import com.techsupply.erp_backend.inventario.domain.model.DetalleAjusteInventario;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface DetalleAjusteInventarioRepository extends Repository<DetalleAjusteInventario, Long> {
    Optional<DetalleAjusteInventario> findById(Long id);
}
