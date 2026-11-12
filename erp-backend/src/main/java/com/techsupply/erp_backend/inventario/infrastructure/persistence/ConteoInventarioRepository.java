package com.techsupply.erp_backend.inventario.infrastructure.persistence;

import com.techsupply.erp_backend.inventario.domain.model.ConteoInventario;
import java.util.Optional;
import java.util.List;
import com.techsupply.erp_backend.inventario.domain.model.EstadoConteo;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

public interface ConteoInventarioRepository extends Repository<ConteoInventario, Long> {
    Optional<ConteoInventario> findById(Long id);
    <S extends ConteoInventario> S save(S entity);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from ConteoInventario c where c.id = :id")
    Optional<ConteoInventario> bloquearPorId(@Param("id") Long id);
    Page<ConteoInventario> findAll(Pageable pageable);
    Page<ConteoInventario> findByAlmacenId(Long almacenId, Pageable pageable);
    Page<ConteoInventario> findByEstado(EstadoConteo estado, Pageable pageable);
    Page<ConteoInventario> findByAlmacenIdAndEstado(Long almacenId, EstadoConteo estado, Pageable pageable);
    @Query("select distinct c from ConteoInventario c left join fetch c.detalles where c.id in :ids")
    List<ConteoInventario> conDetalles(@Param("ids") List<Long> ids);
}
