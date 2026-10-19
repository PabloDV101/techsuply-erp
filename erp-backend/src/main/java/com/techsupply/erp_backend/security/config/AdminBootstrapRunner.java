package com.techsupply.erp_backend.security.config;

import com.techsupply.erp_backend.security.domain.model.Rol;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import com.techsupply.erp_backend.security.infrastructure.persistence.RolRepository;
import com.techsupply.erp_backend.security.infrastructure.persistence.UsuarioRepository;
import java.util.Locale;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Bootstrap opcional de un administrador inicial. Está desactivado por defecto. */
@Component
@ConditionalOnProperty(name = "app.security.bootstrap-admin.enabled", havingValue = "true")
public class AdminBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);
    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String correo;
    private final String password;

    public AdminBootstrapRunner(UsuarioRepository usuarioRepository, RolRepository rolRepository,
                                PasswordEncoder passwordEncoder,
                                @Value("${app.security.bootstrap-admin.username}") String username,
                                @Value("${app.security.bootstrap-admin.email}") String correo,
                                @Value("${app.security.bootstrap-admin.password}") String password) {
        this.usuarioRepository = usuarioRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.correo = correo;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (usuarioRepository.existsByUsernameIgnoreCase(username.trim()) || usuarioRepository.existsByCorreoIgnoreCase(correo.trim())) {
            log.warn("El usuario configurado para bootstrap ya existe; no se realizaron cambios. Desactiva el bootstrap.");
            return;
        }
        if (password.length() < 12 || password.length() > 72) {
            throw new IllegalStateException("La contraseña de bootstrap debe tener entre 12 y 72 caracteres");
        }
        Rol admin = rolRepository.findByCodigoAndActivoTrue("ADMINISTRADOR")
            .orElseThrow(() -> new IllegalStateException("Falta el rol ADMINISTRADOR; revisa las migraciones Flyway"));
        Usuario usuario = new Usuario();
        usuario.setUsername(username.trim().toLowerCase(Locale.ROOT));
        usuario.setCorreo(correo.trim().toLowerCase(Locale.ROOT));
        usuario.setPasswordHash(passwordEncoder.encode(password));
        usuario.setActivo(true);
        usuario.setRoles(Set.of(admin));
        usuarioRepository.save(usuario);
        log.warn("Se creó la cuenta administradora inicial '{}'. Desactiva app.security.bootstrap-admin.enabled inmediatamente.", usuario.getUsername());
    }
}
