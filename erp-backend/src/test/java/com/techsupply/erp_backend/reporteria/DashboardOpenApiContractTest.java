package com.techsupply.erp_backend.reporteria;

import com.techsupply.erp_backend.reporteria.api.DashboardController;
import com.techsupply.erp_backend.reporteria.application.dto.DashboardResponse;
import com.techsupply.erp_backend.reporteria.application.dto.IndicadorDashboardResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.assertThat;

class DashboardOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }
    private static java.util.Set<String> fields(Class<?> type) {
        return Arrays.stream(type.getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).collect(Collectors.toSet());
    }

    @Test void rutaYProyeccionesCoincidenConOpenApi() throws Exception {
        var options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        Path path = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-reporteria.yaml");
        Map<String, Object> api;
        try (var stream = Files.newInputStream(path)) { api = new Yaml(new SafeConstructor(options)).load(stream); }
        String route = "/reports/dashboard";
        var operation = map(map(map(api.get("paths")).get(route)).get("get"));
        assertThat(operation.get("x-implementation-status")).isEqualTo("implemented");
        assertThat(DashboardController.class.getAnnotation(RequestMapping.class).value()).containsExactly("/api/v1" + route);
        var schemas = map(map(api.get("components")).get("schemas"));
        assertThat(map(map(schemas.get("Dashboard")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(DashboardResponse.class));
        assertThat(map(map(schemas.get("Metric")).get("properties")).keySet()).containsExactlyInAnyOrderElementsOf(fields(IndicadorDashboardResponse.class));
        for (String exportPath : java.util.List.of("/reports/export", "/reports/dashboard/export.csv")) {
            var export = map(map(map(api.get("paths")).get(exportPath)).get("get"));
            assertThat(export.get("x-implementation-status")).isEqualTo("implemented");
            var responses = map(export.get("responses"));
            assertThat(responses).containsKeys("200", "401", "403");
            assertThat(map(map(responses.get("200")).get("content"))).containsKey("text/csv");
        }
        var generalExport = map(map(map(api.get("paths")).get("/reports/export")).get("get"));
        assertThat(map(map(map(generalExport.get("responses")).get("200")).get("content")))
            .containsKeys("application/pdf", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    }
}
