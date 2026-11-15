package com.techsupply.erp_backend.security.application;

import com.techsupply.erp_backend.audit.application.AuditTrail;
import com.techsupply.erp_backend.security.application.dto.RegistroUsuarioRequest;
import com.techsupply.erp_backend.security.application.dto.UsuarioResponse;
import com.techsupply.erp_backend.security.domain.model.Rol;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import com.techsupply.erp_backend.security.infrastructure.persistence.RolRepository;
import com.techsupply.erp_backend.security.infrastructure.persistence.UsuarioRepository;
import java.util.Locale;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PublicRegistrationService {
    private static final String BASIC_ROLE = "USUARIO_BASICO";

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditTrail auditTrail;

    public PublicRegistrationService(
            UsuarioRepository usuarioRepository,
            RolRepository rolRepository,
            PasswordEncoder passwordEncoder,
            AuditTrail auditTrail) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditTrail = auditTrail;
    }

    @Transactional
    public UsuarioResponse registrar(RegistroUsuarioRequest request) {
        String username = normalizar(request.username());
        String correo = normalizar(request.correo());

        usuarioRepository.bloquearAdministracion();
        if (usuarioRepository.existsByUsernameIgnoreCaseOrCorreoIgnoreCase(username, username)
                || usuarioRepository.existsByUsernameIgnoreCaseOrCorreoIgnoreCase(correo, correo)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "El nombre de usuario o correo ya está registrado");
        }

        Rol basicRole = rolRepository.findByCodigoAndActivoTrue(BASIC_ROLE)
            .orElseThrow(() -> new IllegalStateException("Falta el rol USUARIO_BASICO; revisa las migraciones Flyway"));
        Usuario usuario = new Usuario();
        usuario.setUsername(username);
        usuario.setCorreo(correo);
        usuario.setPasswordHash(passwordEncoder.encode(request.password()));
        usuario.setActivo(true);
        usuario.setRoles(Set.of(basicRole));

        Usuario created = usuarioRepository.saveAndFlush(usuario);
        auditTrail.publicAccountRegistration(created.getId());
        return AuthService.usuarioResponse(created);
    }

    private String normalizar(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
