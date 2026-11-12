package com.techsupply.erp_backend.security.infrastructure.security;

import java.util.Base64;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class OpaqueTokenServiceTest {
    private final OpaqueTokenService tokens = new OpaqueTokenService();

    @Test void tokenContains32RandomBytesAndNoDuplicatesInSample() {
        var sample = new HashSet<String>();
        for (int i = 0; i < 1000; i++) {
            String token = tokens.generarToken();
            assertThat(token).matches("[A-Za-z0-9_-]{43}");
            assertThat(Base64.getUrlDecoder().decode(token)).hasSize(32);
            assertThat(sample.add(token)).isTrue();
        }
    }

    @Test void sha256MatchesKnownVectorAndDoesNotStorePlainToken() {
        assertThat(tokens.calcularHash("abc"))
            .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(tokens.hashesIguales("abc", tokens.calcularHash("abc"))).isTrue();
        assertThat(tokens.hashesIguales("different", tokens.calcularHash("abc"))).isFalse();
    }
}
