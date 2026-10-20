package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.maestros.api.CategoriaProductoController;
import com.techsupply.erp_backend.maestros.application.dto.ActualizarCategoriaRequest;
import com.techsupply.erp_backend.maestros.application.dto.CategoriaResponse;
import com.techsupply.erp_backend.maestros.application.dto.CrearCategoriaRequest;
import com.techsupply.erp_backend.maestros.application.dto.EstadoCategoriaRequest;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static org.assertj.core.api.Assertions.assertThat;

class CategoriaOpenApiContractTest {
    private static Map<String, Object> contract;

    @BeforeAll
    static void readContract() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Path file = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-crud-categorias.yaml");
        try (var stream = Files.newInputStream(file)) {
            contract = new Yaml(new SafeConstructor(options)).load(stream);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    @Test
    void documentedOperationsMatchControllerAndDeclareSecurityAndErrors() {
        Map<String, Object> paths = map(contract.get("paths"));
        Set<String> declared = paths.entrySet().stream()
            .flatMap(entry -> map(entry.getValue()).keySet().stream()
                .filter(key -> !key.equals("parameters"))
                .map(verb -> verb + " " + entry.getKey()))
            .collect(Collectors.toSet());
        Set<String> normalizedActual = Arrays.stream(CategoriaProductoController.class.getDeclaredMethods())
            .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
            .filter(route -> route != null)
            .map(route -> route.method()[0].name().toLowerCase() + " /categories"
                + (route.value().length == 0 ? "" : route.value()[0]))
            .collect(Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrderElementsOf(normalizedActual);
        for (String operation : declared) {
            String[] parts = operation.split(" ", 2);
            Map<String, Object> entry = map(map(paths.get(parts[1])).get(parts[0]));
            Map<String, Object> responses = map(entry.get("responses"));
            assertThat(responses).containsKeys("401", "403");
            assertThat(entry).containsKey("description");
        }
    }

    @Test
    void requestResponseAndErrorFieldsMatchRecords() {
        Map<String, Object> schemas = map(map(contract.get("components")).get("schemas"));
        for (Class<?> type : List.of(CrearCategoriaRequest.class, ActualizarCategoriaRequest.class,
                EstadoCategoriaRequest.class, CategoriaResponse.class)) {
            Map<String, Object> schema = map(schemas.get(type.getSimpleName()));
            Map<String, Object> properties = map(schema.get("properties"));
            assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(
                Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).toList());
        }
        assertThat(map(map(schemas.get("ApiError")).get("properties")).keySet())
            .containsExactlyInAnyOrder("status", "code", "message");
        assertThat(map(map(schemas.get("CategoriaPage")).get("properties")).keySet())
            .containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
    }
}
