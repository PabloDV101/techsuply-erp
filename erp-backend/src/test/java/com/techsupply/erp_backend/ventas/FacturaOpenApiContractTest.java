package com.techsupply.erp_backend.ventas;

import com.techsupply.erp_backend.ventas.api.FacturaController;
import com.techsupply.erp_backend.ventas.application.dto.FacturaCreateRequest;
import com.techsupply.erp_backend.ventas.application.dto.FacturaResponse;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
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

class FacturaOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }
    private static Set<String> fields(Class<?> type) { return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet()); }
    @Test void rutasDtoYEstadosCoinciden() throws Exception {
        LoaderOptions options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        Path file = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-sprint-3-ventas.yaml");
        Map<String,Object> root;
        try (var stream = Files.newInputStream(file)) { root = new Yaml(new SafeConstructor(options)).load(stream); }
        Map<String,Object> paths = map(root.get("paths"));
        Set<String> actual = Arrays.stream(FacturaController.class.getDeclaredMethods())
            .map(m -> AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class)).filter(Objects::nonNull)
            .map(r -> r.method()[0].name().toLowerCase(Locale.ROOT) + " /sales/invoices" + (r.value().length == 0 ? "" : r.value()[0])).collect(Collectors.toSet());
        Set<String> documented = paths.entrySet().stream().filter(e -> e.getKey().equals("/sales/invoices") || e.getKey().equals("/sales/invoices/{id}"))
            .flatMap(e -> map(e.getValue()).keySet().stream().filter(k -> List.of("get","post","put","patch","delete").contains(k)).map(k -> k + " " + e.getKey())).collect(Collectors.toSet());
        assertThat(documented).containsExactlyInAnyOrderElementsOf(actual);
        for (String route : List.of("/sales/invoices", "/sales/invoices/{id}"))
            assertThat(map(paths.get(route)).get("x-implementation-status")).isEqualTo("implemented");
        Map<String,Object> schemas=map(map(root.get("components")).get("schemas"));
        assertThat(map(map(schemas.get("FacturaCreate")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(FacturaCreateRequest.class));
        assertThat(map(map(schemas.get("Factura")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(FacturaResponse.class));
        assertThat(map(map(schemas.get("FacturaLinea")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(FacturaResponse.Linea.class));
        assertThat(map(map(map(paths.get("/sales/invoices")).get("post")).get("responses"))).containsKeys("200","201","400","401","403","409");
        var listMethod = Arrays.stream(FacturaController.class.getDeclaredMethods()).filter(m -> m.getName().equals("listar")).findFirst().orElseThrow();
        Set<String> actualParams = Arrays.stream(listMethod.getParameters()).filter(p -> p.isAnnotationPresent(RequestParam.class)).map(p -> p.getName()).collect(Collectors.toSet());
        var listOperation = map(map(paths.get("/sales/invoices")).get("get"));
        @SuppressWarnings("unchecked") List<Object> parameters = (List<Object>) listOperation.get("parameters");
        Set<String> expectedParams = parameters.stream().map(FacturaOpenApiContractTest::map).map(p -> p.get("$ref").toString())
            .map(ref -> ref.substring(ref.lastIndexOf('/') + 1)).map(ref -> map(map(map(root.get("components")).get("parameters")).get(ref)).get("name").toString()).collect(Collectors.toSet());
        assertThat(actualParams).containsExactlyInAnyOrderElementsOf(expectedParams);
    }
}
