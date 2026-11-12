package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.ventas.application.dto.CotizacionCreateRequest;
import jakarta.validation.Validation;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class CotizacionCreateRequestTest {
    @Test void validacionEnCascadaRechazaDatosInvalidosSinAplicarPoliticaComercial() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var line = new CotizacionCreateRequest.Linea(1L, new BigDecimal("1.000"), new BigDecimal("10.00"), BigDecimal.ZERO, new BigDecimal("16"));
            var valid = new CotizacionCreateRequest(1L, LocalDate.of(2026, 12, 1), "clave", List.of(line));
            assertThat(validator.validate(valid)).isEmpty();
            var invalidLine = new CotizacionCreateRequest.Linea(0L, new BigDecimal("0"), new BigDecimal("1.001"), new BigDecimal("101"), new BigDecimal("-1"));
            var invalid = new CotizacionCreateRequest(0L, null, " ", List.of(invalidLine));
            assertThat(validator.validate(invalid)).hasSize(8);
            assertThat(validator.validate(new CotizacionCreateRequest(1L, LocalDate.of(2026, 12, 1), "clave", List.of()))).hasSize(1);
        }
    }
}
