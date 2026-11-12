package com.techsupply.erp_backend.security.infrastructure.persistence;

import com.techsupply.erp_backend.security.domain.model.TokenSesion;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TokenSesionRepository extends JpaRepository<TokenSesion, Long> {
    @EntityGraph(attributePaths = {"usuario.roles", "usuario.roles.permisos"})
    Optional<TokenSesion> findByTokenHashAndRevocadoEnIsNullAndVenceEnAfter(String tokenHash, Instant ahora);

    Optional<TokenSesion> findByTokenHash(String tokenHash);

    @Modifying
    @Query("update TokenSesion t set t.revocadoEn = :ahora where t.usuario.id = :usuarioId and t.revocadoEn is null and t.venceEn > :ahora")
    int revocarSesionesActivasDeUsuario(@Param("usuarioId") Long usuarioId, @Param("ahora") Instant ahora);
}
