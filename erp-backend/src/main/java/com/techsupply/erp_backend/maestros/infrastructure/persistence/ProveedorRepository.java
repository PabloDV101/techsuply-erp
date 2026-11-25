package com.techsupply.erp_backend.maestros.infrastructure.persistence;

import com.techsupply.erp_backend.maestros.domain.model.Proveedor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProveedorRepository extends JpaRepository<Proveedor, Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
    boolean existsByRucIgnoreCase(String ruc);
    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);
    boolean existsByRucIgnoreCaseAndIdNot(String ruc, Long id);
    @Query("""
        select p from Proveedor p
        where (:activo is null or p.activo = :activo)
        and (:search is null or lower(p.codigo) like lower(concat('%', :search, '%'))
          or lower(p.ruc) like lower(concat('%', :search, '%'))
          or lower(p.razonSocial) like lower(concat('%', :search, '%')))
        """)
    Page<Proveedor> search(@Param("search") String search, @Param("activo") Boolean activo, Pageable pageable);
}
