package com.techsupply.erp_backend.security.api;

import com.techsupply.erp_backend.security.application.AuthService;
import com.techsupply.erp_backend.security.application.dto.LoginRequest;
import com.techsupply.erp_backend.security.application.dto.LoginResponse;
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
    public AuthController(AuthService authService) { this.authService = authService; }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = authorization != null && authorization.startsWith("Bearer ") ? authorization.substring(7).trim() : null;
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public UsuarioResponse me() { return authService.me(); }
}
