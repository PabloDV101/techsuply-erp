package com.techsupply.erp_backend.maestros;

import com.techsupply.erp_backend.maestros.api.ClienteController;
import com.techsupply.erp_backend.maestros.application.dto.ClienteResponse;
import com.techsupply.erp_backend.maestros.application.dto.ClienteWriteRequest;
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
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.*;

class ClienteOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }
    private static Set<String> fields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }
    @Test void customerRoutesDtosAndStatusMatchContract() throws Exception {
        LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        Path file = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-sprint-3-ventas.yaml");
        Map<String,Object> root;
        try (var stream = Files.newInputStream(file)) { root = new Yaml(new SafeConstructor(options)).load(stream); }
        assertThat(root.get("x-implementation-status")).isEqualTo("implemented");
        Map<String,Object> paths = map(root.get("paths"));
        Set<String> actual = Arrays.stream(ClienteController.class.getDeclaredMethods())
            .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
            .filter(route -> route != null).map(route -> route.method()[0].name().toLowerCase() + " /customers"
                + (route.value().length == 0 ? "" : route.value()[0])).collect(Collectors.toSet());
        Set<String> declared = paths.entrySet().stream().filter(e -> e.getKey().startsWith("/customers"))
            .flatMap(e -> map(e.getValue()).keySet().stream().filter(k -> List.of("get", "post", "put", "patch", "delete").contains(k))
                .map(k -> k + " " + e.getKey())).collect(Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
        for (String route : List.of("/customers", "/customers/{id}", "/customers/{id}/status"))
            assertThat(map(paths.get(route)).get("x-implementation-status")).isEqualTo("implemented");
        Map<String,Object> schemas = map(map(root.get("components")).get("schemas"));
        assertThat(map(map(schemas.get("ClienteWrite")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(ClienteWriteRequest.class));
        assertThat(map(map(schemas.get("Cliente")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(ClienteResponse.class));
        Map<String,Object> customer = map(paths.get("/customers"));
        assertThat(map(map(customer.get("post")).get("responses"))).containsKeys("201", "400", "401", "403", "409");
        assertThat(map(map(customer.get("get")).get("responses"))).containsKeys("200", "400", "401", "403");
        assertThat(map(paths.get("/sales/orders/{id}/cancel")).get("x-implementation-status")).isEqualTo("implemented");
    }
}
