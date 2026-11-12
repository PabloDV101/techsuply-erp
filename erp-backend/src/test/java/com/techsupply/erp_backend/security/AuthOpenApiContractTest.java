package com.techsupply.erp_backend.security;

import com.techsupply.erp_backend.common.api.ApiError;
import com.techsupply.erp_backend.security.api.*;
import com.techsupply.erp_backend.security.application.dto.*;
import jakarta.validation.Valid;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.*;

/** Compares the published contract to actual controller signatures and DTO records; no Spring context or database. */
class AuthOpenApiContractTest {
    private static Map<String, Object> contract;
    private static final List<Class<?>> CONTROLLERS = List.of(AuthController.class, AdminUsuariosController.class);

    @BeforeAll
    static void readContract() throws Exception {
        var options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Path file = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-auth.yaml");
        try (var stream = Files.newInputStream(file)) {
            contract = new Yaml(new SafeConstructor(options)).load(stream);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    private static Map<String, Object> schema(String name) {
        return map(map(map(contract.get("components")).get("schemas")).get(name));
    }

    static Stream<Arguments> records() {
        return Stream.of(Arguments.of("AuthError", ApiError.class),
            Arguments.of("LoginResponse", LoginResponse.class),
            Arguments.of("UsuarioResumen", LoginResponse.UsuarioResumen.class),
            Arguments.of("UsuarioResponse", UsuarioResponse.class),
            Arguments.of("RolResponse", RolResponse.class),
            Arguments.of("PermisoResponse", PermisoResponse.class));
    }

    @ParameterizedTest
    @MethodSource("records")
    void responseSchemaMatchesEveryRecordFieldAndType(String name, Class<?> dto) {
        var declared = schema(name);
        var properties = map(declared.get("properties"));
        var fields = Arrays.stream(dto.getRecordComponents()).map(RecordComponent::getName).toList();
        assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(fields);
        assertThat(new HashSet<>((List<?>) declared.get("required"))).isEqualTo(new HashSet<>(fields));
        assertThat(declared.get("additionalProperties")).isEqualTo(false);
        for (var field : dto.getRecordComponents()) {
            var property = map(properties.get(field.getName()));
            Class<?> type = field.getType();
            if (type.isRecord()) {
                assertThat(property.get("$ref")).isEqualTo("#/components/schemas/"+type.getSimpleName());
            } else if (Set.class.isAssignableFrom(type)) {
                assertThat(property.get("type")).isEqualTo("array");
                assertThat(property.get("uniqueItems")).isEqualTo(true);
                assertThat(map(property.get("items")).get("type")).isEqualTo("string");
            } else {
                String expected = type == Long.class || type == int.class ? "integer"
                    : type == boolean.class ? "boolean" : "string";
                assertThat(property.get("type")).as(name+"."+field.getName()).isEqualTo(expected);
                if (type == Long.class) assertThat(property.get("format")).isEqualTo("int64");
                if (type == java.time.Instant.class) assertThat(property.get("format")).isEqualTo("date-time");
            }
        }
    }

    private static String path(Class<?> controller, RequestMapping route) {
        return controller.getAnnotation(RequestMapping.class).value()[0].substring("/api/v1".length())+route.value()[0];
    }

    @Test
    void everyControllerOperationHasMatchingSuccessSchema() {
        var documented = map(contract.get("paths"));
        var actualOperations = new HashSet<String>();
        for (var controller : CONTROLLERS) {
            for (var method : controller.getDeclaredMethods()) {
                var route = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (route == null) continue;
                String path = path(controller, route);
                String verb = route.method()[0].name().toLowerCase(Locale.ROOT);
                actualOperations.add(verb+" "+path);
                var operation = map(map(documented.get(path)).get(verb));
                var responses = map(operation.get("responses"));
                Type returned = method.getGenericReturnType();
                boolean list = false;
                if (returned instanceof ParameterizedType parameterized) {
                    list = parameterized.getRawType() == List.class;
                    assertThat(parameterized.getRawType()).isIn(List.class, ResponseEntity.class);
                    returned = parameterized.getActualTypeArguments()[0];
                }
                Class<?> dto = (Class<?>) returned;
                if (dto == Void.class) {
                    assertThat(map(responses.get("204"))).doesNotContainKey("content");
                } else {
                    var success = map(map(map(responses.get("200")).get("content")).get("application/json"));
                    var responseSchema = map(success.get("schema"));
                    if (list) {
                        assertThat(responseSchema.get("type")).isEqualTo("array");
                        responseSchema = map(responseSchema.get("items"));
                    }
                    assertThat(responseSchema.get("$ref")).isEqualTo("#/components/schemas/"+dto.getSimpleName());
                }
            }
        }
        var declaredOperations = new HashSet<String>();
        documented.forEach((path, value) -> map(value).keySet().forEach(verb -> declaredOperations.add(verb+" "+path)));
        assertThat(declaredOperations).containsExactlyInAnyOrderElementsOf(actualOperations);
    }

    @Test
    void validatedControllerRequestsDeclare400WithUniformErrorSchema() {
        for (var controller : CONTROLLERS) {
            for (var method : controller.getDeclaredMethods()) {
                var route = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (route == null || Arrays.stream(method.getParameters()).noneMatch(p -> p.isAnnotationPresent(Valid.class))) continue;
                String path = path(controller, route);
                String verb = route.method()[0].name().toLowerCase(Locale.ROOT);
                var responses = map(map(map(map(contract.get("paths")).get(path)).get(verb)).get("responses"));
                assertThat(responses).as(verb+" "+path).containsKey("400");
                var error = map(map(map(map(responses.get("400")).get("content")).get("application/json")).get("schema"));
                assertThat(error.get("$ref")).isEqualTo("#/components/schemas/AuthError");
            }
        }
    }

    @Test
    void nullableDatabaseFieldsAreDocumentedWithoutMakingThemOptional() {
        for (String field : List.of("empleadoId", "clienteId", "ultimoAcceso")) {
            assertThat(map(map(schema("UsuarioResponse").get("properties")).get(field)).get("nullable")).isEqualTo(true);
        }
        for (String name : List.of("RolResponse", "PermisoResponse")) {
            assertThat(map(map(schema(name).get("properties")).get("descripcion")).get("nullable")).isEqualTo(true);
        }
    }
}
