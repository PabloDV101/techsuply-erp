package com.techsupply.erp_backend.security.infrastructure.persistence;

import com.techsupply.erp_backend.security.domain.model.Permiso;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PermisoRepository extends JpaRepository<Permiso, Long> {
    List<Permiso> findByCodigoIn(Set<String> codigos);
    List<Permiso> findAllByOrderByModuloAscAccionAsc();
    Optional<Permiso> findByCodigoIgnoreCase(String codigo);
    boolean existsByCodigoIgnoreCase(String codigo);
    boolean existsByModuloIgnoreCaseAndAccionIgnoreCase(String modulo, String accion);
}
