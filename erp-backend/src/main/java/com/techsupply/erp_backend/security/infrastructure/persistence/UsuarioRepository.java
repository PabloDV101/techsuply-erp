package com.techsupply.erp_backend.security.infrastructure.persistence;

import com.techsupply.erp_backend.security.domain.model.Usuario;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {
    @Query("""
        select distinct u from Usuario u
        left join fetch u.roles r
        left join fetch r.permisos
        where lower(u.username) = lower(:identificador)
           or lower(u.correo) = lower(:identificador)
        """)
    Optional<Usuario> buscarParaAutenticacion(@Param("identificador") String identificador);

    @EntityGraph(attributePaths = {"roles", "roles.permisos"})
    @Query("select distinct u from Usuario u where u.id = :id")
    Optional<Usuario> buscarConRolesYPermisosPorId(@Param("id") Long id);

    @EntityGraph(attributePaths = {"roles", "roles.permisos"})
    @Query("select distinct u from Usuario u order by u.username")
    List<Usuario> listarConRolesYPermisos();

    @Query("select count(distinct u) from Usuario u join u.roles r where u.activo = true and r.activo = true and r.codigo = 'ADMINISTRADOR'")
    long contarAdministradoresActivos();

    Optional<Usuario> findByUsernameIgnoreCase(String username);
    @Modifying
    @Query(value = "UPDATE usuarios_roles SET asignado_por_usuario_id = :asignadorId, asignado_en = :asignadoEn WHERE usuario_id = :usuarioId", nativeQuery = true)
    int registrarAsignadorDeRoles(@Param("usuarioId") Long usuarioId, @Param("asignadorId") Long asignadorId,
                                  @Param("asignadoEn") java.time.Instant asignadoEn);

    boolean existsByUsernameIgnoreCase(String username);
    boolean existsByCorreoIgnoreCase(String correo);
}
