package com.techsupply.erp_backend.contabilidad.infrastructure.persistence;

import com.techsupply.erp_backend.contabilidad.domain.model.PeriodoContable;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

public interface PeriodoContableRepository extends Repository<PeriodoContable, Long> {
    <S extends PeriodoContable> S save(S periodo);
    Optional<PeriodoContable> findById(Long id);
    Page<PeriodoContable> findAll(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PeriodoContable p where p.id = :id")
    Optional<PeriodoContable> bloquearPorId(@Param("id") Long id);
}
