package com.techsupply.erp_backend.ventas.infrastructure.persistence;

import com.techsupply.erp_backend.ventas.domain.model.Cotizacion;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Repositorio sin borrado físico. */
public interface CotizacionRepository extends Repository<Cotizacion, Long> {
    <S extends Cotizacion> S save(S cotizacion);
    void flush();
    Optional<Cotizacion> findById(Long id);
    Optional<Cotizacion> findByClaveIdempotencia(String claveIdempotencia);
    Page<Cotizacion> findAll(Pageable pageable);
    Page<Cotizacion> findByClienteId(Long clienteId, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Cotizacion d where d.id = :id")
    Optional<Cotizacion> bloquearPorId(@Param("id") Long id);
}
