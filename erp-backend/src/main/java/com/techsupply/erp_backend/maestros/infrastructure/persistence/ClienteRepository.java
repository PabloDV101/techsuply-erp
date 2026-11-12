package com.techsupply.erp_backend.maestros.infrastructure.persistence;

import com.techsupply.erp_backend.maestros.domain.model.Cliente;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ClienteRepository extends Repository<Cliente, Long>, JpaSpecificationExecutor<Cliente> {
    Optional<Cliente> findById(Long id);
    Optional<Cliente> findByCodigo(String codigo);
    boolean existsByCodigo(String codigo);
    boolean existsByCodigoAndIdNot(String codigo, Long id);
    boolean existsByIdentificadorFiscal(String fiscal);
    boolean existsByIdentificadorFiscalAndIdNot(String fiscal, Long id);
    Page<Cliente> findAll(Pageable pageable);
    <S extends Cliente> S save(S cliente);
    void flush();
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Cliente c where c.id = :id")
    Optional<Cliente> bloquearPorId(@Param("id") Long id);
}
