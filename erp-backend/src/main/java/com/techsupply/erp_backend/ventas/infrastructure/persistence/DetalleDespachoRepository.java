package com.techsupply.erp_backend.ventas.infrastructure.persistence;

import com.techsupply.erp_backend.ventas.domain.model.DetalleDespacho;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface DetalleDespachoRepository extends Repository<DetalleDespacho, Long> {
    <S extends DetalleDespacho> S save(S detalle);
    Optional<DetalleDespacho> findById(Long id);
    List<DetalleDespacho> findByDespachoIdOrderByIdAsc(Long id);
    List<DetalleDespacho> findByDespachoIdInOrderByDespachoIdAscIdAsc(List<Long> ids);
}
