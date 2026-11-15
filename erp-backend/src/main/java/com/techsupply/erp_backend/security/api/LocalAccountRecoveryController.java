package com.techsupply.erp_backend.security.api;

import com.techsupply.erp_backend.security.application.LocalAccountRecoveryService;
import com.techsupply.erp_backend.security.application.dto.RecuperarCuentaLocalRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.net.InetAddress;
import java.net.UnknownHostException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/auth/local-recovery")
@ConditionalOnProperty(name = "app.security.local-account-recovery.enabled", havingValue = "true")
public class LocalAccountRecoveryController {
    private final LocalAccountRecoveryService recoveryService;

    public LocalAccountRecoveryController(LocalAccountRecoveryService recoveryService) {
        this.recoveryService = recoveryService;
    }

    @PostMapping("")
    public ResponseEntity<Void> recuperar(
            @Valid @RequestBody RecuperarCuentaLocalRequest request,
            HttpServletRequest servletRequest) {
        if (!isLoopback(servletRequest.getRemoteAddr())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        recoveryService.recuperar(request);
        return ResponseEntity.noContent().build();
    }

    private boolean isLoopback(String remoteAddress) {
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException exception) {
            return false;
        }
    }
}
