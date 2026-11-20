package com.techsupply.erp_backend.contabilidad.infrastructure.persistence;

import com.techsupply.erp_backend.contabilidad.domain.model.PartidaAsiento;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.repository.Repository;

public interface PartidaAsientoRepository extends Repository<PartidaAsiento, Long> {
    <S extends PartidaAsiento> S save(S partida);
    void deleteByAsientoId(Long asientoId);
    Optional<PartidaAsiento> findById(Long id);
    List<PartidaAsiento> findByAsientoIdOrderByNumeroLineaAsc(Long asientoId);
    List<PartidaAsiento> findByAsientoIdInOrderByAsientoIdAscNumeroLineaAsc(Collection<Long> asientoIds);
    boolean existsByCuentaContableId(Long cuentaContableId);
}
