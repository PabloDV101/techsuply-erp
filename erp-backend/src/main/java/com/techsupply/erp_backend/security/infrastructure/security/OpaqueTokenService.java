package com.techsupply.erp_backend.security.infrastructure.security;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class OpaqueTokenService {
    private final SecureRandom secureRandom = new SecureRandom();

    public String generarToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Se almacena el digest, no el token Bearer original. */
    public String calcularHash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no está disponible en esta JVM", ex);
        }
    }

    public boolean hashesIguales(String rawToken, String storedHash) {
        return MessageDigest.isEqual(
            calcularHash(rawToken).getBytes(java.nio.charset.StandardCharsets.US_ASCII),
            storedHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII)
        );
    }
}
