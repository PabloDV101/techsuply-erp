package com.techsupply.erp_backend.inventario.infrastructure.persistence;

import com.techsupply.erp_backend.inventario.domain.model.MovimientoInventario;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface MovimientoInventarioRepository extends Repository<MovimientoInventario, Long> {
    Optional<MovimientoInventario> findById(Long id);
    Page<MovimientoInventario> findAll(Pageable pageable);
    Page<MovimientoInventario> findByProductoId(Long productoId, Pageable pageable);
    Page<MovimientoInventario> findByAlmacenId(Long almacenId, Pageable pageable);
    Page<MovimientoInventario> findByProductoIdAndAlmacenId(Long productoId, Long almacenId, Pageable pageable);
}
