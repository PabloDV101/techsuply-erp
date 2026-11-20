package com.techsupply.erp_backend.contabilidad.infrastructure.persistence;

import com.techsupply.erp_backend.contabilidad.domain.model.AsientoContable;
import com.techsupply.erp_backend.contabilidad.domain.model.EstadoAsientoContable;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsientoContableRepository extends Repository<AsientoContable, Long> {
    <S extends AsientoContable> S save(S asiento);
    Optional<AsientoContable> findById(Long id);
    Optional<AsientoContable> findByClaveIdempotencia(String claveIdempotencia);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AsientoContable a where a.id = :id")
    Optional<AsientoContable> bloquearPorId(@Param("id") Long id);
    Page<AsientoContable> findAll(Pageable pageable);
    boolean existsByPeriodoIdAndEstado(Long periodoId, EstadoAsientoContable estado);
}
