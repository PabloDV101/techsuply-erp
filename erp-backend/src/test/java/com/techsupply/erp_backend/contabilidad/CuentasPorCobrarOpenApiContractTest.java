package com.techsupply.erp_backend.contabilidad;

import com.techsupply.erp_backend.contabilidad.api.CuentasPorCobrarController;
import com.techsupply.erp_backend.contabilidad.application.CuentasPorCobrarService;
import com.techsupply.erp_backend.contabilidad.application.dto.CuentaPorCobrarResponse;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import static org.assertj.core.api.Assertions.assertThat;

class CuentasPorCobrarOpenApiContractTest {
    @SuppressWarnings("unchecked") private static Map<String,Object> map(Object value) { return (Map<String,Object>) value; }

    @Test void rutasPermisosYRespuestaCoincidenConOpenApi() throws Exception {
        var options = new LoaderOptions(); options.setAllowDuplicateKeys(false); options.setMaxAliasesForCollections(300);
        Path contract = Path.of(System.getProperty("basedir", "."), "..", "docs", "api", "openapi-sprint-4-contabilidad.yaml");
        Map<String,Object> document;
        try (var stream = Files.newInputStream(contract)) { document = new Yaml(new SafeConstructor(options)).load(stream); }
        var paths = map(document.get("paths"));
        for (String path : Set.of("/accounting/accounts-receivable", "/accounting/accounts-receivable/{id}"))
            assertThat(map(paths.get(path)).get("x-implementation-status")).isEqualTo("implemented");

        assertThat(CuentasPorCobrarController.class.getAnnotation(RequestMapping.class).value())
            .containsExactly("/api/v1/accounting/accounts-receivable");
        var methods = Arrays.stream(CuentasPorCobrarController.class.getDeclaredMethods()).toList();
        assertThat(methods.stream().anyMatch(m -> m.isAnnotationPresent(GetMapping.class)
            && Arrays.asList(m.getAnnotation(GetMapping.class).value()).contains("/{id}")
            && Arrays.stream(m.getParameters()).anyMatch(p -> p.isAnnotationPresent(PathVariable.class)))).isTrue();
        for (var method : Arrays.stream(CuentasPorCobrarService.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("listar") || m.getName().equals("obtener")).toList())
            assertThat(AnnotatedElementUtils.findMergedAnnotation(method, PreAuthorize.class).value())
                .contains("CONTABILIDAD_CXC_LEER");

        var schemas = map(map(document.get("components")).get("schemas"));
        var properties = map(map(schemas.get("CuentaPorCobrar")).get("properties"));
        Set<String> dtoFields = Arrays.stream(CuentaPorCobrarResponse.class.getRecordComponents())
            .map(RecordComponent::getName).collect(Collectors.toSet());
        assertThat(properties.keySet()).containsExactlyInAnyOrderElementsOf(dtoFields);
    }
}
