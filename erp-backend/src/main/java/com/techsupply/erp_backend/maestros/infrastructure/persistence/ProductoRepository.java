package com.techsupply.erp_backend.maestros.infrastructure.persistence;

import com.techsupply.erp_backend.maestros.domain.model.Producto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductoRepository extends JpaRepository<Producto, Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);
    @Query("""
        select p from Producto p
        where (:activo is null or p.activo = :activo)
        and (:search is null or lower(p.codigo) like lower(concat('%', :search, '%'))
          or lower(p.descripcion) like lower(concat('%', :search, '%')))
        """)
    Page<Producto> search(@Param("search") String search, @Param("activo") Boolean activo, Pageable pageable);
}
