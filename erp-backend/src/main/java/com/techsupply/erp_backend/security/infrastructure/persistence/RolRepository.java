package com.techsupply.erp_backend.security.infrastructure.persistence;

import com.techsupply.erp_backend.security.domain.model.Rol;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RolRepository extends JpaRepository<Rol, Long> {
    List<Rol> findByCodigoInAndActivoTrue(Set<String> codigos);
    List<Rol> findAllByActivoTrueOrderByCodigoAsc();
    Optional<Rol> findByCodigoAndActivoTrue(String codigo);

    @EntityGraph(attributePaths = "permisos")
    @Query("select distinct r from Rol r where r.id = :id")
    Optional<Rol> buscarConPermisosPorId(@Param("id") Long id);

    boolean existsByCodigoIgnoreCase(String codigo);
}
