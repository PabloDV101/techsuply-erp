package com.techsupply.erp_backend.security.application;

import com.techsupply.erp_backend.security.application.dto.LoginRequest;
import com.techsupply.erp_backend.security.application.dto.LoginResponse;
import com.techsupply.erp_backend.security.application.dto.UsuarioResponse;
import com.techsupply.erp_backend.security.domain.model.Permiso;
import com.techsupply.erp_backend.security.domain.model.Rol;
import com.techsupply.erp_backend.security.domain.model.TokenSesion;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import com.techsupply.erp_backend.security.infrastructure.persistence.TokenSesionRepository;
import com.techsupply.erp_backend.security.infrastructure.persistence.UsuarioRepository;
import com.techsupply.erp_backend.security.infrastructure.security.OpaqueTokenService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AuthService {
    private static final int MAX_INTENTOS_FALLIDOS = 5;
    private static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);
    private static final Duration DURACION_SESION = Duration.ofHours(2);

    private final Clock clock;
    private final String dummyPasswordHash;
    private final UsuarioRepository usuarioRepository;
    private final TokenSesionRepository tokenSesionRepository;
    private final PasswordEncoder passwordEncoder;
    private final OpaqueTokenService tokenService;
    private final AuthenticatedUserProvider authenticatedUserProvider;

    public AuthService(UsuarioRepository usuarioRepository,
                       TokenSesionRepository tokenSesionRepository,
                       PasswordEncoder passwordEncoder,
                       OpaqueTokenService tokenService,
                       AuthenticatedUserProvider authenticatedUserProvider, Clock clock) {
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(java.util.UUID.randomUUID().toString());
        this.usuarioRepository = usuarioRepository;
        this.tokenSesionRepository = tokenSesionRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.authenticatedUserProvider = authenticatedUserProvider;
    }

    @Transactional(noRollbackFor = ResponseStatusException.class)
    public LoginResponse login(LoginRequest request) {
        String identificador = request.identificador().trim().toLowerCase(java.util.Locale.ROOT);
        Usuario usuario = usuarioRepository.buscarParaAutenticacion(identificador).orElse(null);
        Instant ahora = clock.instant();
        if (usuario == null || !usuario.isActivo()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw credencialesInvalidas();
        }
        if (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora)) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw credencialesInvalidas();
        }
        if (usuario.getBloqueadoHasta() != null) usuario.reiniciarIntentosFallidos();

        if (!passwordEncoder.matches(request.password(), usuario.getPasswordHash())) {
            usuario.incrementarIntentosFallidos();
            if (usuario.getIntentosFallidos() >= MAX_INTENTOS_FALLIDOS) {
                usuario.setBloqueadoHasta(ahora.plus(DURACION_BLOQUEO));
            }
            usuarioRepository.save(usuario);
            throw credencialesInvalidas();
        }

        usuario.reiniciarIntentosFallidos();
        usuario.setUltimoAcceso(ahora);
        usuarioRepository.save(usuario);

        String tokenPlano = tokenService.generarToken();
        Instant vence = ahora.plus(DURACION_SESION);
        tokenSesionRepository.save(new TokenSesion(usuario, tokenService.calcularHash(tokenPlano), ahora, vence));
        return new LoginResponse(tokenPlano, "Bearer", vence, resumen(usuario));
    }

    @Transactional
    public void logout(String bearerToken) {
        if (bearerToken == null || bearerToken.isBlank()) return;
        tokenSesionRepository.findByTokenHash(tokenService.calcularHash(bearerToken))
            .ifPresent(token -> {
                if (token.getRevocadoEn() == null) {
                    token.setRevocadoEn(clock.instant());
                    tokenSesionRepository.save(token);
                }
            });
    }

    @Transactional(readOnly = true)
    public UsuarioResponse me() {
        AuthenticatedActor actor = authenticatedUserProvider.currentUser()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autenticación requerida"));
        Usuario usuario = usuarioRepository.buscarConRolesYPermisosPorId(actor.usuarioId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "La cuenta ya no existe"));
        return usuarioResponse(usuario);
    }

    private ResponseStatusException credencialesInvalidas() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas o cuenta no disponible");
    }

    public static LoginResponse.UsuarioResumen resumen(Usuario usuario) {
        Set<String> roles = usuario.getRoles().stream().filter(Rol::isActivo)
            .map(Rol::getCodigo).collect(Collectors.toCollection(TreeSet::new));
        Set<String> permisos = usuario.getRoles().stream().filter(Rol::isActivo)
            .flatMap(rol -> rol.getPermisos().stream()).map(Permiso::getCodigo)
            .collect(Collectors.toCollection(TreeSet::new));
        return new LoginResponse.UsuarioResumen(usuario.getId(), usuario.getUsername(), usuario.getCorreo(), roles, permisos);
    }

    public static UsuarioResponse usuarioResponse(Usuario usuario) {
        LoginResponse.UsuarioResumen resumen = resumen(usuario);
        return new UsuarioResponse(usuario.getId(), usuario.getUsername(), usuario.getCorreo(), usuario.isActivo(),
            usuario.getEmpleadoId(), usuario.getClienteId(), usuario.getUltimoAcceso(), resumen.roles(), resumen.permisos());
    }
}
