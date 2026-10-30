package com.techsupply.erp_backend.compras.infrastructure.persistence;

import com.techsupply.erp_backend.compras.domain.model.RecepcionCompra;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecepcionCompraRepository extends JpaRepository<RecepcionCompra, Long>, JpaSpecificationExecutor<RecepcionCompra> {
    Optional<RecepcionCompra> findByClaveIdempotencia(String claveIdempotencia);
    @Query(value = "select * from recepciones_compra where id = :id for update", nativeQuery = true)
    Optional<RecepcionCompra> bloquearPorId(@Param("id") Long id);
    @Query("select distinct r from RecepcionCompra r left join fetch r.detalles where r.id in :ids")
    List<RecepcionCompra> conDetalles(@Param("ids") List<Long> ids);
}
