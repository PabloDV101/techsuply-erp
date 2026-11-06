package com.techsupply.erp_backend.compras;

import com.techsupply.erp_backend.compras.api.RecepcionCompraController;
import com.techsupply.erp_backend.compras.application.dto.CrearRecepcionRequest;
import com.techsupply.erp_backend.compras.application.dto.RecepcionCompraResponse;
import java.lang.reflect.RecordComponent;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.*;

class RecepcionCompraOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }
    private static Set<String> fields(Class<?> type) { return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet()); }
    @Test void routesParametersAndDtosMatchContract() throws Exception {
        LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false);
        Map<String,Object> root;
        try (var in = Files.newInputStream(Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-sprint-1-maestros-compras.yaml"))) { root = new Yaml(new SafeConstructor(options)).load(in); }
        var paths = map(root.get("paths"));
        Set<String> actual = Arrays.stream(RecepcionCompraController.class.getDeclaredMethods()).map(m -> AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class))
            .filter(Objects::nonNull).map(r -> r.method()[0].name().toLowerCase(Locale.ROOT) + " /purchase-receipts" + (r.value().length == 0 ? "" : r.value()[0])).collect(Collectors.toSet());
        Set<String> declared = paths.entrySet().stream().filter(e -> e.getKey().equals("/purchase-receipts") || e.getKey().startsWith("/purchase-receipts/"))
            .flatMap(e -> map(e.getValue()).keySet().stream().filter(k -> List.of("get","post","put","patch","delete").contains(k)).map(k -> k + " " + e.getKey())).collect(Collectors.toSet());
        assertThat(declared).containsExactlyInAnyOrderElementsOf(actual);
        for (String op : declared) { String[] parts = op.split(" ",2); var path = map(paths.get(parts[1]));
            assertThat(path.get("x-implementation-status")).isEqualTo("implemented");
            assertThat(map(map(path.get(parts[0])).get("responses"))).containsKeys("401","403"); }
        var components = map(root.get("components")); var schemas = map(components.get("schemas"));
        assertThat(map(map(schemas.get("PurchaseReceiptCreate")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(CrearRecepcionRequest.class));
        assertThat(map(map(schemas.get("PurchaseReceiptLineCreate")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(CrearRecepcionRequest.Linea.class));
        assertThat(map(map(schemas.get("PurchaseReceipt")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(RecepcionCompraResponse.class));
        var line = new HashSet<>(fields(CrearRecepcionRequest.Linea.class)); line.add("id");
        assertThat(line).containsExactlyInAnyOrderElementsOf(fields(RecepcionCompraResponse.Linea.class));
        var method = Arrays.stream(RecepcionCompraController.class.getDeclaredMethods()).filter(m -> m.getName().equals("listar")).findFirst().orElseThrow();
        var actualParams = Arrays.stream(method.getParameters()).filter(p -> p.isAnnotationPresent(RequestParam.class)).map(p -> p.getName()).collect(Collectors.toSet());
        var list = map(map(paths.get("/purchase-receipts")).get("get"));
        Set<String> documented = ((List<?>) list.get("parameters")).stream().map(RecepcionCompraOpenApiContractTest::map).map(item -> item.containsKey("$ref")
            ? map(map(components.get("parameters")).get(item.get("$ref").toString().substring(item.get("$ref").toString().lastIndexOf('/')+1))).get("name").toString()
            : item.get("name").toString()).collect(Collectors.toSet());
        assertThat(documented).containsExactlyInAnyOrderElementsOf(actualParams);
    }
}
