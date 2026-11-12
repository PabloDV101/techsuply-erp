package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.maestros.api.*;
import com.techsupply.erp_backend.maestros.application.dto.*;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static org.assertj.core.api.Assertions.assertThat;

class MasterCatalogOpenApiContractTest {
    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    private static Map<String, Object> contract() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Path file = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-sprint-1-maestros-compras.yaml");
        try (var stream = Files.newInputStream(file)) {
            return new Yaml(new SafeConstructor(options)).load(stream);
        }
    }

    @Test void implementedRoutesAndDtosMatchDraft() throws Exception {
        Map<String, Object> root = contract();
        Map<String, Object> paths = map(root.get("paths"));
        Map<String, Object> schemas = map(map(root.get("components")).get("schemas"));
        for (Object[] entry : new Object[][] {
                {"departments", DepartamentoController.class, DepartamentoWriteRequest.class, DepartamentoResponse.class, "Department"},
                {"suppliers", ProveedorController.class, ProveedorWriteRequest.class, ProveedorResponse.class, "Supplier"},
                {"warehouses", AlmacenController.class, AlmacenWriteRequest.class, AlmacenResponse.class, "Warehouse"}}) {
            String path = "/" + entry[0];
            Class<?> controller = (Class<?>) entry[1];
            Set<String> actual = Arrays.stream(controller.getDeclaredMethods())
                .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
                .filter(route -> route != null)
                .map(route -> route.method()[0].name().toLowerCase() + " " + path
                    + (route.value().length == 0 ? "" : route.value()[0]))
                .collect(Collectors.toSet());
            Set<String> documented = paths.entrySet().stream()
                .filter(item -> item.getKey().equals(path) || item.getKey().startsWith(path + "/"))
                .flatMap(item -> map(item.getValue()).keySet().stream()
                    .filter(key -> List.of("get", "post", "put", "patch", "delete").contains(key))
                    .map(key -> key + " " + item.getKey()))
                .collect(Collectors.toSet());
            assertThat(documented).containsExactlyInAnyOrderElementsOf(actual);
            for (String route : documented) {
                String[] parts = route.split(" ", 2);
                Map<String, Object> pathItem = map(paths.get(parts[1]));
                assertThat(pathItem.get("x-implementation-status")).isEqualTo("implemented");
                Map<String, Object> operation = map(pathItem.get(parts[0]));
                assertThat(map(operation.get("responses"))).containsKeys("401", "403");
            }
            String schema = (String) entry[4];
            assertThat(map(map(schemas.get(schema + "Write")).get("properties")).keySet())
                .containsExactlyInAnyOrderElementsOf(recordFields((Class<?>) entry[2]));
            Set<String> responseFields = recordFields((Class<?>) entry[3]);
            Set<String> documentedFields = new java.util.HashSet<>(map(map(schemas.get(schema + "Write")).get("properties")).keySet());
            Map<String, Object> responseSchema = map(schemas.get(schema));
            for (Object fragment : (List<?>) responseSchema.get("allOf")) {
                Map<String, Object> part = map(fragment);
                if (part.containsKey("properties")) documentedFields.addAll(map(part.get("properties")).keySet());
            }
            assertThat(documentedFields).containsExactlyInAnyOrderElementsOf(responseFields);
            var listMethod = Arrays.stream(controller.getDeclaredMethods())
                .filter(method -> method.getName().equals("listar")).findFirst().orElseThrow();
            Set<String> actualParams = Arrays.stream(listMethod.getParameters())
                .filter(parameter -> parameter.isAnnotationPresent(RequestParam.class))
                .map(parameter -> parameter.getName()).collect(Collectors.toSet());
            Map<String, Object> listOperation = map(map(paths.get(path)).get("get"));
            Set<String> documentedParams = ((List<?>) listOperation.get("parameters")).stream()
                .map(item -> map(item).get("$ref").toString())
                .map(ref -> ref.substring(ref.lastIndexOf('/') + 1))
                .map(ref -> map(map(map(root.get("components")).get("parameters")).get(ref)).get("name").toString())
                .collect(Collectors.toSet());
            assertThat(documentedParams).containsExactlyInAnyOrderElementsOf(actualParams);
        }
    }

    private static Set<String> recordFields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }

    @Test void productContractHasMatchingOperationsAndFields() throws Exception {
        Map<String, Object> root = contract();
        Map<String, Object> paths = map(root.get("paths"));
        Set<String> actual = Arrays.stream(ProductoController.class.getDeclaredMethods())
            .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
            .filter(route -> route != null)
            .map(route -> route.method()[0].name().toLowerCase() + " /products"
                + (route.value().length == 0 ? "" : route.value()[0])).collect(Collectors.toSet());
        Set<String> declared = paths.entrySet().stream().filter(e -> e.getKey().equals("/products") || e.getKey().startsWith("/products/"))
            .flatMap(e -> map(e.getValue()).keySet().stream().filter(k -> List.of("get", "post", "put", "patch").contains(k))
                .map(k -> k + " " + e.getKey())).collect(Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
        Map<String, Object> schemas = map(map(root.get("components")).get("schemas"));
        assertThat(map(map(schemas.get("ProductWrite")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(recordFields(ProductoWriteRequest.class));
        Set<String> fields = new java.util.HashSet<>(recordFields(ProductoWriteRequest.class));
        fields.add("id"); fields.add("activo");
        assertThat(fields).containsExactlyInAnyOrderElementsOf(recordFields(ProductoResponse.class));
    }
}
