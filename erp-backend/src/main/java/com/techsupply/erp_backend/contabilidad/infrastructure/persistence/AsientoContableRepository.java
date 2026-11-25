package com.techsupply.erp_backend.contabilidad.infrastructure.persistence;

import com.techsupply.erp_backend.contabilidad.domain.model.AsientoContable;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AsientoContableRepository extends JpaRepository<AsientoContable,Long> {
    Optional<AsientoContable> findByClaveIdempotencia(String clave);
    @Query("""
        select a from AsientoContable a
        where (:periodoId is null or a.periodo.id=:periodoId)
        and (:estado is null or a.estado=:estado)
        and (:search is null or lower(a.folio) like lower(concat('%',:search,'%'))
          or lower(a.descripcion) like lower(concat('%',:search,'%')))
        """)
    Page<AsientoContable> search(@Param("periodoId")Long periodoId,@Param("estado")String estado,@Param("search")String search,Pageable pageable);
}
