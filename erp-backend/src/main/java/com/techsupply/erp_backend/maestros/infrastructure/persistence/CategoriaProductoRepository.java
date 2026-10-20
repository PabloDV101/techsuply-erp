package com.techsupply.erp_backend.maestros.infrastructure.persistence;

import com.techsupply.erp_backend.maestros.domain.model.CategoriaProducto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoriaProductoRepository extends JpaRepository<CategoriaProducto, Long> {
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Long id);

    // Serialize changes to the hierarchy across backend instances.
    @Query(value = "select 1 from pg_advisory_xact_lock(74320102)", nativeQuery = true)
    int bloquearJerarquia();
}
