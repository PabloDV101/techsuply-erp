package com.techsupply.erp_backend.common.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalApiExceptionHandlerTest {
    private MockMvc mvc;
    private LocalValidatorFactoryBean validator;

    @BeforeEach
    void setUp() {
        validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mvc = MockMvcBuilders.standaloneSetup(new ValidationProbeController())
            .setControllerAdvice(new GlobalApiExceptionHandler())
            .setValidator(validator)
            .build();
    }

    @Test
    void acceptsValidDto() throws Exception {
        mvc.perform(post("/validation/probe").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Producto\",\"cantidad\":5}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nombre").value("Producto"))
            .andExpect(jsonPath("$.cantidad").value(5));
    }

    @Test
    void rejectsMissingRequiredFieldWithStandardError() throws Exception {
        assertBadRequest("{\"cantidad\":5}", "nombre");
    }

    @Test
    void rejectsBlankStringWithStandardError() throws Exception {
        assertBadRequest("{\"nombre\":\"   \",\"cantidad\":5}", "nombre");
    }

    @Test
    void rejectsValueOutsideLengthAndNumericRanges() throws Exception {
        assertBadRequest("{\"nombre\":\"nombre demasiado largo\",\"cantidad\":5}", "nombre");
        assertBadRequest("{\"nombre\":\"Producto\",\"cantidad\":0}", "cantidad");
        assertBadRequest("{\"nombre\":\"Producto\",\"cantidad\":101}", "cantidad");
    }

    @Test
    void rejectsInvalidPathParameterWithStandardError() throws Exception {
        mvc.perform(get("/validation/probe/not-a-number"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void rejectsMalformedJsonWithStandardError() throws Exception {
        mvc.perform(post("/validation/probe").contentType(MediaType.APPLICATION_JSON)
                .content("{malformed"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value("El cuerpo o los parámetros de la solicitud no son válidos"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        mvc.perform(get("/validation/probe/failure"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.status").value(500))
            .andExpect(jsonPath("$.code").value("INTERNAL_SERVER_ERROR"))
            .andExpect(jsonPath("$.message").value("Error interno del servidor"))
            .andExpect(jsonPath("$.trace").doesNotExist())
            .andExpect(jsonPath("$.sql").doesNotExist());
    }

    @Test
    void mapsAuthorizationDenialToForbidden() throws Exception {
        mvc.perform(get("/validation/probe/denied"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.code").value("FORBIDDEN"))
            .andExpect(jsonPath("$.message").value("Permiso insuficiente"));
    }

    private void assertBadRequest(String json, String field) throws Exception {
        mvc.perform(post("/validation/probe").contentType(MediaType.APPLICATION_JSON).content(json))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value(400))
            .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString(field)));
    }

    @RestController
    @RequestMapping("/validation/probe")
    static class ValidationProbeController {
        @PostMapping
        ProbeResponse create(@Valid @RequestBody ProbeRequest request) {
            return new ProbeResponse(request.nombre(), request.cantidad());
        }

        @GetMapping("/{id}")
        ProbeResponse find(@PathVariable("id") Long id) {
            return new ProbeResponse("Producto " + id, 1);
        }

        @GetMapping("/failure")
        ProbeResponse failure() {
            throw new IllegalStateException("private SQL details: SELECT * FROM usuarios");
        }

        @GetMapping("/denied")
        ProbeResponse denied() {
            throw new AccessDeniedException("private authorization detail");
        }
    }

    record ProbeRequest(@NotBlank @Size(min = 3, max = 10) String nombre, @Min(1) @Max(100) int cantidad) {}
    record ProbeResponse(String nombre, int cantidad) {}
}
