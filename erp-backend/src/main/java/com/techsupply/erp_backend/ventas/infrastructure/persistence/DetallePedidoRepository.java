package com.techsupply.erp_backend.ventas.infrastructure.persistence;

import com.techsupply.erp_backend.ventas.domain.model.DetallePedido;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface DetallePedidoRepository extends Repository<DetallePedido, Long> {
    Optional<DetallePedido> findById(Long id);
    List<DetallePedido> findByPedidoIdOrderByIdAsc(Long id);
}
