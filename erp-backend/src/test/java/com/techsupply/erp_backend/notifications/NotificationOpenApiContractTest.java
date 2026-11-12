package com.techsupply.erp_backend.notifications;

import com.techsupply.erp_backend.notifications.api.NotificationController;
import com.techsupply.erp_backend.notifications.application.NotificationResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationOpenApiContractTest {
    private static Map<String, Object> contract;

    @BeforeAll static void load() throws Exception {
        LoaderOptions options = new LoaderOptions();
        options.setAllowDuplicateKeys(false);
        Path file = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-notifications.yaml");
        try (var stream = Files.newInputStream(file)) {
            contract = new Yaml(new SafeConstructor(options)).load(stream);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) { return (Map<String, Object>) value; }

    @Test void pathsAndQueryParametersMatchController() {
        Map<String, Object> paths = map(contract.get("paths"));
        Set<String> documented = paths.entrySet().stream()
            .flatMap(entry -> map(entry.getValue()).keySet().stream()
                .filter(key -> !key.equals("parameters"))
                .map(verb -> verb + " " + entry.getKey()))
            .collect(Collectors.toSet());
        Set<String> actual = Arrays.stream(NotificationController.class.getDeclaredMethods())
            .map(method -> AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class))
            .filter(route -> route != null)
            .map(route -> route.method()[0].name().toLowerCase() + " /notifications"
                + (route.value().length == 0 ? "" : route.value()[0]))
            .collect(Collectors.toSet());
        assertThat(documented).containsExactlyInAnyOrderElementsOf(actual);

        Map<String, Object> listing = map(map(paths.get("/notifications")).get("get"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parameters = (List<Map<String, Object>>) listing.get("parameters");
        Set<String> documentedParams = parameters.stream().map(p -> (String) p.get("name")).collect(Collectors.toSet());
        Method listMethod = Arrays.stream(NotificationController.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("list")).findFirst().orElseThrow();
        Set<String> actualParams = Arrays.stream(listMethod.getParameters())
            .filter(parameter -> parameter.isAnnotationPresent(RequestParam.class))
            .map(java.lang.reflect.Parameter::getName).collect(Collectors.toSet());
        assertThat(documentedParams).containsExactlyInAnyOrderElementsOf(actualParams);
    }

    @Test void responseAndErrorShapesMatchRecords() {
        Map<String, Object> schemas = map(map(contract.get("components")).get("schemas"));
        assertThat(map(map(schemas.get("Notification")).get("properties")).keySet())
            .containsExactlyInAnyOrderElementsOf(Arrays.stream(NotificationResponse.class.getRecordComponents())
                .map(java.lang.reflect.RecordComponent::getName).toList());
        assertThat(map(map(schemas.get("ApiError")).get("properties")).keySet())
            .containsExactlyInAnyOrder("status", "code", "message");
        assertThat(map(map(schemas.get("NotificationPage")).get("properties")).keySet())
            .containsExactlyInAnyOrder("content", "page", "size", "totalElements", "totalPages");
    }
}
