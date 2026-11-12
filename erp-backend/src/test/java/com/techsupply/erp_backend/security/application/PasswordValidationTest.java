package com.techsupply.erp_backend.security.application;

import com.techsupply.erp_backend.security.application.dto.CrearUsuarioRequest;
import com.techsupply.erp_backend.security.application.dto.LoginRequest;
import jakarta.validation.Validation;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordValidationTest {
    @Test void rejectsUnicodeOver72BytesForLoginAndCreation() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            assertThat(validator.validate(new LoginRequest("test", "é".repeat(37)))).isNotEmpty();
            assertThat(validator.validate(new CrearUsuarioRequest("test", "test@example.test", "é".repeat(37),
                Set.of("ADMINISTRADOR"), null, null))).isNotEmpty();
            assertThat(validator.validate(new LoginRequest("test", "é".repeat(36)))).isEmpty();
            assertThat(validator.validate(new LoginRequest(" ", ""))).hasSizeGreaterThanOrEqualTo(2);
        }
    }
}
