package com.techsupply.erp_backend.audit;

import com.techsupply.erp_backend.audit.api.AuditController;
import com.techsupply.erp_backend.audit.application.AuditEventResponse;
import com.techsupply.erp_backend.common.api.ApiError;
import com.techsupply.erp_backend.common.api.PageResponse;
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
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static org.assertj.core.api.Assertions.assertThat;

class AuditOpenApiContractTest {
    private static Map<String, Object> contract;

    @BeforeAll
    static void readContract() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Path file = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-audit.yaml");
        try (var stream = Files.newInputStream(file)) {
            contract = new Yaml(new SafeConstructor(options)).load(stream);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    @Test
    void routeFiltersAndAuthorizationMatchController() {
        Map<String, Object> paths = map(contract.get("paths"));
        assertThat(paths.keySet()).containsExactly("/audit/events");
        assertThat(map(paths.get("/audit/events")).keySet()).containsExactly("get");
        var method = Arrays.stream(AuditController.class.getDeclaredMethods())
            .filter(candidate -> AnnotatedElementUtils.findMergedAnnotation(candidate, RequestMapping.class) != null)
            .findFirst().orElseThrow();
        RequestMapping route = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
        assertThat(route.method()[0].name()).isEqualTo("GET");
        assertThat(AuditController.class.getAnnotation(RequestMapping.class).value()[0])
            .isEqualTo("/api/v1/audit/events");
        assertThat(method.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('SEGURIDAD_AUDITORIA_LEER')");

        Map<String, Object> operation = map(map(paths.get("/audit/events")).get("get"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) operation.get("parameters");
        Set<String> documented = parameters.stream().map(parameter -> (String) parameter.get("name"))
            .collect(Collectors.toSet());
        Set<String> actual = Arrays.stream(method.getParameters())
            .filter(parameter -> parameter.isAnnotationPresent(RequestParam.class))
            .map(java.lang.reflect.Parameter::getName).collect(Collectors.toSet());
        assertThat(documented).containsExactlyInAnyOrderElementsOf(actual);
        assertThat(map(operation.get("responses"))).containsKeys("200", "400", "401", "403");
        assertThat(contract).containsKey("security");
    }

    @Test
    void responseAndErrorSchemasMatchSharedRecords() {
        Map<String, Object> schemas = map(map(contract.get("components")).get("schemas"));
        assertThat(map(map(schemas.get("AuditEvent")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(Arrays.stream(AuditEventResponse.class.getRecordComponents())
                .map(RecordComponent::getName).toList());
        assertThat(map(map(schemas.get("AuditPage")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(Arrays.stream(PageResponse.class.getRecordComponents())
                .map(RecordComponent::getName).toList());
        assertThat(map(map(schemas.get("ApiError")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(Arrays.stream(ApiError.class.getRecordComponents())
                .map(RecordComponent::getName).toList());
    }
}
