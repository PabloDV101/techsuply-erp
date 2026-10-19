package com.techsupply.erp_backend.security.application;

import com.techsupply.erp_backend.security.application.dto.LoginRequest;
import com.techsupply.erp_backend.security.domain.model.TokenSesion;
import com.techsupply.erp_backend.security.domain.model.Usuario;
import com.techsupply.erp_backend.security.infrastructure.persistence.*;
import com.techsupply.erp_backend.security.infrastructure.security.OpaqueTokenService;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthServiceTest {
    private final UsuarioRepository users = mock(UsuarioRepository.class);
    private final TokenSesionRepository sessions = mock(TokenSesionRepository.class);
    private final PasswordEncoder passwords = mock(PasswordEncoder.class);
    private final AuthenticatedUserProvider actors = mock(AuthenticatedUserProvider.class);
    private final OpaqueTokenService tokens = new OpaqueTokenService();
    private final Instant now = Instant.parse("2026-10-19T12:00:00Z");
    private AuthService service;
    private Usuario user;

    @BeforeEach
    void setup() {
        when(passwords.encode(anyString())).thenReturn("dummy-hash");
        service = new AuthService(users, sessions, passwords, tokens, actors, Clock.fixed(now, ZoneOffset.UTC));
        user = new Usuario();
        user.setUsername("tester");
        user.setCorreo("tester@example.test");
        user.setPasswordHash("stored-hash");
    }

    private void found() { when(users.buscarParaAutenticacion("tester")).thenReturn(Optional.of(user)); }
    private void denied() {
        assertThatThrownBy(() -> service.login(new LoginRequest("tester", "wrong")))
            .isInstanceOfSatisfying(ResponseStatusException.class,
                ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        verify(sessions, never()).save(any());
    }

    @Test void successfulLoginPersistsOnlyDigestAndExpiry() {
        found();
        when(passwords.matches("correct", "stored-hash")).thenReturn(true);
        var response = service.login(new LoginRequest(" TESTER ", "correct"));
        var saved = ArgumentCaptor.forClass(TokenSesion.class);
        verify(sessions).save(saved.capture());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(tokens.calcularHash(response.accessToken()))
            .isNotEqualTo(response.accessToken());
        assertThat(response.expiresAt()).isEqualTo(now.plusSeconds(7200));
        assertThat(user.getUltimoAcceso()).isEqualTo(now);
    }

    @Test void wrongPasswordIncrementsAttempts() {
        found(); denied();
        assertThat(user.getIntentosFallidos()).isEqualTo((short) 1);
        verify(users).save(user);
    }

    @Test void unknownUserRunsDummyComparison() {
        when(users.buscarParaAutenticacion("tester")).thenReturn(Optional.empty());
        denied(); verify(passwords).matches("wrong", "dummy-hash");
    }

    @Test void inactiveUserCannotLogin() {
        found(); user.setActivo(false); denied();
        verify(passwords).matches("wrong", "dummy-hash");
        assertThat(user.getIntentosFallidos()).isZero();
    }

    @Test void fifthFailureLocksForFifteenMinutes() {
        found(); for (int i = 0; i < 4; i++) user.incrementarIntentosFallidos();
        denied();
        assertThat(user.getIntentosFallidos()).isEqualTo((short) 5);
        assertThat(user.getBloqueadoHasta()).isEqualTo(now.plusSeconds(900));
    }

    @Test void activeLockRejectsEvenCorrectPassword() {
        found(); user.setBloqueadoHasta(now.plusSeconds(1)); denied();
        verify(passwords).matches("wrong", "dummy-hash");
        verify(users, never()).save(any());
    }

    @Test void expiredLockStartsNewAttemptWindow() {
        found(); for (int i = 0; i < 5; i++) user.incrementarIntentosFallidos();
        user.setBloqueadoHasta(now); denied();
        assertThat(user.getIntentosFallidos()).isEqualTo((short) 1);
        assertThat(user.getBloqueadoHasta()).isNull();
    }

    @Test void successfulLoginResetsFailuresAndExpiredLock() {
        found(); user.incrementarIntentosFallidos(); user.setBloqueadoHasta(now.minusSeconds(1));
        when(passwords.matches("correct", "stored-hash")).thenReturn(true);
        service.login(new LoginRequest("tester", "correct"));
        assertThat(user.getIntentosFallidos()).isZero();
        assertThat(user.getBloqueadoHasta()).isNull();
    }

    @Test void logoutRevokesSession() {
        var session = new TokenSesion(user, tokens.calcularHash("token"), now, now.plusSeconds(7200));
        when(sessions.findByTokenHash(session.getTokenHash())).thenReturn(Optional.of(session));
        service.logout("token");
        assertThat(session.getRevocadoEn()).isEqualTo(now);
        verify(sessions).save(session);
    }

    @Test void repeatedLogoutDoesNotChangeRevocationTime() {
        var session = new TokenSesion(user, tokens.calcularHash("token"), now.minusSeconds(100), now.plusSeconds(7200));
        session.setRevocadoEn(now.minusSeconds(1));
        when(sessions.findByTokenHash(session.getTokenHash())).thenReturn(Optional.of(session));
        service.logout("token");
        assertThat(session.getRevocadoEn()).isEqualTo(now.minusSeconds(1));
        verify(sessions, never()).save(any());
    }

    @Test void meWithoutActorIsUnauthorized() {
        when(actors.currentUser()).thenReturn(Optional.empty());
        assertThatThrownBy(service::me).isInstanceOfSatisfying(ResponseStatusException.class,
            ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
    }
}
