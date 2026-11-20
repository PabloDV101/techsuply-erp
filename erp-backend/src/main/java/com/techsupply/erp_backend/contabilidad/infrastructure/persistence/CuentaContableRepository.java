package com.techsupply.erp_backend.contabilidad.infrastructure.persistence;

import com.techsupply.erp_backend.contabilidad.domain.model.CuentaContable;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.Repository;

public interface CuentaContableRepository extends Repository<CuentaContable, Long> {
    <S extends CuentaContable> S save(S cuenta);
    Optional<CuentaContable> findById(Long id);
    List<CuentaContable> findAllByIdIn(Collection<Long> ids);
    Optional<CuentaContable> findByCodigo(String codigo);
    boolean existsByCodigoIgnoreCase(String codigo);
    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);
    boolean existsByCuentaPadreId(Long cuentaPadreId);
    Page<CuentaContable> findAll(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CuentaContable c where c.id = :id")
    Optional<CuentaContable> bloquearPorId(@Param("id") Long id);
}
