package com.techsupply.erp_backend.inventario;

import com.techsupply.erp_backend.inventario.api.ExistenciaController;
import com.techsupply.erp_backend.inventario.application.dto.ExistenciaResponse;
import java.lang.reflect.RecordComponent;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.*;

class InventarioOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }
    @Test void stockFieldsAndParametersMatchController() throws Exception {
        LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
        Map<String,Object> root;
        try (var stream = Files.newInputStream(Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-sprint-1-maestros-compras.yaml"))) {
            root = new Yaml(new SafeConstructor(options)).load(stream);
        }
        var components = map(root.get("components")); var path = map(map(root.get("paths")).get("/inventory/stock"));
        assertThat(path.get("x-implementation-status")).isEqualTo("implemented");
        var schema = map(map(components.get("schemas")).get("Stock"));
        assertThat(map(schema.get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(Arrays.stream(ExistenciaResponse.class.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet()));
        var method = Arrays.stream(ExistenciaController.class.getDeclaredMethods()).filter(m -> m.getName().equals("listar")).findFirst().orElseThrow();
        Set<String> actual = Arrays.stream(method.getParameters()).filter(p -> p.isAnnotationPresent(RequestParam.class)).map(p -> p.getName()).collect(Collectors.toSet());
        Set<String> declared = ((List<?>)map(path.get("get")).get("parameters")).stream().map(InventarioOpenApiContractTest::map).map(item -> item.containsKey("$ref")
            ? map(map(components.get("parameters")).get(item.get("$ref").toString().substring(item.get("$ref").toString().lastIndexOf('/') + 1))).get("name").toString()
            : item.get("name").toString()).collect(Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
        assertThat(map(map(path.get("get")).get("responses"))).containsKeys("200", "400", "401", "403");
    }
}
