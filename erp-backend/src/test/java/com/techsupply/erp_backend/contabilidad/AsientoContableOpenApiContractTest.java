package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.contabilidad.api.AsientoContableController;
import com.techsupply.erp_backend.contabilidad.application.dto.*;
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
import static org.assertj.core.api.Assertions.assertThat;

class AsientoContableOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object obj) { return (Map<String,Object>) obj; }
    private static Set<String> fields(Class<?> type) { return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet()); }

    @Test void rutasParametrosYEsquemasCoincidenConApiImplementada() throws Exception {
        var options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        var file = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-sprint-4-contabilidad.yaml");
        Map<String,Object> root;
        try (var stream = Files.newInputStream(file)) { root = new Yaml(new SafeConstructor(options)).load(stream); }
        var paths = map(root.get("paths")); var components = map(root.get("components")); var schemas = map(components.get("schemas"));
        var actual = Arrays.stream(AsientoContableController.class.getDeclaredMethods())
            .map(m -> AnnotatedElementUtils.findMergedAnnotation(m, RequestMapping.class)).filter(Objects::nonNull)
            .map(r -> r.method()[0].name().toLowerCase(Locale.ROOT) + " /accounting/entries" + (r.value().length == 0 ? "" : r.value()[0])).collect(Collectors.toSet());
        var documented = paths.entrySet().stream().filter(e -> e.getKey().startsWith("/accounting/entries")
                && "implemented".equals(map(e.getValue()).get("x-implementation-status")))
            .flatMap(e -> map(e.getValue()).keySet().stream().filter(k -> List.of("get","post","put","patch","delete").contains(k)).map(k -> k + " " + e.getKey())).collect(Collectors.toSet());
        assertThat(documented).containsExactlyInAnyOrderElementsOf(actual);
        for (String route : List.of("/accounting/entries", "/accounting/entries/{id}"))
            assertThat(map(paths.get(route)).get("x-implementation-status")).isEqualTo("implemented");
        assertThat(map(map(schemas.get("AsientoCreate")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(fields(AsientoContableCreateRequest.class));
        assertThat(map(map(schemas.get("PartidaCreate")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(fields(PartidaAsientoCreateRequest.class));
        assertThat(map(map(schemas.get("Asiento")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(fields(AsientoContableResponse.class));
        assertThat(map(map(schemas.get("Partida")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(fields(PartidaAsientoResponse.class));
        assertThat(map(map(schemas.get("BorradorEdit")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(fields(AsientoContableEditRequest.class));
        assertThat(map(map(schemas.get("BorradorCancel")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(fields(AsientoContableCancelRequest.class));
        assertThat(map(map(schemas.get("ReversionCreate")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(fields(AsientoContableReverseRequest.class));
        var listMethod = Arrays.stream(AsientoContableController.class.getDeclaredMethods()).filter(m -> m.getName().equals("listar")).findFirst().orElseThrow();
        var actualParams = Arrays.stream(listMethod.getParameters()).filter(p -> p.isAnnotationPresent(RequestParam.class)).map(p -> p.getName()).collect(Collectors.toSet());
        @SuppressWarnings("unchecked") List<Object> parameters = (List<Object>) map(map(paths.get("/accounting/entries")).get("get")).get("parameters");
        var expectedParams = parameters.stream().map(AsientoContableOpenApiContractTest::map).map(p -> p.get("$ref").toString())
            .map(ref -> map(map(components.get("parameters")).get(ref.substring(ref.lastIndexOf('/') + 1))).get("name").toString()).collect(Collectors.toSet());
        assertThat(actualParams).containsExactlyInAnyOrderElementsOf(expectedParams);
    }
}
