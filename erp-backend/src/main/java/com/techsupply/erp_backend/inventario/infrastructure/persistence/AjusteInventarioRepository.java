package com.techsupply.erp_backend.inventario.infrastructure.persistence;

import com.techsupply.erp_backend.inventario.domain.model.AjusteInventario;
import java.util.Optional;
import java.util.List;
import com.techsupply.erp_backend.inventario.domain.model.EstadoAjuste;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

public interface AjusteInventarioRepository extends Repository<AjusteInventario, Long> {
    Optional<AjusteInventario> findById(Long id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AjusteInventario a where a.id = :id")
    Optional<AjusteInventario> bloquearPorId(@Param("id") Long id);
    <S extends AjusteInventario> S save(S entity);
    Optional<AjusteInventario> findByClaveIdempotencia(String clave);
    Page<AjusteInventario> findAll(Pageable pageable);
    Page<AjusteInventario> findByAlmacenId(Long almacenId, Pageable pageable);
    Page<AjusteInventario> findByEstado(EstadoAjuste estado, Pageable pageable);
    Page<AjusteInventario> findByAlmacenIdAndEstado(Long almacenId, EstadoAjuste estado, Pageable pageable);
    @Query("select distinct a from AjusteInventario a left join fetch a.detalles where a.id in :ids")
    List<AjusteInventario> conDetalles(@Param("ids") List<Long> ids);
}
