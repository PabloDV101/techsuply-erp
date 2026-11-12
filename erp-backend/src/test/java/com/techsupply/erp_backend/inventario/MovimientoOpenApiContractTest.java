package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.api.MovimientoController;
import com.techsupply.erp_backend.inventario.application.dto.MovimientoResponse;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.assertThat;

class MovimientoOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object v) { return (Map<String, Object>) v; }
    @Test void implementedRouteFieldsAndParametersMatchController() throws Exception {
        LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
        Map<String, Object> root;
        try (var stream = Files.newInputStream(Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-sprint-2-inventario.yaml"))) {
            root = new Yaml(new SafeConstructor(options)).load(stream);
        }
        assertThat(root.get("x-implementation-status")).isEqualTo("implemented");
        var components = map(root.get("components"));
        var path = map(map(root.get("paths")).get("/inventory/movements"));
        assertThat(path.get("x-implementation-status")).isEqualTo("implemented");
        var schema = map(map(components.get("schemas")).get("Movimiento"));
        Set<String> fields = Arrays.stream(MovimientoResponse.class.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(map(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields);
        var method = Arrays.stream(MovimientoController.class.getDeclaredMethods()).filter(m -> m.getName().equals("listar")).findFirst().orElseThrow();
        Set<String> actual = Arrays.stream(method.getParameters()).filter(p -> p.isAnnotationPresent(RequestParam.class)).map(p -> p.getName()).collect(Collectors.toSet());
        Set<String> declared = ((List<?>) map(path.get("get")).get("parameters")).stream().map(MovimientoOpenApiContractTest::map).map(item -> {
            String name = item.get("$ref").toString();
            return map(map(components.get("parameters")).get(name.substring(name.lastIndexOf('/') + 1))).get("name").toString();
        }).collect(Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
        assertThat(map(map(path.get("get")).get("responses"))).containsKeys("200", "400", "401", "403");
    }
}
