package com.techsupply.erp_backend.ventas.infrastructure.persistence;

import com.techsupply.erp_backend.ventas.domain.model.PedidoVenta;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** No delete or save until the business operation is implemented. */
public interface PedidoVentaRepository extends Repository<PedidoVenta, Long> {
    Optional<PedidoVenta> findById(Long id);
    Optional<PedidoVenta> findByClaveIdempotencia(String claveIdempotencia);
    Optional<PedidoVenta> findByCotizacionId(Long cotizacionId);
    Page<PedidoVenta> findAll(Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from PedidoVenta d where d.id = :id")
    Optional<PedidoVenta> bloquearPorId(@Param("id") Long id);
}
