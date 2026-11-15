package com.techsupply.erp_backend.security.api;

import com.techsupply.erp_backend.security.application.AuthService;
import com.techsupply.erp_backend.security.application.PublicRegistrationService;
import com.techsupply.erp_backend.security.application.dto.CambiarContrasenaRequest;
import com.techsupply.erp_backend.security.application.dto.LoginRequest;
import com.techsupply.erp_backend.security.application.dto.LoginResponse;
import com.techsupply.erp_backend.security.application.dto.RegistroUsuarioRequest;
import com.techsupply.erp_backend.security.application.dto.UsuarioResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService authService;
    private final PublicRegistrationService registrationService;
    public AuthController(AuthService authService, PublicRegistrationService registrationService) {
        this.authService = authService;
        this.registrationService = registrationService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/register")
    public UsuarioResponse registrar(@Valid @RequestBody RegistroUsuarioRequest request) {
        return registrationService.registrar(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7) ? authorization.substring(7).trim() : null;
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UsuarioResponse me() { return authService.me(); }

    @PostMapping("/password")
    public ResponseEntity<Void> cambiarContrasena(@Valid @RequestBody CambiarContrasenaRequest request) {
        authService.cambiarContrasena(request);
        return ResponseEntity.noContent().build();
    }
}
