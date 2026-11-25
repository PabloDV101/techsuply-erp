package com.techsupply.erp_backend.maestros.infrastructure.persistence;

import com.techsupply.erp_backend.maestros.domain.model.Cliente;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
    boolean existsByCodigoIgnoreCase(String codigo);
    boolean existsByRucIgnoreCase(String ruc);
    boolean existsByCodigoIgnoreCaseAndIdNot(String codigo, Long id);
    boolean existsByRucIgnoreCaseAndIdNot(String ruc, Long id);
    @Query("""
        select c from Cliente c
        where (:activo is null or c.activo = :activo)
        and (:search is null or lower(c.codigo) like lower(concat('%', :search, '%'))
          or lower(c.ruc) like lower(concat('%', :search, '%'))
          or lower(c.razonSocial) like lower(concat('%', :search, '%')))
        """)
    Page<Cliente> search(@Param("search") String search, @Param("activo") Boolean activo, Pageable pageable);
}
