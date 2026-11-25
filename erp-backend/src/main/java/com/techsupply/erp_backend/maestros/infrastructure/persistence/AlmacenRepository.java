package com.techsupply.erp_backend.maestros.infrastructure.persistence;

import com.techsupply.erp_backend.maestros.domain.model.Almacen;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AlmacenRepository extends JpaRepository<Almacen, Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);
    @Query("""
        select a from Almacen a
        where (:activo is null or a.activo = :activo)
        and (:search is null or lower(a.codigo) like lower(concat('%', :search, '%'))
          or lower(a.nombre) like lower(concat('%', :search, '%')))
        """)
    Page<Almacen> search(@Param("search") String search, @Param("activo") Boolean activo, Pageable pageable);
}
