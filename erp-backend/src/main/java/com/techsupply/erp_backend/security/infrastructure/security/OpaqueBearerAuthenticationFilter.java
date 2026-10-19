package com.techsupply.erp_backend.security.infrastructure.security;

import com.techsupply.erp_backend.security.domain.model.Permiso;
import com.techsupply.erp_backend.security.domain.model.Rol;
import com.techsupply.erp_backend.security.domain.model.TokenSesion;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import com.techsupply.erp_backend.security.infrastructure.persistence.TokenSesionRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class OpaqueBearerAuthenticationFilter extends OncePerRequestFilter {
    private final TokenSesionRepository tokenSesionRepository;
    private final OpaqueTokenService tokenService;

    public OpaqueBearerAuthenticationFilter(TokenSesionRepository tokenSesionRepository, OpaqueTokenService tokenService) {
        this.tokenSesionRepository = tokenSesionRepository;
        this.tokenService = tokenService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ") && SecurityContextHolder.getContext().getAuthentication() == null) {
            String rawToken = header.substring(7).trim();
            if (!rawToken.isEmpty()) autenticarToken(rawToken);
        }
        filterChain.doFilter(request, response);
    }

    private void autenticarToken(String rawToken) {
        String hash = tokenService.calcularHash(rawToken);
        TokenSesion sesion = tokenSesionRepository
            .findByTokenHashAndRevocadoEnIsNullAndVenceEnAfter(hash, Instant.now())
            .orElse(null);
        if (sesion == null) return;

        Usuario usuario = sesion.getUsuario();
        Instant ahora = Instant.now();
        if (!usuario.isActivo() || (usuario.getBloqueadoHasta() != null && usuario.getBloqueadoHasta().isAfter(ahora))) return;

        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        for (Rol rol : usuario.getRoles()) {
            if (!rol.isActivo()) continue;
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.getCodigo()));
            for (Permiso permiso : rol.getPermisos()) {
                authorities.add(new SimpleGrantedAuthority(permiso.getCodigo()));
            }
        }
        SecurityActor actor = new SecurityActor(usuario.getId(), usuario.getUsername());
        var authentication = new UsernamePasswordAuthenticationToken(actor, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
