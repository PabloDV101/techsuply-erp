package com.techsupply.erp_backend.security.application;

import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.security.application.dto.RecuperarCuentaLocalRequest;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import com.techsupply.erp_backend.security.infrastructure.persistence.TokenSesionRepository;
import com.techsupply.erp_backend.security.infrastructure.persistence.UsuarioRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "app.security.local-account-recovery.enabled", havingValue = "true")
public class LocalAccountRecoveryService {
    private static final int MINIMUM_KEY_BYTES = 32;

    private final UsuarioRepository usuarioRepository;
    private final TokenSesionRepository tokenSesionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrail auditTrail;
    private final byte[] recoveryKey;

    public LocalAccountRecoveryService(
            UsuarioRepository usuarioRepository,
            TokenSesionRepository tokenSesionRepository,
            PasswordEncoder passwordEncoder,
            AuditTrail auditTrail,
            @Value("${app.security.local-account-recovery.key}") String recoveryKey) {
        this.usuarioRepository = usuarioRepository;
        this.tokenSesionRepository = tokenSesionRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditTrail = auditTrail;
        this.recoveryKey = recoveryKey.getBytes(StandardCharsets.UTF_8);
        if (this.recoveryKey.length < MINIMUM_KEY_BYTES) {
            throw new IllegalStateException("La clave de recuperación local debe tener al menos 32 bytes UTF-8");
        }
    }

    @Transactional
    public void recuperar(RecuperarCuentaLocalRequest request) {
        if (!MessageDigest.isEqual(recoveryKey, request.claveRecuperacion().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Clave de recuperación local inválida");
        }

        String identificador = request.identificador().trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.buscarParaAutenticacion(identificador).orElse(null);
        String passwordHash = passwordEncoder.encode(request.contrasenaNueva());
        if (usuario == null) return;

        usuario.setPasswordHash(passwordHash);
        usuario.reiniciarIntentosFallidos();
        usuarioRepository.save(usuario);
        tokenSesionRepository.revocarSesionesActivasDeUsuario(usuario.getId(), Instant.now());
        auditTrail.systemPasswordRecovery(usuario.getId());
    }
}
