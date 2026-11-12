package com.techsupply.erp_backend.ventas.infrastructure.persistence;

import com.techsupply.erp_backend.ventas.domain.model.FacturaVenta;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

public interface FacturaVentaRepository extends Repository<FacturaVenta,Long> {
    <S extends FacturaVenta> S save(S invoice);
    void flush();
    Optional<FacturaVenta> findById(Long id);
    Optional<FacturaVenta> findByPedidoId(Long pedidoId);
    Optional<FacturaVenta> findByClaveIdempotencia(String clave);
    Page<FacturaVenta> findAll(Pageable pageable);
    Page<FacturaVenta> findByClienteId(Long clienteId,Pageable pageable);
}
