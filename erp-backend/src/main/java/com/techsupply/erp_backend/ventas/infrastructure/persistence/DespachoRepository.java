package com.techsupply.erp_backend.ventas.infrastructure.persistence;

import com.techsupply.erp_backend.ventas.domain.model.Despacho;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Repositorio de despachos sin borrado físico. */
public interface DespachoRepository extends Repository<Despacho, Long> {
    <S extends Despacho> S save(S despacho);
    void flush();
    Optional<Despacho> findById(Long id);
    Optional<Despacho> findByClaveIdempotencia(String claveIdempotencia);
    Page<Despacho> findAll(Pageable pageable);
    Page<Despacho> findByPedidoId(Long pedidoId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Despacho d where d.id = :id")
    Optional<Despacho> bloquearPorId(@Param("id") Long id);
}
